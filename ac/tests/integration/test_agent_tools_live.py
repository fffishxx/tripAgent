"""Agent Java 工具在真实 Java 服务下的集成测试。"""

import importlib.util
from pathlib import Path

import pytest

from conftest import idem, plan, taxi_payload

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def tools():
    try:
        import clients  # noqa: F401
    except Exception as exc:
        pytest.skip(f"无法导入 clients: {exc}")

    path = Path(__file__).resolve().parents[2] / "agent/tripAgent/tools/java_tools.py"
    spec = importlib.util.spec_from_file_location("java_tools_live", path)
    if spec is None or spec.loader is None:
        pytest.skip(f"无法加载 {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@pytest.fixture(scope="module")
def clients_module(tools):
    import clients
    return clients


async def test_save_trip_business_replay(tools, clients_module, java_user_a):
    session_id = "live-session-" + idem()
    trip = await tools.save_trip_business(
        plan=plan(),
        start_date="2026-11-10",
        end_date="2026-11-11",
        title="PYTEST PY 工具行程",
        user_token=java_user_a["token"],
        user_id=java_user_a["user_id"],
        request_id=idem(),
        session_id=session_id,
        plan_request_id="plan-request-live",
    )
    trip_id = trip["id"]

    try:
        replay = await tools.save_trip_business(
            plan=plan(),
            start_date="2026-11-10",
            end_date="2026-11-11",
            title="PYTEST PY 工具行程",
            user_token=java_user_a["token"],
            user_id=java_user_a["user_id"],
            request_id=idem(),
            session_id=session_id,
            plan_request_id="plan-request-live",
        )
        assert replay["id"] == trip_id
        assert replay["idempotent_replay"] is True

        with pytest.raises(clients_module.BackendNotFoundError):
            # 使用 Long 范围内的不存在 ID，避免超出 Java Long 解析范围
            await tools.backend_client.get_trip(
                java_user_a["token"], java_user_a["user_id"], idem(), "1")
    finally:
        await tools.backend_client.delete_trip(
            java_user_a["token"], java_user_a["user_id"], idem(), trip_id)


async def test_agent_tool_permissions(tools, clients_module, java_user_a, java_user_b):
    session_id = "live-session-" + idem()
    trip = await tools.save_trip_business(
        plan=plan(),
        start_date="2026-11-10",
        end_date="2026-11-11",
        title="PYTEST PY 工具权限",
        user_token=java_user_a["token"],
        user_id=java_user_a["user_id"],
        request_id=idem(),
        session_id=session_id,
        plan_request_id="plan-request-permission",
    )
    trip_id = trip["id"]

    try:
        with pytest.raises(clients_module.BackendNotFoundError):
            await tools.backend_client.get_trip(
                java_user_b["token"], java_user_b["user_id"], idem(), trip_id)
    finally:
        await tools.backend_client.delete_trip(
            java_user_a["token"], java_user_a["user_id"], idem(), trip_id)


async def test_taxi_tool_create_and_replay(tools, java_user_a):
    session_id = "live-session-" + idem()
    payload = taxi_payload()

    order = await tools.create_taxi_order_business(
        payload=payload,
        user_token=java_user_a["token"],
        user_id=java_user_a["user_id"],
        request_id=idem(),
        session_id=session_id,
        taxi_request_id="taxi-request-live",
    )
    order_id = order["id"]
    assert order["status"] == "CREATED"

    replay = await tools.create_taxi_order_business(
        payload=payload,
        user_token=java_user_a["token"],
        user_id=java_user_a["user_id"],
        request_id=idem(),
        session_id=session_id,
        taxi_request_id="taxi-request-live",
    )
    assert replay["id"] == order_id
    assert replay["idempotent_replay"] is True

    cancelled = await tools.backend_client.cancel_taxi_order(
        java_user_a["token"], java_user_a["user_id"], idem(), order_id, "pytest")
    assert cancelled["status"] == "CANCELLED"
