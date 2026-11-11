"""Gateway 服务身份与可信用户头中间件测试。"""

import base64

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from starlette.requests import Request
from starlette.responses import Response

from util import JWTUtil


@pytest.fixture(scope="module")
def app_keys():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    private_der = key.private_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    public_der = key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return (base64.b64encode(private_der).decode("ascii"),
            base64.b64encode(public_der).decode("ascii"))


@pytest.fixture
def request_filter(load_module, fake_config, app_keys):
    _, public_b64 = app_keys
    fake_config.values.update({
        "jwt.public_key": public_b64,
        "security.gateway.allowed-app-keys": "gateway-app",
    })
    module = load_module("request_filter_under_test", "filter/RequestFilter.py")
    return module.RequestFilter(lambda scope, receive, send: None)


def app_token(app_keys, app_key="gateway-app"):
    private_b64, _ = app_keys
    return JWTUtil.create_token(
        {"app_id": 1, "app_key": app_key, "name": "gateway"},
        private_b64,
        expire_hours=1,
    )


def make_request(path="/chat", headers=None, with_gateway=True):
    raw_headers = []
    if with_gateway:
        raw_headers.append((b"request-from", b"agent-center-gateway"))
    for key, value in (headers or {}).items():
        raw_headers.append((key.lower().encode("latin-1"), value.encode("latin-1")))
    return Request({
        "type": "http",
        "method": "POST",
        "path": path,
        "headers": raw_headers,
        "state": {},
    })


async def call_next(request):
    return Response("ok")


async def test_missing_service_token_is_rejected(request_filter):
    response = await request_filter.dispatch(make_request(), call_next)
    assert response.status_code == 401


async def test_missing_gateway_header_is_rejected(request_filter, app_keys):
    response = await request_filter.dispatch(
        make_request(headers={"Authorization": "Bearer " + app_token(app_keys)},
                     with_gateway=False),
        call_next,
    )
    assert response.status_code == 401


async def test_invalid_service_token_is_rejected(request_filter):
    response = await request_filter.dispatch(
        make_request(headers={"Authorization": "Bearer invalid"}),
        call_next,
    )
    assert response.status_code == 401


async def test_disallowed_app_key_is_rejected(request_filter, app_keys):
    response = await request_filter.dispatch(
        make_request(headers={
            "Authorization": "Bearer " + app_token(app_keys, "other-app"),
        }),
        call_next,
    )
    assert response.status_code == 401


async def test_anonymous_request_passes(request_filter, app_keys):
    response = await request_filter.dispatch(
        make_request(headers={"Authorization": "Bearer " + app_token(app_keys)}),
        call_next,
    )
    assert response.status_code == 200


async def test_trusted_user_headers_are_stored(request_filter, app_keys):
    captured = {}

    async def next_fn(request):
        captured["user_id"] = request.state.user_id
        captured["user_token"] = request.state.user_token
        return Response("ok")

    response = await request_filter.dispatch(
        make_request(headers={
            "Authorization": "Bearer " + app_token(app_keys),
            "X-User-Id": "7",
            "X-User-Token": "user-token",
        }),
        next_fn,
    )

    assert response.status_code == 200
    assert captured == {"user_id": 7, "user_token": "user-token"}


async def test_incomplete_user_headers_are_rejected(request_filter, app_keys):
    response = await request_filter.dispatch(
        make_request(headers={
            "Authorization": "Bearer " + app_token(app_keys),
            "X-User-Id": "7",
        }),
        call_next,
    )
    assert response.status_code == 401


async def test_invalid_user_id_is_rejected(request_filter, app_keys):
    response = await request_filter.dispatch(
        make_request(headers={
            "Authorization": "Bearer " + app_token(app_keys),
            "X-User-Id": "abc",
            "X-User-Token": "user-token",
        }),
        call_next,
    )
    assert response.status_code == 400


async def test_docs_requires_gateway_identity(request_filter):
    response = await request_filter.dispatch(make_request(path="/docs", with_gateway=False), call_next)
    assert response.status_code == 401


async def test_auth_token_is_rate_limited(request_filter):
    request_filter._auth_token_hits.clear()
    responses = []
    for _ in range(31):
        responses.append(await request_filter.dispatch(
            make_request(path="/auth/token", with_gateway=False), call_next))
    assert all(response.status_code == 200 for response in responses[:30])
    assert responses[30].status_code == 429


async def test_auth_token_ip_whitelist(request_filter):
    request_filter.auth_token_allowed_ips = {"10.0.0.1"}
    response = await request_filter.dispatch(
        make_request(path="/auth/token", with_gateway=False), call_next)
    assert response.status_code == 403
