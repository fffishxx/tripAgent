"""行程/打车管理动作识别测试。"""

import pytest


@pytest.fixture(scope="module")
def detect(load_module):
    module = load_module("trip_manage_utils_under_test", "agent/tripAgent/trip_manage_utils.py")
    return module.detect_manage_action


@pytest.mark.parametrize("text,has_plan,has_saved,expected", [
    ("保存这份行程", True, False, "SAVE_TRIP"),
    ("保存这份行程", False, False, None),
    ("帮我保存一下这份行程", True, False, "SAVE_TRIP"),
    ("查询我之前保存的行程", False, False, "QUERY_TRIP"),
    ("我有哪些行程", False, False, "QUERY_TRIP"),
    ("更新已保存的行程", False, True, "UPDATE_TRIP"),
    ("更新这份行程", True, False, "UPDATE_TRIP"),
    ("更新这份行程", False, False, None),
    ("删除这份行程", False, False, "DELETE_TRIP"),
    ("删掉保存的记录", False, False, "DELETE_TRIP"),
    ("删除西湖这个景点", False, False, None),
    ("查询我的打车订单", False, False, "QUERY_TAXI"),
    ("取消订单", False, False, "CANCEL_TAXI"),
    ("取消我的打车订单", False, False, "CANCEL_TAXI"),
    ("查询我的订单", False, False, "QUERY_TAXI"),
    ("随便聊聊", False, False, None),
    ("", False, False, None),
])
def test_detect_manage_action(detect, text, has_plan, has_saved, expected):
    assert detect(text, has_plan=has_plan, has_saved_trip=has_saved) == expected
