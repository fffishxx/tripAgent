"""严格会话鉴权集成测试（服务 Token + 可信 X-User-Id）。"""

import uuid

import pytest

from conftest import session_headers

pytestmark = pytest.mark.integration


def create_session(py, gateway, user_id, user=None):
    headers = session_headers(gateway, user) if user else dict(gateway)
    return py.post("/session", params={
        "n": 1, "agent_id": 1004, "user_id": user_id,
    }, headers=headers)


def detail(py, gateway, user, session_id, authenticated=True):
    headers = session_headers(gateway, user) if authenticated else dict(gateway)
    return py.get(
        f"/session/1004/{user['user_id']}/{session_id}", headers=headers)


def delete_session(py, gateway, user, session_id):
    return py.delete("/session/history", params={
        "agent_id": 1004,
        "user_id": user["user_id"],
        "session_id": session_id,
    }, headers=session_headers(gateway, user))


def test_session_create_requires_trusted_user(py, gateway_headers, java_user_a, java_user_b):
    no_user = create_session(py, gateway_headers, java_user_a["user_id"])
    assert no_user.status_code == 401

    mismatch = create_session(
        py, gateway_headers, java_user_b["user_id"], java_user_a)
    assert mismatch.status_code == 403

    unknown_agent = py.post(
        "/session", params={"n": 1, "agent_id": 9999, "user_id": java_user_a["user_id"]},
        headers=session_headers(gateway_headers, java_user_a))
    assert unknown_agent.status_code == 404


def test_session_ownership_and_delete(py, gateway_headers, java_user_a, java_user_b):
    created = create_session(
        py, gateway_headers, java_user_a["user_id"], java_user_a)
    assert created.status_code == 200
    session_id = created.json()["sessionId"]

    try:
        no_user = detail(py, gateway_headers, java_user_a, session_id, authenticated=False)
        assert no_user.status_code == 401

        own_detail = detail(py, gateway_headers, java_user_a, session_id)
        assert own_detail.status_code == 200

        other_detail = detail(py, gateway_headers, java_user_b, session_id)
        assert other_detail.status_code == 404

        mismatch_path = py.get(
            f"/session/1004/{java_user_a['user_id']}/{session_id}",
            headers=session_headers(gateway_headers, java_user_b))
        assert mismatch_path.status_code == 403

        no_user_history = py.get(
            "/session/history",
            params={"agent_id": 1004, "user_id": java_user_a["user_id"]},
            headers=gateway_headers)
        assert no_user_history.status_code == 401

        own_history = py.get(
            "/session/history",
            params={"agent_id": 1004, "user_id": java_user_a["user_id"]},
            headers=session_headers(gateway_headers, java_user_a))
        assert own_history.status_code == 200

        foreign_delete = delete_session(
            py, gateway_headers, java_user_b, session_id)
        assert foreign_delete.status_code == 200
        assert detail(py, gateway_headers, java_user_a, session_id).status_code == 200

        own_delete = delete_session(
            py, gateway_headers, java_user_a, session_id)
        assert own_delete.status_code == 200
        assert detail(py, gateway_headers, java_user_a, session_id).status_code == 404
    finally:
        delete_session(py, gateway_headers, java_user_a, session_id)


def test_session_stop_ownership(py, gateway_headers, java_user_a, java_user_b):
    created = create_session(
        py, gateway_headers, java_user_a["user_id"], java_user_a)
    assert created.status_code == 200
    session_id = created.json()["sessionId"]

    try:
        no_user = py.post("/session/stop", params={
            "session_id": session_id, "agent_id": 1004,
        }, headers=gateway_headers)
        assert no_user.status_code == 401

        foreign = py.post("/session/stop", params={
            "session_id": session_id, "agent_id": 1004,
        }, headers=session_headers(gateway_headers, java_user_b))
        assert foreign.status_code == 404

        own = py.post("/session/stop", params={
            "session_id": session_id, "agent_id": 1004,
        }, headers=session_headers(gateway_headers, java_user_a))
        assert own.status_code == 200
    finally:
        delete_session(py, gateway_headers, java_user_a, session_id)

    anonymous = py.post("/session/stop", params={
        "session_id": uuid.uuid4().hex, "agent_id": 1004,
    }, headers=gateway_headers)
    assert anonymous.status_code == 200


def test_chat_rejects_other_users_session(py, gateway_headers, java_user_a, java_user_b):
    created = create_session(
        py, gateway_headers, java_user_a["user_id"], java_user_a)
    assert created.status_code == 200
    session_id = created.json()["sessionId"]

    try:
        response = py.post("/chat", headers=session_headers(gateway_headers, java_user_b), json={
            "question": "你好",
            "sessionId": session_id,
            "agentId": 1004,
        })
        assert response.status_code == 403
    finally:
        delete_session(py, gateway_headers, java_user_a, session_id)


def test_gateway_and_invalid_service_token(py, gateway_headers, java_user_a):
    no_gateway = py.post("/session", params={
        "n": 1, "agent_id": 1004, "user_id": java_user_a["user_id"],
    }, headers={
        "Authorization": "Bearer invalid-token",
        "X-User-Id": str(java_user_a["user_id"]),
        "X-User-Token": java_user_a["token"],
    })
    assert no_gateway.status_code == 401

    invalid_service = py.post("/session", params={
        "n": 1, "agent_id": 1004, "user_id": java_user_a["user_id"],
    }, headers={
        "request-from": "agent-center-gateway",
        "Authorization": "Bearer invalid-token",
        "X-User-Id": str(java_user_a["user_id"]),
        "X-User-Token": java_user_a["token"],
    })
    assert invalid_service.status_code == 401


def test_python_request_id_echo(py, gateway_headers):
    request_id = "py-test-trace-001"
    response = py.get("/health", headers={"X-Request-Id": request_id})
    assert response.headers.get("X-Request-Id") == request_id

    invalid = py.get("/health", headers={"X-Request-Id": "bad id!"})
    generated = invalid.headers.get("X-Request-Id")
    assert generated != "bad id!"
    assert len(generated) == 32
