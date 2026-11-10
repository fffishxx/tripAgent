"""阶段六全链路 E2E（新认证协议）。

链路：前端/脚本 -> Java -> Gateway -> Python Agent -> Java 业务接口。

覆盖：
- Java 注册/登录/偏好更新
- Java 代理会话创建/聊天（SSE）
- Agent 读取偏好、规划、保存、查询、更新、删除
- Java 模拟打车：估价 → 选车型 → 下单 → 查询 → 取消
- 匿名聊天触发 SSE 2002 登录提示

前置：Java 8080、Gateway 10086、Python Agent 18089、Nacos/MySQL/PG/Redis 可用。
"""

import json
import os
import uuid

import httpx

JAVA_BASE = os.environ.get("JAVA_BASE", "http://127.0.0.1:8080")
SERVICE_TOKEN = os.environ.get("AGENT_SERVICE_TOKEN", "dev-agent-service-token")
PHONE = os.environ.get("TEST_PHONE", "19900000005")
PASSWORD = os.environ.get("TEST_PASSWORD", "Test1234")

failures = []
session_id = None
anonymous_session_id = None
user_id = None
java_token = None


def check(label, condition):
    print(("PASS " if condition else "FAIL ") + label)
    if not condition:
        failures.append(label)


def json_call(client, method, url, **kwargs):
    response = client.request(method, url, **kwargs)
    try:
        body = response.json()
    except Exception:
        body = {}
    return response.status_code, body


def user_headers(token):
    return {"Authorization": "Bearer " + token} if token else {}


def sse_chat(client, token, question, sid):
    body = {
        "question": question,
        "sessionId": sid,
        "agentId": 1004,
    }
    plan = None
    card = None
    auth_event = None
    error = None
    texts = []
    with client.stream(
        "POST", JAVA_BASE + "/api/v1/agent/chat",
        json=body, headers=user_headers(token),
        timeout=httpx.Timeout(300, connect=10, read=300),
    ) as response:
        response.raise_for_status()
        for line in response.iter_lines():
            if not line.startswith("data:"):
                continue
            raw = line[5:].strip()
            if not raw:
                continue
            try:
                event = json.loads(raw)
            except json.JSONDecodeError:
                continue
            event_type = event.get("eventType")
            data = event.get("eventData")
            if event_type == 1001 and isinstance(data, str):
                texts.append(data)
            elif event_type == 1005:
                plan = data
            elif event_type == 1007:
                card = data
            elif event_type == 2002:
                auth_event = data
            elif event_type == 2001:
                error = data
            elif event_type == 1002:
                break
    return plan, card, auth_event, error, texts


def create_session(client, token):
    status, body = json_call(
        client, "POST", JAVA_BASE + "/api/v1/agent/sessions",
        params={"n": 1, "agent_id": 1004},
        headers=user_headers(token),
    )
    return status, body.get("sessionId") if isinstance(body, dict) else None


def delete_session(client, token, sid):
    if not sid or not token:
        return
    try:
        json_call(
            client, "DELETE", JAVA_BASE + "/api/v1/agent/sessions/history",
            params={"agent_id": 1004, "session_id": sid},
            headers=user_headers(token),
        )
    except Exception:
        pass


def agent_headers(token, uid):
    return {
        "X-User-Token": token,
        "X-User-Id": str(uid),
        "X-Service-Token": SERVICE_TOKEN,
        "X-Agent-Id": "1004",
        "Content-Type": "application/json",
    }


def agent_trips(client, token, uid):
    return json_call(
        client, "GET", JAVA_BASE + "/api/agent/v1/trips",
        headers=agent_headers(token, uid), params={"limit": 10},
    )


def agent_taxi_orders(client, token, uid, status=None):
    params = {"limit": 10}
    if status:
        params["status"] = status
    return json_call(
        client, "GET", JAVA_BASE + "/api/agent/v1/taxi/orders",
        headers=agent_headers(token, uid), params=params,
    )


