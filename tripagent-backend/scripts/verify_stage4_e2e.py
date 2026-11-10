"""阶段六最小闭环 E2E（新认证协议）。

Java 登录 -> Java 代理会话/聊天 -> Gateway -> Agent 生成行程
-> 用户确认保存 -> Agent 调 Java 落库 -> Java API 核对。

前置：Java 8080、Gateway 10086、Python Agent 18089、Nacos/MySQL/PG/Redis 可用。
"""

import json
import os
import uuid

import httpx

JAVA_BASE = os.environ.get("JAVA_BASE", "http://127.0.0.1:8080")
SERVICE_TOKEN = os.environ.get("AGENT_SERVICE_TOKEN", "dev-agent-service-token")
PHONE = os.environ.get("TEST_PHONE", "19900000003")
PASSWORD = os.environ.get("TEST_PASSWORD", "Test1234")

failures = []
session_id = None
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


def sse_chat(client, token, question, sid):
    body = {"question": question, "sessionId": sid, "agentId": 1004}
    headers = {"Authorization": "Bearer " + token} if token else {}
    plan = None
    error = None
    texts = []
    with client.stream(
        "POST", JAVA_BASE + "/api/v1/agent/chat", headers=headers, json=body,
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
            elif event_type == 2001:
                error = data
            elif event_type == 1002:
                break
    return plan, error, texts


def agent_trips(client, token, uid):
    return json_call(
        client, "GET", JAVA_BASE + "/api/agent/v1/trips",
        headers={
            "X-User-Token": token,
            "X-User-Id": str(uid),
            "X-Service-Token": SERVICE_TOKEN,
            "X-Agent-Id": "1004",
        },
        params={"limit": 10},
    )


try:
    with httpx.Client(timeout=httpx.Timeout(60, connect=10)) as client:
        status, body = json_call(client, "POST", JAVA_BASE + "/api/v1/auth/login", json={
            "phone": PHONE, "password": PASSWORD,
        })
        check("java login", status == 200 and body.get("code") == 0)
        java_token = body["data"]["accessToken"]
        user_id = body["data"]["user"]["userId"]
        print("USER_ID=" + str(user_id))

        status, body = json_call(
            client, "POST", JAVA_BASE + "/api/v1/agent/sessions",
            params={"n": 1, "agent_id": 1004},
            headers={"Authorization": "Bearer " + java_token})
        check("session create", status == 200 and bool(body.get("sessionId")))
        session_id = body.get("sessionId") if isinstance(body, dict) else None
        print("SESSION_ID=" + str(session_id))

        print("== planning chat (may take a while) ==")
        plan, error, texts = sse_chat(
            client, java_token,
            "帮我规划杭州 2026-11-10 到 2026-11-12 三日游", session_id)
        check("planning no backend error", error is None)
        check("plan event exists", isinstance(plan, dict))
        check("plan destination", isinstance(plan, dict) and plan.get("destination") == "杭州")
        check("plan 3 days", isinstance(plan, dict) and len(plan.get("days") or []) == 3)
        check("plan has items", isinstance(plan, dict)
              and any(day.get("items") for day in plan.get("days") or []))

        print("== save chat ==")
        _, error2, texts2 = sse_chat(client, java_token, "保存这份行程", session_id)
        check("save no backend error", error2 is None)
        check("save result text", any(
            ("已保存" in text) or ("保存成功" in text) for text in texts2))

        print("== save again ==")
        _, error3, texts3 = sse_chat(client, java_token, "保存这份行程", session_id)
        check("repeat save no backend error", error3 is None)
        check("repeat save text", any(
            ("已经保存过" in text) or ("未重复创建" in text) or ("已保存" in text)
            for text in texts3))

        status, body = agent_trips(client, java_token, user_id)
        check("java trip list", status == 200 and body.get("code") == 0)
        data = body.get("data") or {}
        trips = data.get("trips") or []
        check("java one trip only", data.get("total") == 1 and len(trips) == 1)
        if trips:
            trip = trips[0]
            check("java trip dates",
                  trip.get("start_date") == "2026-11-10"
                  and trip.get("end_date") == "2026-11-12"
                  and trip.get("days_count") == 3)

except Exception as exc:
    check("e2e exception: " + type(exc).__name__, False)

finally:
    if session_id and java_token:
        try:
            with httpx.Client(timeout=httpx.Timeout(30, connect=5)) as client:
                client.delete(
                    JAVA_BASE + "/api/v1/agent/sessions/history",
                    params={"agent_id": 1004, "session_id": session_id},
                    headers={"Authorization": "Bearer " + java_token},
                    timeout=30,
                )
        except Exception:
            pass

print("FAILURES=" + (",".join(failures) if failures else "none"))
print("CLEANUP_PHONE=" + PHONE)
print("CLEANUP_SESSION=" + str(session_id))
raise SystemExit(1 if failures else 0)
