"""Java API 集成测试公共配置。

运行前需启动 Java 后端（连接可用的 MySQL），默认地址 http://127.0.0.1:8080。
"""

import os
import uuid

import httpx
import pytest

JAVA_BASE = os.environ.get("JAVA_BASE", "http://127.0.0.1:8080")
SERVICE_TOKEN = os.environ.get("AGENT_SERVICE_TOKEN", "dev-agent-service-token")
PASSWORD = os.environ.get("TEST_PASSWORD", "Test1234")

USER_A_PHONE = os.environ.get("TEST_PHONE_A", "19900000101")
USER_B_PHONE = os.environ.get("TEST_PHONE_B", "19900000102")

pytestmark = pytest.mark.integration


@pytest.fixture(scope="session")
def java():
    with httpx.Client(base_url=JAVA_BASE, timeout=httpx.Timeout(30, connect=5)) as client:
        yield client


@pytest.fixture(scope="session", autouse=True)
def ensure_java(java):
    try:
        response = java.get("/api/v1/health")
    except httpx.HTTPError as exc:
        pytest.skip(f"Java backend 不可用 ({JAVA_BASE}): {exc}")
    if response.status_code != 200:
        pytest.skip(f"Java backend 健康检查失败: HTTP {response.status_code}")


def register_or_login(client: httpx.Client, phone: str) -> tuple[str, int]:
    register = client.post("/api/v1/auth/register", json={
        "phone": phone, "password": PASSWORD,
    })
    if register.status_code not in (201, 409):
        pytest.fail(f"注册失败: HTTP {register.status_code} {register.text}")

    login = client.post("/api/v1/auth/login", json={
        "phone": phone, "password": PASSWORD,
    })
    if login.status_code != 200:
        pytest.fail(f"登录失败: HTTP {login.status_code} {login.text}")
    body = login.json()
    assert body["code"] == 0, body
    return body["data"]["accessToken"], body["data"]["user"]["userId"]


@pytest.fixture(scope="session")
def user_a(java):
    token, user_id = register_or_login(java, USER_A_PHONE)
    return {"token": token, "user_id": user_id, "phone": USER_A_PHONE}


@pytest.fixture(scope="session")
def user_b(java):
    token, user_id = register_or_login(java, USER_B_PHONE)
    return {"token": token, "user_id": user_id, "phone": USER_B_PHONE}


def frontend_headers(user):
    return {
        "Authorization": "Bearer " + user["token"],
        "Content-Type": "application/json",
    }


def agent_headers(user, service_token=True, agent_id="1004"):
    headers = {
        "X-User-Token": user["token"],
        "X-User-Id": str(user["user_id"]),
        "Content-Type": "application/json",
    }
    if service_token:
        headers["X-Service-Token"] = SERVICE_TOKEN
        headers["X-Agent-Id"] = agent_id
    return headers


def idem() -> str:
    return uuid.uuid4().hex


def trip_payload(title: str = "PYTEST 杭州两日游", version: int | None = None) -> dict:
    payload = {
        "title": title,
        "start_date": "2026-11-10",
        "end_date": "2026-11-11",
        "description": "pytest integration",
        "plan": {
            "destination": "杭州",
            "days": [
                {
                    "day_index": 1,
                    "theme": "西湖",
                    "items": [{
                        "poi": "西湖",
                        "coords": [120.15507, 30.274084],
                        "item_type": "ATTRACTION",
                        "address": "西湖区",
                        "time_slot": "上午",
                        "reason": "环湖",
                        "duration_min": 180,
                    }],
                },
                {
                    "day_index": 2,
                    "theme": "灵隐",
                    "items": [{
                        "poi": "灵隐寺",
                        "coords": [120.10210, 30.24086],
                        "item_type": "ATTRACTION",
                        "address": "西湖区",
                        "time_slot": "上午",
                        "reason": "祈福",
                        "duration_min": 120,
                    }],
                },
            ],
        },
    }
    if version is not None:
        payload["version"] = version
    return payload


def create_trip(client, user, title="PYTEST 杭州两日游", key=None):
    key = key or idem()
    response = client.post(
        "/api/agent/v1/trips",
        headers={**agent_headers(user), "Idempotency-Key": key},
        json=trip_payload(title),
    )
    return response, key


def taxi_estimate_payload() -> dict:
    return {
        "pickup": {
            "name": "西湖",
            "longitude": 120.15507,
            "latitude": 30.274084,
        },
        "destination": {
            "name": "灵隐寺",
            "longitude": 120.10210,
            "latitude": 30.24086,
        },
        "city": "杭州",
    }


def taxi_order_payload() -> dict:
    return {
        **taxi_estimate_payload(),
        "vehicle_type": "快车",
        "provider": "MOCK",
    }