try:
    with httpx.Client(timeout=httpx.Timeout(90, connect=10)) as client:
        # 1. 注册/登录
        status, body = json_call(client, "POST", JAVA_BASE + "/api/v1/auth/register", json={
            "phone": PHONE, "password": PASSWORD,
        })
        if status == 409:
            check("java register (existing test user)", True)
        else:
            check("java register", status == 201 and body.get("code") == 0)

        status, body = json_call(client, "POST", JAVA_BASE + "/api/v1/auth/login", json={
            "phone": PHONE, "password": PASSWORD,
        })
        check("java login", status == 200 and body.get("code") == 0)
        java_token = body["data"]["accessToken"]
        user_id = body["data"]["user"]["userId"]
        print("USER_ID=" + str(user_id))

        # 2. 设置用户偏好
        status, body = json_call(
            client, "PUT", JAVA_BASE + "/api/v1/preferences",
            headers=user_headers(java_token),
            json={
                "crowd": "family",
                "budgetLevel": "comfort",
                "preferredTransport": "transit",
                "travelPace": "relaxed",
                "interests": ["history", "food"],
                "foodPreferences": ["不吃辣"],
                "accommodationPreference": "靠近地铁站",
                "notes": "带老人，行程不要太赶",
            },
        )
        pref = body.get("data") or {}
        check("preference update",
              status == 200 and pref.get("crowd") == "family"
              and pref.get("budgetLevel") == "comfort")

        # 3. Java 代理创建会话
        status, session_id = create_session(client, java_token)
        check("session create", status == 200 and bool(session_id))
        print("SESSION_ID=" + str(session_id))

        # 4. 规划：验证偏好进入 assumptions
        plan, card, auth_event, error, texts = sse_chat(
            client, java_token,
            "帮我规划杭州 2026-11-10 到 2026-11-12 三日游",
            session_id,
        )
        check("planning no backend error", error is None)
        check("plan event", isinstance(plan, dict))
        assumptions = (plan or {}).get("assumptions") or {}
        check("plan preference crowd", assumptions.get("crowd") == "family")
        check("plan preference budget", assumptions.get("budget_level") == "comfort")
        check("plan preference notes", any(
            "已结合" in note for note in assumptions.get("notes") or []))
        check("plan 3 days", len((plan or {}).get("days") or []) == 3)

        # 5. 保存
        plan, card, auth_event, error, texts = sse_chat(
            client, java_token, "保存这份行程", session_id)
        check("save no error", error is None and any("已保存" in text for text in texts))
        status, body = agent_trips(client, java_token, user_id)
        trips = (body.get("data") or {}).get("trips") or []
        trip = trips[0] if trips else {}
        trip_id = trip.get("id")
        check("save one trip", status == 200 and (body.get("data") or {}).get("total") == 1)
        check("save version 0", trip.get("version") == 0)

        # 6. 查询
        plan, card, auth_event, error, texts = sse_chat(
            client, java_token, "查询我之前保存的行程", session_id)
        query_text = "\n".join(texts)
        check("query trips no error", error is None)
        check("query trips text", "你保存的行程" in query_text and str(trip_id) in query_text)

        # 7. 更新
        plan, card, auth_event, error, texts = sse_chat(
            client, java_token, "更新已保存的行程", session_id)
        update_text = "\n".join(texts)
        check("update trip no error", error is None and "已更新" in update_text)
        status, body = agent_trips(client, java_token, user_id)
        updated = ((body.get("data") or {}).get("trips") or [{}])[0]
        check("update version 1", updated.get("version") == 1)

        # 8. 删除
        plan, card, auth_event, error, texts = sse_chat(
            client, java_token, "删除这份行程", session_id)
        delete_text = "\n".join(texts)
        check("delete trip no error", error is None and "已删除" in delete_text)
        status, body = agent_trips(client, java_token, user_id)
        check("trip list empty after delete",
              status == 200 and (body.get("data") or {}).get("total") == 0)

        # 9. 打车估价 → 选择车型
        plan, card, auth_event, error, texts = sse_chat(
            client, java_token, "从西湖打车到灵隐寺", session_id)
        check("taxi estimate no error", error is None)
        check("taxi estimate card", isinstance(card, dict)
              and card.get("status") == "SELECT_VEHICLE"
              and len(card.get("vehicle_options") or []) >= 1)

        plan, card, auth_event, error, texts = sse_chat(
            client, java_token, "快车", session_id)
        taxi_card = card if isinstance(card, dict) else {}
        check("taxi create no error", error is None)
        check("taxi create card",
              taxi_card.get("status") == "CREATED"
              and bool(taxi_card.get("java_order_id"))
              and taxi_card.get("provider") == "mock")
        taxi_order_id = taxi_card.get("java_order_id")

        # 10. 查询/取消打车订单
        plan, card, auth_event, error, texts = sse_chat(
            client, java_token, "查询我的打车订单", session_id)
        check("taxi query no error", error is None)
        check("taxi query text", "你最近的打车订单" in "\n".join(texts))

        plan, card, auth_event, error, texts = sse_chat(
            client, java_token, "取消订单", session_id)
        check("taxi cancel no error", error is None and "已取消" in "\n".join(texts))
        status, body = agent_taxi_orders(client, java_token, user_id, status="CANCELLED")
        orders = (body.get("data") or {}).get("orders") or []
        check("taxi cancelled in java",
              status == 200 and any(str(o.get("id")) == str(taxi_order_id) for o in orders))

        # 11. 匿名聊天 -> SSE 2002
        anonymous_session_id = uuid.uuid4().hex
        plan, card, auth_event, error, texts = sse_chat(
            client, "", "从西湖打车到灵隐寺", anonymous_session_id)
        check("anonymous auth event", isinstance(auth_event, dict)
              and auth_event.get("reason") == "LOGIN_REQUIRED")

except Exception as exc:
    check("e2e exception: " + type(exc).__name__, False)

finally:
    try:
        with httpx.Client(timeout=httpx.Timeout(30, connect=5)) as client:
            delete_session(client, java_token, session_id)
    except Exception:
        pass

print("FAILURES=" + (",".join(failures) if failures else "none"))
print("CLEANUP_PHONE=" + PHONE)
print("CLEANUP_SESSION=" + str(session_id))
raise SystemExit(1 if failures else 0)
