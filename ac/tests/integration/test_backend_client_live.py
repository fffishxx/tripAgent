"""真实 Java 后端下的 BackendClient 集成测试。"""

import pytest

from conftest import JAVA_BASE, SERVICE_TOKEN, idem, taxi_payload, trip_payload

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def clients_module():
    try:
        import clients
    except Exception as exc:  # Nacos/配置不可用时跳过而不是收集失败
        pytest.skip(f"无法导入 clients: {exc}")
    return clients


@pytest.fixture
async def backend(clients_module):
    client = clients_module.BackendClient(
        base_url=JAVA_BASE,
        service_token=SERVICE_TOKEN,
        agent_id=1004,
        connect_timeout=5,
        read_timeout=30,
        max_retries=1,
    )
    await client.start()
    try:
        yield client
    finally:
        await client.close()


async def test_preference_live(backend, java_user_a):
    data = await backend.get_preference(java_user_a["token"], java_user_a["user_id"], idem())
    assert isinstance(data, dict)
    assert "crowd" in data


async def test_trip_lifecycle_live(backend, clients_module, java_user_a):
    key = idem()
    payload = trip_payload("PYTEST PY BackendClient")

    created = await backend.save_trip(java_user_a["token"], java_user_a["user_id"], idem(), key, payload)
    trip_id = created["id"]
    assert created["idempotent_replay"] is False
    assert created["version"] == 0

    replay = await backend.save_trip(java_user_a["token"], java_user_a["user_id"], idem(), key, payload)
    assert replay["id"] == trip_id
    assert replay["idempotent_replay"] is True

    detail = await backend.get_trip(java_user_a["token"], java_user_a["user_id"], idem(), trip_id)
    assert detail["id"] == trip_id

    updated = await backend.update_trip(
        java_user_a["token"], java_user_a["user_id"], idem(), trip_id,
        trip_payload("PYTEST PY 已更新", version=0),
    )
    assert updated["version"] == 1

    await backend.delete_trip(java_user_a["token"], java_user_a["user_id"], idem(), trip_id)
    with pytest.raises(clients_module.BackendNotFoundError):
        await backend.get_trip(java_user_a["token"], java_user_a["user_id"], idem(), trip_id)


async def test_permissions_live(backend, clients_module, java_user_a, java_user_b):
    created = await backend.save_trip(
        java_user_a["token"], java_user_a["user_id"], idem(), idem(),
        trip_payload("PYTEST PY 权限"))
    trip_id = created["id"]

    try:
        with pytest.raises(clients_module.BackendNotFoundError):
            await backend.get_trip(java_user_b["token"], java_user_b["user_id"], idem(), trip_id)
    finally:
        await backend.delete_trip(java_user_a["token"], java_user_a["user_id"], idem(), trip_id)


async def test_taxi_lifecycle_live(backend, java_user_a):
    estimate = await backend.estimate_taxi(
        java_user_a["token"], java_user_a["user_id"], idem(),
        {
            "pickup": {"name": "西湖", "longitude": 120.15507, "latitude": 30.274084},
            "destination": {"name": "灵隐寺", "longitude": 120.10210, "latitude": 30.24086},
            "city": "杭州",
        },
    )
    assert estimate["vehicle_options"]

    key = idem()
    created = await backend.create_taxi_order(
        java_user_a["token"], java_user_a["user_id"], idem(), key, taxi_payload())
    order_id = created["id"]
    assert created["status"] == "CREATED"

    replay = await backend.create_taxi_order(
        java_user_a["token"], java_user_a["user_id"], idem(), key, taxi_payload())
    assert replay["id"] == order_id
    assert replay["idempotent_replay"] is True

    cancelled = await backend.cancel_taxi_order(
        java_user_a["token"], java_user_a["user_id"], idem(), order_id, "pytest")
    assert cancelled["status"] == "CANCELLED"


async def test_invalid_token_maps_to_auth_error(backend, clients_module, java_user_a):
    with pytest.raises(clients_module.BackendAuthError):
        await backend.get_preference(
            "invalid-token", java_user_a["user_id"], idem())
