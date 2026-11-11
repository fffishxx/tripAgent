"""BackendClient HTTP 契约与异常映射测试（不访问真实 Java）。"""

import asyncio

import httpx
import pytest


@pytest.fixture(scope="module")
def client_module(load_module):
    return load_module("backend_client_under_test", "clients/backend_client.py")


def make_client(client_module, handler, max_retries=0):
    client = client_module.BackendClient(
        base_url="http://java",
        service_token="service-token",
        agent_id=1004,
        connect_timeout=1,
        read_timeout=1,
        max_retries=max_retries,
    )
    client._client = httpx.AsyncClient(
        base_url="http://java",
        transport=httpx.MockTransport(handler),
        timeout=httpx.Timeout(1),
    )
    return client


async def test_request_headers_and_success_data(client_module):
    captured = []

    def handler(request):
        captured.append(request)
        return httpx.Response(200, json={"code": 0, "data": {"crowd": "family"}})

    client = make_client(client_module, handler)
    try:
        data = await client.get_preference("user-token", 7, "request-1")
    finally:
        await client.close()

    assert data == {"crowd": "family"}
    request = captured[0]
    assert request.method == "GET"
    assert request.url.path == "/api/agent/v1/preferences"
    assert request.headers["x-user-token"] == "user-token"
    assert request.headers["x-user-id"] == "7"
    assert "authorization" not in request.headers
    assert request.headers["x-service-token"] == "service-token"
    assert request.headers["x-request-id"] == "request-1"
    assert request.headers["x-agent-id"] == "1004"


async def test_idempotency_header_is_sent(client_module):
    captured = []

    def handler(request):
        captured.append(request)
        return httpx.Response(200, json={"code": 0, "data": {"id": "1"}})

    client = make_client(client_module, handler)
    try:
        await client.save_trip("user-token", 7, "request-2", "idem-1", {"title": "x"})
    finally:
        await client.close()

    assert captured[0].headers["idempotency-key"] == "idem-1"
    assert captured[0].method == "POST"


@pytest.mark.parametrize("status,attribute", [
    (400, "BackendValidationError"),
    (401, "BackendAuthError"),
    (403, "BackendForbiddenError"),
    (404, "BackendNotFoundError"),
    (409, "BackendConflictError"),
    (500, "BackendUnavailableError"),
])
async def test_error_status_mapping(client_module, status, attribute):
    def handler(request):
        return httpx.Response(status, json={"code": status, "message": "error"})

    client = make_client(client_module, handler)
    try:
        with pytest.raises(getattr(client_module, attribute)):
            await client.get_preference("user-token", 7, "request-3")
    finally:
        await client.close()


async def test_business_code_error(client_module):
    def handler(request):
        return httpx.Response(200, json={"code": 123, "message": "业务失败"})

    client = make_client(client_module, handler)
    try:
        with pytest.raises(client_module.BackendError):
            await client.get_preference("user-token", 7, "request-4")
    finally:
        await client.close()


async def test_invalid_json_response(client_module):
    def handler(request):
        return httpx.Response(200, text="not-json")

    client = make_client(client_module, handler)
    try:
        with pytest.raises(client_module.BackendError):
            await client.get_preference("user-token", 7, "request-5")
    finally:
        await client.close()


async def test_non_object_response(client_module):
    def handler(request):
        return httpx.Response(200, json=[1, 2, 3])

    client = make_client(client_module, handler)
    try:
        with pytest.raises(client_module.BackendError):
            await client.get_preference("user-token", 7, "request-6")
    finally:
        await client.close()


async def test_missing_token_fails_without_http(client_module):
    calls = []

    def handler(request):
        calls.append(request)
        return httpx.Response(200, json={"code": 0, "data": {}})

    client = make_client(client_module, handler)
    try:
        with pytest.raises(client_module.BackendAuthError):
            await client.get_preference("", 7, "request-7")
    finally:
        await client.close()
    assert calls == []


async def test_missing_base_url_is_unavailable(client_module):
    client = client_module.BackendClient(
        base_url="", service_token="service-token", max_retries=0)
    try:
        with pytest.raises(client_module.BackendUnavailableError):
            await client.get_preference("user-token", 7, "request-8")
    finally:
        await client.close()


async def test_retries_5xx_then_succeeds(client_module, monkeypatch):
    calls = []

    async def fast_sleep(_seconds):
        return None

    monkeypatch.setattr(client_module.asyncio, "sleep", fast_sleep)

    def handler(request):
        calls.append(request)
        if len(calls) < 3:
            return httpx.Response(500, json={"code": 50000, "message": "down"})
        return httpx.Response(200, json={"code": 0, "data": {"ok": True}})

    client = make_client(client_module, handler, max_retries=2)
    try:
        data = await client.get_preference("user-token", 7, "request-9")
    finally:
        await client.close()

    assert data == {"ok": True}
    assert len(calls) == 3


async def test_timeout_retries_then_unavailable(client_module, monkeypatch):
    calls = []

    async def fast_sleep(_seconds):
        return None

    monkeypatch.setattr(client_module.asyncio, "sleep", fast_sleep)

    def handler(request):
        calls.append(request)
        raise httpx.ReadTimeout("timeout", request=request)

    client = make_client(client_module, handler, max_retries=1)
    try:
        with pytest.raises(client_module.BackendUnavailableError):
            await client.get_preference("user-token", 7, "request-10")
    finally:
        await client.close()

    assert len(calls) == 2
