"""阶段四 Java Agent API 验收脚本（不依赖 Python Agent）。"""

import json
import os
import urllib.error
import urllib.request

BASE = os.environ.get("BASE_URL", "http://localhost:8080")
PHONE = "19900000003"
PASSWORD = "Test1234"
SERVICE_TOKEN = os.environ.get("AGENT_SERVICE_TOKEN", "dev-agent-service-token")

failures = []
USER_ID = None


def call(method, path, body=None, token=None, user_id=None, service_token=None, idem=None):
    headers = {"Content-Type": "application/json; charset=UTF-8"}
    if token:
        headers["X-User-Token"] = token
        headers["X-User-Id"] = str(user_id or USER_ID)
    if service_token is not None:
        headers["X-Service-Token"] = service_token
    if idem:
        headers["Idempotency-Key"] = idem
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read().decode("utf-8")
            try:
                parsed = json.loads(raw) if raw else None
            except json.JSONDecodeError:
                parsed = raw
            return resp.status, parsed
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            parsed = json.loads(raw)
        except Exception:
            parsed = raw
        return e.code, parsed


def check(label, condition):
    print(("PASS " if condition else "FAIL ") + label)
    if not condition:
        failures.append(label)


def trip_payload(day_two_poi="灵隐寺", version=None):
    payload = {
        "title": "Agent 杭州两日游",
        "start_date": "2026-11-10",
        "end_date": "2026-11-11",
        "description": "阶段四 Agent API 测试",
        "plan": {
            "destination": "杭州",
            "days": [
                {
                    "day_index": 1,
                    "theme": "西湖",
                    "items": [
                        {
                            "poi": "西湖",
                            "coords": [120.15507, 30.274084],
                            "item_type": "ATTRACTION",
                            "address": "西湖区",
                            "time_slot": "上午",
                            "reason": "环湖",
                            "duration_min": 180,
                            "external_poi_id": "B0FFH1",
                            "notes": None,
                        }
                    ],
                },
                {
                    "day_index": 2,
                    "theme": "灵隐",
                    "items": [
                        {
                            "poi": day_two_poi,
                            "coords": [120.10210, 30.24086],
                            "item_type": "ATTRACTION",
                            "address": "西湖区",
                            "time_slot": "上午",
                            "reason": "祈福",
                            "duration_min": 120,
                            "external_poi_id": None,
                            "notes": None,
                        }
                    ],
                },
            ],
            "lodging_area": {"name": "湖滨商圈", "reason": "交通便利"},
            "dining_suggestions": [{"area": "河坊街", "cuisine": "杭帮菜", "note": "晚餐"}],
            "budget_estimate": {"total": "1500-2000", "breakdown": "交通/餐饮/门票/住宿"},
            "assumptions": {
                "days": 2,
                "crowd": "family",
                "budget_level": "economy",
                "notes": ["测试假设"],
            },
            "validation_status": "done",
            "warnings": [],
        },
    }
    if version is not None:
        payload["version"] = version
    return payload


print("== setup user ==")
status, result = call("POST", "/api/v1/auth/register", {
    "phone": PHONE, "password": PASSWORD,
})
check("register test user", status == 201 and result.get("code") == 0)
status, result = call("POST", "/api/v1/auth/login", {
    "phone": PHONE, "password": PASSWORD,
})
check("login test user", status == 200 and result.get("code") == 0)
TOKEN = result["data"]["accessToken"]
USER_ID = result["data"]["user"]["userId"]

print("== auth on agent api ==")
status, result = call("GET", "/api/agent/v1/preferences", token=TOKEN,
                      service_token=SERVICE_TOKEN)
check("agent preference 200", status == 200 and result.get("code") == 0)
pref = result.get("data", {})
check("agent preference snake_case",
      pref.get("budget_level") == "economy"
      and pref.get("preferred_transport") == "transit"
      and pref.get("travel_pace") == "moderate"
      and pref.get("interests") == [])

