"""tripAgent 节点共用的消息、JSON 工具函数。"""

from typing import Any, Dict, List, Optional

from langchain_core.messages import AIMessage, HumanMessage

from agent.tripAgent.TripState import TripState
from util import JsonUtil


# ------------------ 用户偏好展示 ------------------

CROWD_LABELS = {
    "general": "普通人群",
    "family": "家庭出行（带父母/孩子）",
    "couple": "情侣出行",
    "elderly": "老人出行",
    "solo": "独自出行",
    "student": "学生出行",
}

BUDGET_LABELS = {
    "economy": "经济型",
    "comfort": "舒适型",
    "premium": "高端型",
}

TRANSPORT_LABELS = {
    "walking": "步行优先",
    "driving": "驾车优先",
    "transit": "公共交通优先",
}

PACE_LABELS = {
    "relaxed": "轻松",
    "moderate": "适中",
    "intensive": "紧凑",
}


def crowd_label(value: Optional[str]) -> str:
    return CROWD_LABELS.get(value or "", value or "普通人群")


def budget_label(value: Optional[str]) -> str:
    return BUDGET_LABELS.get(value or "", value or "经济型")


def preference_context(
        preference: Optional[Dict[str, Any]],
        budget_level: Optional[str] = None,
) -> str:
    """把 Java 用户偏好压缩成一段可注入规划提示词的上下文。"""
    if not preference and not budget_level:
        return ""

    preference = preference or {}
    parts: List[str] = []

    crowd = preference.get("crowd")
    if crowd:
        parts.append(f"同行人群：{crowd_label(crowd)}")

    budget = budget_level or preference.get("budget_level")
    if budget:
        parts.append(f"预算档位：{budget_label(budget)}")

    transport = preference.get("preferred_transport")
    if transport:
        parts.append(f"交通偏好：{TRANSPORT_LABELS.get(transport, transport)}")

    pace = preference.get("travel_pace")
    if pace:
        parts.append(f"行程节奏：{PACE_LABELS.get(pace, pace)}")

    interests = preference.get("interests") or []
    if interests:
        parts.append("兴趣：" + "、".join(str(item) for item in interests if item))

    foods = preference.get("food_preferences") or []
    if foods:
        parts.append("饮食偏好：" + "、".join(str(item) for item in foods if item))

    accommodation = preference.get("accommodation_preference")
    if accommodation:
        parts.append(f"住宿偏好：{accommodation}")

    notes = preference.get("notes")
    if notes:
        parts.append(f"补充说明：{notes}")

    return "；".join(parts)


def current_turn_messages(state: TripState) -> List[Any]:
    """截取本轮消息（最后一条 HumanMessage 及之后的内容）"""
    messages = state.get("messages") or []
    for index in range(len(messages) - 1, -1, -1):
        if isinstance(messages[index], HumanMessage):
            return list(messages[index:])
    return list(messages)


def intent_history(state: TripState) -> List[Any]:
    """意图识别只保留有文本内容的用户/助手消息，避免工具调用污染上下文"""
    history = []
    for message in state.get("messages") or []:
        if isinstance(message, HumanMessage):
            history.append(message)
        elif isinstance(message, AIMessage) and message.content and not getattr(message, "tool_calls", None):
            if not (message.response_metadata or {}).get("internal"):
                history.append(message)
    return history[-8:]


def previous_plan(state: TripState, destination: str) -> Optional[Dict[str, Any]]:
    """只有目的地相同时，才把上一版行程作为修改参考"""
    plan = state.get("plan")
    if plan and plan.get("destination") == destination:
        return plan
    return None


def content_to_text(content: Any) -> str:
    """把模型返回的 content 统一转成字符串"""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and block.get("type") == "text":
                parts.append(block.get("text", ""))
        return "".join(parts)
    return ""


def extract_json(text: str) -> Optional[Dict[str, Any]]:
    """从模型输出中截取第一个 JSON 对象"""
    if not text:
        return None
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        return JsonUtil.to_obj(text[start:end + 1])
    except Exception:
        return None
