"""行程/打车管理动作识别。

将“保存/查询/更新/删除行程”和“查询/取消打车订单”转换成确定性动作，
避免依赖 Nacos 意图提示词是否已经更新。
"""

SAVE_WORDS = ("保存", "存下来", "存一下")
UPDATE_WORDS = (
    "更新已保存",
    "更新这份行程",
    "更新行程",
    "保存修改",
    "同步到已保存",
)
DELETE_WORDS = ("删除", "删掉", "移除")
# 注意：“我保存的”而不是“我保存”，避免“帮我保存一下这份行程”被误判为查询
QUERY_TRIP_WORDS = ("查询", "查看", "有哪些", "保存过", "我保存的", "我的", "之前保存")


def _contains(text: str, words) -> bool:
    return any(word in text for word in words)


def detect_manage_action(
        text: str,
        has_plan: bool = False,
        has_saved_trip: bool = False,
) -> str | None:
    """
    返回：
    - SAVE_TRIP / QUERY_TRIP / UPDATE_TRIP / DELETE_TRIP
    - QUERY_TAXI / CANCEL_TAXI
    - None
    """
    text = (text or "").strip()
    if not text:
        return None

    # 打车订单优先，避免“查询我的打车订单”被识别为查询行程
    if _contains(text, ("打车", "叫车", "查询订单", "我的订单")):
        if _contains(text, ("取消",)):
            return "CANCEL_TAXI"
        if _contains(text, ("订单", "查询", "查看")):
            return "QUERY_TAXI"

    if _contains(text, ("取消",)) and _contains(text, ("订单", "打车", "叫车")):
        return "CANCEL_TAXI"

    # 删除行程：必须明确提到行程，避免把“删除某个景点”误判成删除整份行程
    if _contains(text, DELETE_WORDS) and ("行程" in text or "保存的" in text):
        return "DELETE_TRIP"

    # 更新行程（“修改第二天行程”仍由规划子图重新生成，不在这里匹配）
    if _contains(text, UPDATE_WORDS) and (has_plan or has_saved_trip):
        return "UPDATE_TRIP"

    # 查询行程
    if "行程" in text and _contains(text, QUERY_TRIP_WORDS):
        return "QUERY_TRIP"

    # 保存行程
    if _contains(text, SAVE_WORDS) and has_plan:
        return "SAVE_TRIP"

    return None