status, result = call("GET", "/api/agent/v1/preferences", token=TOKEN)
check("missing service token 40302", status == 403 and result.get("code") == 40302)

status, result = call("GET", "/api/agent/v1/preferences", token=TOKEN,
                      service_token="wrong-service-token")
check("wrong service token 40302", status == 403 and result.get("code") == 40302)

status, result = call("GET", "/api/agent/v1/preferences",
                      service_token=SERVICE_TOKEN)
check("missing user token 40101", status == 401 and result.get("code") == 40101)

print("== idempotent trip create ==")
IDEM = "agent-stage4-idem-001"
status, result = call("POST", "/api/agent/v1/trips", trip_payload(),
                      token=TOKEN, service_token=SERVICE_TOKEN, idem=IDEM)
check("agent trip create 201", status == 201 and result.get("code") == 0)
trip = result["data"]
trip_id = trip["id"]
check("agent trip id string", isinstance(trip_id, str))
check("agent trip snake_case",
      trip.get("start_date") == "2026-11-10"
      and trip.get("days_count") == 2
      and trip.get("source") == "AGENT"
      and trip.get("idempotent_replay") is False)
check("agent plan shape",
      trip["plan"]["days"][0]["items"][0]["poi"] == "西湖"
      and trip["plan"]["days"][0]["items"][0]["coords"] == [120.15507, 30.274084]
      and trip["plan"]["days"][0]["items"][0]["time_slot"] == "上午"
      and trip["plan"]["lodging_area"]["name"] == "湖滨商圈"
      and trip["plan"]["validation_status"] == "done")

status, result = call("POST", "/api/agent/v1/trips", trip_payload(),
                      token=TOKEN, service_token=SERVICE_TOKEN, idem=IDEM)
check("agent trip replay 200 same id",
      status == 200 and result.get("code") == 0
      and result["data"]["id"] == trip_id
      and result["data"]["idempotent_replay"] is True)

status, result = call("GET", "/api/agent/v1/trips", token=TOKEN,
                      service_token=SERVICE_TOKEN)
check("agent trip list total 1",
      status == 200 and result["data"]["total"] == 1
      and result["data"]["trips"][0]["id"] == trip_id)

status, result = call("GET", "/api/agent/v1/trips/" + trip_id, token=TOKEN,
                      service_token=SERVICE_TOKEN)
check("agent trip detail", status == 200 and result["data"]["id"] == trip_id)

print("== trip update / delete ==")
status, result = call("PUT", "/api/agent/v1/trips/" + trip_id,
                      trip_payload(day_two_poi="灵隐寺调整", version=0),
                      token=TOKEN, service_token=SERVICE_TOKEN)
check("agent trip update version 1",
      status == 200 and result["data"]["version"] == 1
      and result["data"]["plan"]["days"][1]["items"][0]["poi"] == "灵隐寺调整")

status, result = call("PUT", "/api/agent/v1/trips/" + trip_id,
                      trip_payload(version=0), token=TOKEN,
                      service_token=SERVICE_TOKEN)
check("agent trip stale version 40902",
      status == 409 and result.get("code") == 40902)

status, result = call("POST", "/api/agent/v1/trips", trip_payload(),
                      token=TOKEN, service_token=SERVICE_TOKEN)
check("agent trip missing idempotency 400", status == 400)

status, _ = call("DELETE", "/api/agent/v1/trips/" + trip_id, token=TOKEN,
                 service_token=SERVICE_TOKEN)
check("agent trip delete", status == 200)
status, result = call("GET", "/api/agent/v1/trips/" + trip_id, token=TOKEN,
                      service_token=SERVICE_TOKEN)
check("agent trip deleted 40401", status == 404 and result.get("code") == 40401)
status, _ = call("DELETE", "/api/agent/v1/trips/" + trip_id, token=TOKEN,
                 service_token=SERVICE_TOKEN)
check("agent trip delete idempotent", status == 200)

print("FAILURES=" + (",".join(failures) if failures else "none"))
raise SystemExit(1 if failures else 0)
