"""Java Agent API 集成测试：鉴权、行程 CRUD、幂等、权限、异常契约。

运行前启动 Java 后端（默认 http://127.0.0.1:8080）并确保 MySQL 可用。
"""

import copy
import uuid
from concurrent.futures import ThreadPoolExecutor

import httpx
import pytest

from conftest import (
    SERVICE_TOKEN,
    agent_headers,
    frontend_headers,
    create_trip,
    idem,
    taxi_estimate_payload,
    taxi_order_payload,
    trip_payload,
)

pytestmark = pytest.mark.integration


def assert_success(response, expected_status=200, expected_code=0):
    assert response.status_code == expected_status, response.text
    body = response.json()
    assert body["code"] == expected_code, body
    return body["data"]


def assert_error(response, expected_status, expected_code):
    assert response.status_code == expected_status, response.text
    body = response.json()
    assert body["code"] == expected_code, body
    return body


def test_health(java):
    data = assert_success(java.get("/api/v1/health"))
    assert data["status"] == "UP"


def test_agent_api_requires_user_token(java):
    response = java.get("/api/agent/v1/trips", headers={
        "X-Service-Token": SERVICE_TOKEN,
    })
    assert_error(response, 401, 40101)


def test_agent_api_requires_service_token(java, user_a):
    response = java.get("/api/agent/v1/trips",
                        headers=agent_headers(user_a, service_token=False))
    assert_error(response, 403, 40302)


def test_agent_api_rejects_wrong_service_token(java, user_a):
    headers = agent_headers(user_a, service_token=False)
    headers["X-Service-Token"] = "wrong-service-token"
    response = java.get("/api/agent/v1/trips", headers=headers)
    assert_error(response, 403, 40302)


def test_agent_api_rejects_tampered_jwt(java, user_a):
    headers = agent_headers(user_a)
    headers["X-User-Token"] = user_a["token"] + "tampered"
    response = java.get("/api/agent/v1/trips", headers=headers)
    assert_error(response, 401, 40101)


def test_request_id_is_echoed(java):
    request_id = "pytest-trace-001"
    response = java.get("/api/v1/health", headers={"X-Request-Id": request_id})
    assert response.headers.get("X-Request-Id") == request_id
    body = response.json()
    assert body["requestId"] == request_id


def test_invalid_request_id_is_replaced(java):
    response = java.get("/api/v1/health", headers={"X-Request-Id": "bad id!"})
    generated = response.headers.get("X-Request-Id")
    assert generated != "bad id!"
    assert len(generated) == 32


def test_unknown_agent_route_is_404(java, user_a):
    response = java.get("/api/agent/v1/not-exists", headers=agent_headers(user_a))
    assert_error(response, 404, 40400)


def test_wrong_method_is_405(java, user_a):
    response = java.post("/api/agent/v1/trips/1", headers=agent_headers(user_a), json={})
    assert_error(response, 405, 40500)


def test_malformed_json_is_bad_request(java, user_a):
    response = java.post(
        "/api/agent/v1/trips",
        headers={**agent_headers(user_a), "Idempotency-Key": idem()},
        content="{bad json",
    )
    assert_error(response, 400, 40000)


def test_trip_create_is_idempotent(java, user_a):
    response, key = create_trip(java, user_a, title="PYTEST 幂等行程")
    created = assert_success(response, 201)
    trip_id = created["id"]
    assert created["idempotent_replay"] is False

    replay_response = java.post(
        "/api/agent/v1/trips",
        headers={**agent_headers(user_a), "Idempotency-Key": key},
        json=trip_payload("PYTEST 幂等行程"),
    )
    replay = assert_success(replay_response, 200)
    assert replay["id"] == trip_id
    assert replay["idempotent_replay"] is True


def test_trip_crud_and_version_conflict(java, user_a):
    response, _ = create_trip(java, user_a, title="PYTEST CRUD 行程")
    trip_id = assert_success(response, 201)["id"]

    detail = assert_success(java.get(
        f"/api/agent/v1/trips/{trip_id}", headers=agent_headers(user_a)))
    assert detail["id"] == trip_id
    assert detail["version"] == 0

    updated = assert_success(java.put(
        f"/api/agent/v1/trips/{trip_id}",
        headers=agent_headers(user_a),
        json=trip_payload("PYTEST 已更新", version=0),
    ))
    assert updated["title"] == "PYTEST 已更新"
    assert updated["version"] == 1

    response = java.put(
        f"/api/agent/v1/trips/{trip_id}",
        headers=agent_headers(user_a),
        json=trip_payload("PYTEST 冲突", version=0),
    )
    assert_error(response, 409, 40902)


def test_trip_ownership_is_enforced(java, user_a, user_b):
    response, key = create_trip(java, user_a, title="PYTEST 权限行程")
    trip_id = assert_success(response, 201)["id"]

    assert_error(java.get(
        f"/api/agent/v1/trips/{trip_id}", headers=agent_headers(user_b)),
        404, 40401)
    assert_error(java.put(
        f"/api/agent/v1/trips/{trip_id}",
        headers=agent_headers(user_b),
        json=trip_payload("PYTEST 越权修改", version=0)),
        404, 40401)
    assert_error(java.delete(
        f"/api/agent/v1/trips/{trip_id}", headers=agent_headers(user_b)),
        404, 40401)

    # 原用户仍然可以正常访问
    assert_success(java.get(
        f"/api/agent/v1/trips/{trip_id}", headers=agent_headers(user_a)))

    assert_success(java.delete(
        f"/api/agent/v1/trips/{trip_id}", headers=agent_headers(user_a)))
    # 重复删除保持幂等
    assert_success(java.delete(
        f"/api/agent/v1/trips/{trip_id}", headers=agent_headers(user_a)))

    # 同一幂等键在删除后创建会返回 40905
    response = java.post(
        "/api/agent/v1/trips",
        headers={**agent_headers(user_a), "Idempotency-Key": key},
        json=trip_payload("PYTEST 权限行程"),
    )
    assert_error(response, 409, 40905)


