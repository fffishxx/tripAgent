"""Java 确定性业务函数与幂等键测试。"""

import pytest


@pytest.fixture(scope="module")
def tools(load_module):
    return load_module("java_tools_under_test", "agent/tripAgent/tools/java_tools.py")


@pytest.fixture
def backend(tools, fake_backend_client):
    tools.backend_client = fake_backend_client
    return fake_backend_client


def plan():
    return {
        "destination": "杭州",
        "days": [{"day_index": 1, "items": [{"poi": "西湖"}]}],
    }


def test_save_idempotency_key_is_stable_and_input_sensitive(tools):
    first = tools.build_save_idempotency_key(
        "session-1", "request-1", plan(), "2026-11-10", "2026-11-12")
    second = tools.build_save_idempotency_key(
        "session-1", "request-1", plan(), "2026-11-10", "2026-11-12")

    assert first == second
    assert len(first) == 32
    assert first != tools.build_save_idempotency_key(
        "session-2", "request-1", plan(), "2026-11-10", "2026-11-12")
    assert first != tools.build_save_idempotency_key(
        "session-1", "request-2", plan(), "2026-11-10", "2026-11-12")
    assert first != tools.build_save_idempotency_key(
        "session-1", "request-1", plan(), "2026-11-11", "2026-11-13")


def test_taxi_idempotency_key_is_stable_and_input_sensitive(tools):
    payload = {"pickup": {"name": "西湖"}, "vehicle_type": "快车"}
    first = tools.build_taxi_idempotency_key("session-1", "request-1", payload)

    assert first == tools.build_taxi_idempotency_key("session-1", "request-1", payload)
    assert len(first) == 32
    assert first != tools.build_taxi_idempotency_key(
        "session-2", "request-1", payload)
    assert first != tools.build_taxi_idempotency_key(
        "session-1", "request-1", {**payload, "vehicle_type": "专车"})


async def test_save_trip_business_requires_login(tools, backend):
    with pytest.raises(tools.BackendAuthError):
        await tools.save_trip_business(plan(), "2026-11-10", "2026-11-12")
    assert backend.calls == []


async def test_save_trip_business_passes_idempotency_key(tools, backend):
    result = await tools.save_trip_business(
        plan=plan(),
        start_date="2026-11-10",
        end_date="2026-11-12",
        title="杭州三日游",
        user_token="user-token",
        user_id=7,
        request_id="request-1",
        session_id="session-1",
        plan_request_id="plan-request-1",
    )

    assert result["id"] == "9001"
    call = backend.calls[0]
    assert call[0] == "save_trip"
    assert call[1] == "user-token"
    assert call[2] == 7
    assert call[3] == "request-1"
    assert len(call[4]) == 32
    assert call[5]["title"] == "杭州三日游"
    assert call[5]["plan"] == plan()


async def test_create_taxi_order_business_requires_login(tools, backend):
    with pytest.raises(tools.BackendAuthError):
        await tools.create_taxi_order_business({"vehicle_type": "快车"})
    assert backend.calls == []


async def test_create_taxi_order_business_passes_idempotency_key(tools, backend):
    payload = {"pickup": {"name": "西湖"}, "vehicle_type": "快车"}
    result = await tools.create_taxi_order_business(
        payload=payload,
        user_token="user-token",
        user_id=7,
        request_id="request-2",
        session_id="session-1",
        taxi_request_id="taxi-request-1",
    )

    assert result["id"] == "7001"
    call = backend.calls[0]
    assert call[0] == "create_taxi_order"
    assert call[1] == "user-token"
    assert call[2] == 7
    assert call[3] == "request-2"
    assert len(call[4]) == 32
    assert call[5] == payload
