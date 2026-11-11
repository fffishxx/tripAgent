"""行程结构化输出字段测试。"""


def test_plan_item_supports_poi_metadata(load_module):
    schemas = load_module("trip_schemas_under_test", "agent/tripAgent/schemas.py")

    item = schemas.PlanItem(
        poi="西湖",
        coords=[120.15507, 30.274084],
        time_slot="上午",
        reason="环湖",
        duration_min=180,
        item_type="ATTRACTION",
        address="西湖区",
        external_poi_id="B0FFH123",
        notes="建议从断桥入口进入",
    )

    assert item.item_type == "ATTRACTION"
    assert item.address == "西湖区"
    assert item.external_poi_id == "B0FFH123"
    assert item.notes == "建议从断桥入口进入"


def test_plan_item_new_fields_are_optional(load_module):
    schemas = load_module("trip_schemas_optional_under_test", "agent/tripAgent/schemas.py")

    item = schemas.PlanItem(
        poi="灵隐寺",
        time_slot="下午",
        reason="避开人流",
    )

    assert item.item_type is None
    assert item.address is None
    assert item.external_poi_id is None
    assert item.notes is None
