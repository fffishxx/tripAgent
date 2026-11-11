"""Python 跨服务集成测试公共配置。

前置：
- Java 后端运行在 JAVA_BASE（默认 8080），连接可用 MySQL。
- Python Agent 运行在 PY_BASE（默认 18089），连接 Nacos/MySQL/PostgreSQL/Redis。
"""

import os
import uuid

import httpx
import pytest

JAVA_BASE = os.environ.get("JAVA_BASE", "http://127.0.0.1:8080")
PY_BASE = os.environ.get("PY_BASE", "http://127.0.0.1:18089")
SERVICE_TOKEN = os.environ.get("AGENT_SERVICE_TOKEN", "dev-agent-service-token")
PASSWORD = os.environ.get("TEST_PASSWORD", "Test1234")
APP_KEY = os.environ.get("APP_KEY", "ddd8c127b3c1baa5f2ca7280d287a102")
APP_SECRET = os.environ.get("APP_SECRET", "5ca5087fd74a5afd5cb1cad3016e4980")

USER_A_PHONE = os.environ.get("TEST_PHONE_A", "19900000103")
USER_B_PHONE = os.environ.get("TEST_PHONE_B", "19900000104")

pytestmark = pytest.mark.integration


@pytest.fixture(scope="session")
def java():
    with httpx.Client(base_url=JAVA_BASE, timeout=httpx.Timeout(60, connect=5)) as client:
        yield client


@pytest.fixture(scope="session")
def py():
    with httpx.Client(base_url=PY_BASE, timeout=httpx.Timeout(60, connect=5)) as client:
        yield client


@pytest.fixture(scope="session", autouse=True)
def ensure_services(java, py):
    try:
        response = java.get("/api/v1/health")
    except httpx.HTTPError as exc:
        pytest.skip(f"Java backend 不可用 ({JAVA_BASE}): {exc}")
    if response.status_code != 200:
        pytest.skip(f"Java backend 健康检查失败: HTTP {response.status_code}")

    try:
        response = py.get("/health")
    except httpx.HTTPError as exc:
        pytest.skip(f"Python Agent 不可用 ({PY_BASE}): {exc}")
    if response.status_code != 200:
        pytest.skip(f"Python Agent 健康检查失败: HTTP {response.status_code}")


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
def java_user_a(java):
    token, user_id = register_or_login(java, USER_A_PHONE)
    return {"token": token, "user_id": user_id, "phone": USER_A_PHONE}


@pytest.fixture(scope="session")
def java_user_b(java):
    token, user_id = register_or_login(java, USER_B_PHONE)
    return {"token": token, "user_id": user_id, "phone": USER_B_PHONE}


@pytest.fixture(scope="session")
def gateway_headers(py):
    """直接访问 Agent 时模拟 Gateway：服务 Token + request-from。"""
    response = py.post("/auth/token", json={
        "app_key": APP_KEY, "app_secret": APP_SECRET,
    })
    assert response.status_code == 200, response.text
    return {
        "request-from": "agent-center-gateway",
        "Authorization": "Bearer " + response.json()["token"],
    }


def session_headers(gateway, user):
    """在 Gateway 服务身份之上增加可信用户头。"""
    return {
        **gateway,
        "X-User-Id": str(user["user_id"]),
        "X-User-Token": user["token"],
    }


def idem() -> str:
    return uuid.uuid4().hex


def plan() -> dict:
    return {
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
    }


def trip_payload(title="PYTEST PY 杭州两日游", version=None) -> dict:
    payload = {
        "title": title,
        "start_date": "2026-11-10",
        "end_date": "2026-11-11",
        "description": "python integration",
        "plan": plan(),
    }
    if version is not None:
        payload["version"] = version
    return payload


def taxi_payload() -> dict:
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
        "vehicle_type": "快车",
        "provider": "MOCK",
    }
