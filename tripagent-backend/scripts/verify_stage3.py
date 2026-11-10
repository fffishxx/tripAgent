"""阶段三端到端验收脚本。

仅使用 HTTP API，不直接访问数据库。测试用户/数据需要运行后清理。
"""

import json
import os
import urllib.error
import urllib.request

BASE = os.environ.get("BASE_URL", "http://localhost:8080")
PHONE_A = "19900000001"
PHONE_B = "19900000002"
PASSWORD = "Test1234"

failures = []


def call(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json; charset=UTF-8"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            raw = response.read().decode("utf-8")
            try:
                parsed = json.loads(raw) if raw else None
            except json.JSONDecodeError:
                parsed = raw
            return response.status, parsed, dict(response.headers)
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8")
        try:
            parsed = json.loads(raw)
        except Exception:
            parsed = raw
        return error.code, parsed, dict(error.headers)


def check(label, condition):
    print(("PASS " if condition else "FAIL ") + label)
    if not condition:
        failures.append(label)


def register_and_login(phone):
    status, result, _ = call("POST", "/api/v1/auth/register", {
        "phone": phone,
        "password": PASSWORD,
    })
    check("register " + phone, status == 201 and result.get("code") == 0)
    user_id = result["data"]["userId"]
    check("register id string " + phone, isinstance(user_id, str))

    status, result, _ = call("POST", "/api/v1/auth/login", {
        "phone": phone,
        "password": PASSWORD,
    })
    check("login " + phone, status == 200 and result.get("code") == 0)
    token = result["data"]["accessToken"]
    check("login token present " + phone, isinstance(token, str) and len(token) > 30)
    check("login expiresIn " + phone, result["data"]["expiresIn"] == 86400)
    return user_id, token


def trip_payload(start="2026-11-10", end="2026-11-11", day_two_name="灵隐寺", version=None):
    payload = {
        "title": "杭州两日游",
        "startDate": start,
        "endDate": end,
        "description": "阶段三测试行程",
        "plan": {
            "destination": "杭州",
            "days": [
                {
                    "dayIndex": 1,
                    "theme": "西湖",
                    "items": [
                        {
                            "itemType": "ATTRACTION",
                            "name": "西湖",
                            "address": "西湖区",
                            "longitude": 120.15507,
                            "latitude": 30.274084,
                            "timeSlot": "上午",
                            "durationMin": 180,
                            "reason": "环湖",
                            "externalPoiId": "B0FFH1",
                            "notes": None,
                        }
                    ],
                },
                {
                    "dayIndex": 2,
                    "theme": "灵隐",
                    "items": [
                        {
                            "itemType": "ATTRACTION",
                            "name": day_two_name,
                            "address": "西湖区",
                            "longitude": 120.10210,
                            "latitude": 30.24086,
                            "timeSlot": "上午",
                            "durationMin": 120,
                            "reason": "祈福",
                            "externalPoiId": None,
                            "notes": None,
                        }
                    ],
                },
            ],
            "lodgingArea": {"name": "湖滨商圈", "reason": "交通便利"},
            "diningSuggestions": [{"area": "河坊街", "cuisine": "杭帮菜", "note": "晚餐"}],
            "budgetEstimate": {"total": "1500-2000", "breakdown": "交通/餐饮/门票/住宿"},
            "assumptions": {
                "days": 2,
                "crowd": "family",
                "budgetLevel": "economy",
                "notes": ["测试假设"],
            },
            "validationStatus": "done",
            "warnings": [],
        },
    }
    if version is not None:
        payload["version"] = version
    return payload


print("== auth ==")
user_a_id, token_a = register_and_login(PHONE_A)
status, result, _ = call("POST", "/api/v1/auth/register", {
    "phone": PHONE_A,
    "password": PASSWORD,
})
check("duplicate phone 40901", status == 409 and result.get("code") == 40901)

status, result, _ = call("GET", "/api/v1/users/me")
check("no token 40101", status == 401 and result.get("code") == 40101)
status, result, _ = call("GET", "/api/v1/users/me", token="bad-token")
check("invalid token 40101", status == 401 and result.get("code") == 40101)

status, result, _ = call("GET", "/api/v1/users/me", token=token_a)
check("profile ok", status == 200 and result["data"]["userId"] == user_a_id)

print("== preference ==")
status, result, _ = call("GET", "/api/v1/preferences", token=token_a)
pref = result.get("data", {})
check("preference defaults",
      status == 200 and pref.get("crowd") == "general"
      and pref.get("budgetLevel") == "economy"
      and pref.get("preferredTransport") == "transit"
      and pref.get("travelPace") == "moderate"
      and pref.get("interests") == [])
status, result, _ = call("PUT", "/api/v1/preferences", {
    "crowd": "family",
    "budgetLevel": "comfort",
    "preferredTransport": "transit",
    "travelPace": "relaxed",
    "interests": ["history", "food"],
    "foodPreferences": ["不吃辣"],
    "accommodationPreference": "靠近地铁站",
    "notes": "带老人",
}, token=token_a)
pref = result.get("data", {})
check("preference update",
      status == 200 and pref.get("crowd") == "family"
      and pref.get("budgetLevel") == "comfort"
      and pref.get("interests") == ["history", "food"]
      and pref.get("foodPreferences") == ["不吃辣"])
status, result, _ = call("GET", "/api/v1/preferences", token=token_a)
check("preference read back",
      status == 200 and result["data"]["interests"] == ["history", "food"])

print("== trip ==")
status, result, _ = call("POST", "/api/v1/trips", trip_payload(), token=token_a)
check("trip create 201", status == 201 and result.get("code") == 0)
trip = result["data"]
trip_id = trip["id"]
check("trip id string", isinstance(trip_id, str))
check("trip source/version", trip["source"] == "MANUAL" and trip["version"] == 0)
check("trip extras", trip["plan"]["lodgingArea"]["name"] == "湖滨商圈")
check("trip item id string", isinstance(trip["plan"]["days"][0]["items"][0]["id"], str))

status, result, _ = call("GET", "/api/v1/trips", token=token_a)
check("trip list", status == 200 and result["data"]["total"] == 1
      and result["data"]["records"][0]["id"] == trip_id)

status, result, _ = call("GET", "/api/v1/trips/" + trip_id, token=token_a)
check("trip detail", status == 200 and result["data"]["version"] == 0)

status, result, _ = call("PUT", "/api/v1/trips/" + trip_id,
                         trip_payload(day_two_name="灵隐寺调整", version=0), token=token_a)
check("trip update version 1",
      status == 200 and result["data"]["version"] == 1
      and result["data"]["plan"]["days"][1]["items"][0]["name"] == "灵隐寺调整")

status, result, _ = call("PUT", "/api/v1/trips/" + trip_id,
                         trip_payload(version=0), token=token_a)
check("trip stale version 40902", status == 409 and result.get("code") == 40902)

missing_dates = trip_payload()
missing_dates.pop("startDate")
missing_dates.pop("endDate")
status, result, _ = call("POST", "/api/v1/trips", missing_dates, token=token_a)
check("trip dates required 40002", status == 400 and result.get("code") == 40002)

mismatch = trip_payload(start="2026-11-10", end="2026-11-12")
status, result, _ = call("POST", "/api/v1/trips", mismatch, token=token_a)
check("trip days mismatch 40001", status == 400 and result.get("code") == 40001)

empty_items = trip_payload()
for day in empty_items["plan"]["days"]:
    day["items"] = []
status, result, _ = call("POST", "/api/v1/trips", empty_items, token=token_a)
check("trip empty items 40001", status == 400 and result.get("code") == 40001)

print("== ownership ==")
user_b_id, token_b = register_and_login(PHONE_B)
status, result, _ = call("GET", "/api/v1/trips/" + trip_id, token=token_b)
check("trip ownership 40401", status == 404 and result.get("code") == 40401)
status, result, _ = call("GET", "/api/v1/trips", token=token_b)
check("user b trip list empty", status == 200 and result["data"]["total"] == 0)

status, _, _ = call("DELETE", "/api/v1/trips/" + trip_id, token=token_a)
check("trip delete", status == 200)
status, result, _ = call("GET", "/api/v1/trips/" + trip_id, token=token_a)
check("trip deleted 40401", status == 404 and result.get("code") == 40401)
status, _, _ = call("DELETE", "/api/v1/trips/" + trip_id, token=token_a)
check("trip delete idempotent", status == 200)

print("== favorite ==")
status, result, _ = call("POST", "/api/v1/favorites", {
    "externalPoiId": "B0FFH1",
    "name": "西湖",
    "city": "杭州",
    "address": "西湖区",
    "longitude": 120.15507,
    "latitude": 30.274084,
    "category": "风景名胜",
    "typecode": "110000",
    "note": "看日落",
    "source": "TRIP",
}, token=token_a)
check("favorite add 201", status == 201 and result["data"]["duplicate"] is False)
favorite_id = result["data"]["id"]
check("favorite id string", isinstance(favorite_id, str))
check("favorite place key", result["data"]["placeKey"] == "amap:B0FFH1")

status, result, _ = call("POST", "/api/v1/favorites", {
    "externalPoiId": "B0FFH1",
    "name": "西湖",
    "city": "杭州",
    "longitude": 120.15507,
    "latitude": 30.274084,
}, token=token_a)
check("favorite duplicate 200",
      status == 200 and result["data"]["duplicate"] is True
      and result["data"]["id"] == favorite_id)

status, result, _ = call("POST", "/api/v1/favorites", {
    "name": "河坊街",
    "city": "杭州",
    "longitude": 120.16923,
    "latitude": 30.24211,
    "source": "SEARCH",
}, token=token_a)
check("favorite geo add", status == 201 and result["data"]["placeKey"].startswith("geo:"))
geo_favorite_id = result["data"]["id"]

status, result, _ = call("GET", "/api/v1/favorites", token=token_a)
check("favorite list 2", status == 200 and result["data"]["total"] == 2)

status, _, _ = call("DELETE", "/api/v1/favorites/" + favorite_id, token=token_a)
check("favorite delete", status == 200)
status, _, _ = call("DELETE", "/api/v1/favorites/" + favorite_id, token=token_a)
check("favorite delete idempotent", status == 200)

status, result, _ = call("POST", "/api/v1/favorites", {
    "externalPoiId": "B0FFH1",
    "name": "西湖",
    "city": "杭州",
    "longitude": 120.15507,
    "latitude": 30.274084,
}, token=token_a)
check("favorite revive",
      status in (200, 201) and result["data"]["duplicate"] is False
      and result["data"]["id"] == favorite_id)

status, result, _ = call("DELETE", "/api/v1/favorites/" + geo_favorite_id, token=token_b)
check("favorite ownership 40402", status == 404 and result.get("code") == 40402)
status, result, _ = call("GET", "/api/v1/favorites", token=token_b)
check("user b favorites empty", status == 200 and result["data"]["total"] == 0)

print("== taxi ==")
status, result, _ = call("POST", "/api/v1/taxi/estimates", {
    "pickup": {"name": "西湖", "longitude": 120.15507, "latitude": 30.274084},
    "destination": {"name": "灵隐寺", "longitude": 120.10210, "latitude": 30.24086},
    "city": "杭州",
    "vehicleType": "快车",
}, token=token_a)
check("taxi estimate",
      status == 200 and result["data"]["distanceM"] > 0
      and result["data"]["durationMin"] >= 5
      and len(result["data"]["vehicleOptions"]) == 4
      and result["data"]["mock"] is True)

status, result, _ = call("POST", "/api/v1/taxi/orders", {
    "pickup": {"name": "西湖", "longitude": 120.15507, "latitude": 30.274084},
    "destination": {"name": "灵隐寺", "longitude": 120.10210, "latitude": 30.24086},
    "city": "杭州",
    "vehicleType": "快车",
}, token=token_a)
check("taxi order create 201",
      status == 201 and result["data"]["status"] == "CREATED"
      and result["data"]["provider"] == "MOCK"
      and result["data"]["orderNo"].startswith("TX"))
order_id = result["data"]["id"]
check("taxi order id string", isinstance(order_id, str))

status, result, _ = call("GET", "/api/v1/taxi/orders", token=token_a)
check("taxi list 1", status == 200 and result["data"]["total"] == 1)
status, result, _ = call("GET", "/api/v1/taxi/orders/" + order_id, token=token_a)
check("taxi detail", status == 200 and result["data"]["id"] == order_id)

status, result, _ = call("POST", "/api/v1/taxi/orders/" + order_id + "/cancel",
                         {"reason": "行程有变"}, token=token_a)
check("taxi cancel", status == 200 and result["data"]["status"] == "CANCELLED")
status, result, _ = call("POST", "/api/v1/taxi/orders/" + order_id + "/cancel",
                         {"reason": "再次取消"}, token=token_a)
check("taxi cancel idempotent", status == 200 and result["data"]["status"] == "CANCELLED")

status, result, _ = call("POST", "/api/v1/taxi/orders", {
    "pickup": {"name": "西湖", "longitude": 120.15507, "latitude": 30.274084},
    "destination": {"name": "灵隐寺", "longitude": 120.10210, "latitude": 30.24086},
    "city": "杭州",
    "vehicleType": "专车",
}, token=token_a)
order_two_id = result["data"]["id"]
status, result, _ = call("POST", "/api/v1/taxi/orders/" + order_two_id + "/complete",
                         token=token_a)
check("taxi complete", status == 200 and result["data"]["status"] == "COMPLETED")
status, result, _ = call("POST", "/api/v1/taxi/orders/" + order_two_id + "/cancel",
                         {"reason": "取消已完成"}, token=token_a)
check("taxi cancel completed 40903", status == 409 and result.get("code") == 40903)
status, result, _ = call("POST", "/api/v1/taxi/orders/" + order_two_id + "/complete",
                         token=token_a)
check("taxi complete idempotent", status == 200 and result["data"]["status"] == "COMPLETED")

status, result, _ = call("GET", "/api/v1/taxi/orders/" + order_id, token=token_b)
check("taxi ownership 40403", status == 404 and result.get("code") == 40403)
status, result, _ = call("GET", "/api/v1/taxi/orders", token=token_b)
check("user b taxi list empty", status == 200 and result["data"]["total"] == 0)

status, result, _ = call("POST", "/api/v1/taxi/estimates", {
    "pickup": {"name": "同一点", "longitude": 120.15507, "latitude": 30.274084},
    "destination": {"name": "同一点", "longitude": 120.15507, "latitude": 30.274084},
}, token=token_a)
check("taxi same point 40001", status == 400 and result.get("code") == 40001)

print("== swagger ==")
status, _, _ = call("GET", "/doc.html")
check("knife4j 200", status == 200)
status, docs, _ = call("GET", "/v3/api-docs")
paths = docs.get("paths", {}) if isinstance(docs, dict) else {}
check("openapi paths",
      status == 200
      and "/api/v1/auth/register" in paths
      and "/api/v1/trips" in paths
      and "/api/v1/favorites" in paths
      and "/api/v1/taxi/orders" in paths)

print("== cleanup markers ==")
print("user_a_id=" + str(user_a_id))
print("user_b_id=" + str(user_b_id))
print("trip_id=" + str(trip_id))
print("favorite_id=" + str(favorite_id))
print("order_id=" + str(order_id))
print("FAILURES=" + (",".join(failures) if failures else "none"))
raise SystemExit(1 if failures else 0)