def test_trip_validation_errors(java, user_a):
    headers = {**agent_headers(user_a), "Idempotency-Key": idem()}

    missing_plan = java.post(
        "/api/agent/v1/trips", headers=headers,
        json={"title": "缺 plan", "start_date": "2026-11-10", "end_date": "2026-11-11"})
    assert_error(missing_plan, 400, 40001)

    missing_dates = trip_payload("PYTEST 缺日期")
    missing_dates.pop("start_date")
    missing_dates.pop("end_date")
    response = java.post("/api/agent/v1/trips", headers=headers, json=missing_dates)
    assert_error(response, 400, 40002)

    day_mismatch = trip_payload("PYTEST 天数不一致")
    day_mismatch["plan"]["days"] = day_mismatch["plan"]["days"][:1]
    response = java.post("/api/agent/v1/trips", headers=headers, json=day_mismatch)
    assert_error(response, 400, 40001)

    bad_coords = copy.deepcopy(trip_payload("PYTEST 坐标错误"))
    bad_coords["plan"]["days"][0]["items"][0]["coords"] = [120.1]
    response = java.post("/api/agent/v1/trips", headers=headers, json=bad_coords)
    assert_error(response, 400, 40001)

    bad_time_slot = copy.deepcopy(trip_payload("PYTEST 时段错误"))
    bad_time_slot["plan"]["days"][0]["items"][0]["time_slot"] = "深夜"
    response = java.post("/api/agent/v1/trips", headers=headers, json=bad_time_slot)
    assert_error(response, 400, 40001)


def test_trip_list_limit_is_clamped(java, user_a):
    data = assert_success(java.get(
        "/api/agent/v1/trips", headers=agent_headers(user_a), params={"limit": 999}))
    assert isinstance(data["trips"], list)
    assert len(data["trips"]) <= 20


def test_concurrent_idempotency_creates_one_trip(java, user_a):
    key = idem()
    payload = trip_payload("PYTEST 并发幂等")

    def create_once():
        with httpx.Client(base_url=str(java.base_url),
                          timeout=httpx.Timeout(30, connect=5)) as client:
            return client.post(
                "/api/agent/v1/trips",
                headers={**agent_headers(user_a), "Idempotency-Key": key},
                json=payload,
            )

    with ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(lambda _: create_once(), range(2)))

    assert all(r.status_code in (200, 201) for r in responses), [r.text for r in responses]
    ids = {r.json()["data"]["id"] for r in responses}
    assert len(ids) == 1, [r.text for r in responses]

    trip_id = ids.pop()
    data = assert_success(java.get(
        "/api/agent/v1/trips", headers=agent_headers(user_a), params={"limit": 50}))
    matching = [trip for trip in data["trips"] if trip["id"] == trip_id]
    assert len(matching) == 1


def test_taxi_flow(java, user_a, user_b):
    estimate = assert_success(java.post(
        "/api/agent/v1/taxi/estimates",
        headers=agent_headers(user_a),
        json=taxi_estimate_payload(),
    ))
    assert estimate["mock"] is True
    assert estimate["vehicle_options"]

    key = idem()
    created = assert_success(java.post(
        "/api/agent/v1/taxi/orders",
        headers={**agent_headers(user_a), "Idempotency-Key": key},
        json=taxi_order_payload(),
    ), 201)
    order_id = created["id"]
    assert created["status"] == "CREATED"
    assert created["provider"] == "MOCK"

    replay = assert_success(java.post(
        "/api/agent/v1/taxi/orders",
        headers={**agent_headers(user_a), "Idempotency-Key": key},
        json=taxi_order_payload(),
    ))
    assert replay["id"] == order_id
    assert replay["idempotent_replay"] is True

    assert_error(java.get(
        f"/api/agent/v1/taxi/orders/{order_id}", headers=agent_headers(user_b)),
        404, 40403)

    cancelled = assert_success(java.post(
        f"/api/agent/v1/taxi/orders/{order_id}/cancel",
        headers=agent_headers(user_a),
        json={"reason": "pytest"},
    ))
    assert cancelled["status"] == "CANCELLED"

    again = assert_success(java.post(
        f"/api/agent/v1/taxi/orders/{order_id}/cancel",
        headers=agent_headers(user_a),
        json={},
    ))
    assert again["status"] == "CANCELLED"

    # 已取消订单不能完成（前端模拟完成接口）
    completed = java.post(
        f"/api/v1/taxi/orders/{order_id}/complete",
        headers=frontend_headers(user_a),
    )
    assert_error(completed, 409, 40903)


def test_taxi_create_requires_idempotency_key(java, user_a):
    response = java.post(
        "/api/agent/v1/taxi/orders",
        headers=agent_headers(user_a),
        json=taxi_order_payload(),
    )
    assert_error(response, 400, 40000)
