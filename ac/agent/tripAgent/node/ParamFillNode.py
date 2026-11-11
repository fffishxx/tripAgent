import uuid
from typing import Any, Dict, List

from langchain_core.runnables import RunnableConfig

from agent.tripAgent import prompts
from agent.tripAgent.AmapClient import amap_client
from agent.tripAgent.constants import DEFAULT_BUDGET_LEVEL, DEFAULT_CROWD, DEFAULT_DAYS
from agent.tripAgent.node.BaseTripNode import BaseTripNode
from agent.tripAgent.schemas import CROWD_TYPES
from agent.tripAgent.TripState import TripState
from agent.tripAgent.utils import budget_label, crowd_label
from clients import BackendError, backend_client
from config import logger

BUDGET_LEVELS = {"economy", "comfort", "premium"}


class ParamFillNode(BaseTripNode):
    """
    参数补全：
    - destination 缺失 -> 追问（唯一硬性阻断）
    - 登录用户先从 Java 读取偏好；本轮用户明确指定的人群优先于偏好
    - 天数默认 3 天、人群/预算优先使用用户偏好，否则使用默认值
    - 调用高德地理编码把目的地转为 adcode，供天气查询使用
    """

    async def execute(self, state: TripState, config: RunnableConfig) -> Dict[str, Any]:
        destination = (state.get("destination") or "").strip()
        if not destination:
            return {
                "need_clarification": True,
                "clarification_question": prompts.CLARIFY_DESTINATION,
            }

        preference = await self._load_preference(config)
        days = state.get("days") or DEFAULT_DAYS
        state_crowd = state.get("crowd")
        preference_crowd = (preference.get("crowd") or "").strip()

        if bool(state.get("crowd_explicit")) and state_crowd in CROWD_TYPES:
            crowd = state_crowd
            crowd_source = "user"
        elif preference_crowd in CROWD_TYPES:
            crowd = preference_crowd
            crowd_source = "preference"
        elif state_crowd in CROWD_TYPES:
            crowd = state_crowd
            crowd_source = "conversation"
        else:
            crowd = DEFAULT_CROWD
            crowd_source = "default"

        preference_budget = (preference.get("budget_level") or "").strip()
        if preference_budget in BUDGET_LEVELS:
            budget_level = preference_budget
            budget_source = "preference"
        else:
            budget_level = DEFAULT_BUDGET_LEVEL
            budget_source = "default"

        notes: List[str] = []
        if not state.get("days"):
            notes.append(f"未指定天数，默认 {DEFAULT_DAYS} 天")
        if crowd_source == "preference":
            notes.append(f"未指定同行人群，使用用户偏好：{crowd_label(crowd)}")
        elif crowd_source == "default":
            notes.append("未指定同行人群，默认普通人群")
        if budget_source == "preference":
            notes.append(f"预算档位使用用户偏好：{budget_label(budget_level)}")
        else:
            notes.append("未指定预算，默认按经济型规划")
        if preference:
            notes.append("已结合 Java 业务系统中的用户偏好")

        assumptions = {
            "days": int(days),
            "crowd": crowd,
            "budget_level": budget_level,
            "notes": notes,
        }

        # 目的地每次都可能变化，这里重新地理编码
        adcode = None
        try:
            geo = await amap_client.geocode(destination)
            adcode = geo.get("adcode") or None
        except Exception as e:
            logger.warning("目的地 %s 地理编码失败：%s", destination, e)

        return {
            "destination": destination,
            "days": int(days),
            "crowd": crowd,
            "budget_level": budget_level,
            "user_preference": preference,
            "city_adcode": adcode,
            "assumptions": assumptions,
            "need_clarification": False,
        }

    async def _load_preference(self, config: RunnableConfig) -> Dict[str, Any]:
        """登录用户从 Java 读取偏好；匿名或失败时返回空字典，不阻断规划。"""
        configurable = (config or {}).get("configurable", {}) or {}
        user_token = (configurable.get("user_token") or "").strip()
        user_id = configurable.get("user_id")
        if not user_token or user_id is None:
            return {}
        request_id = configurable.get("request_id") or uuid.uuid4().hex
        try:
            data = await backend_client.get_preference(user_token, user_id, request_id)
            return data if isinstance(data, dict) else {}
        except BackendError as exc:
            logger.warning("查询用户偏好失败，使用默认值：%s", exc)
            return {}
        except Exception as exc:
            logger.warning("查询用户偏好异常，使用默认值：%s", exc)
            return {}

    def route(self, state: TripState) -> str:
        """参数补全条件边：缺目的地 -> 追问，否则 -> 规划"""
        return "clarify" if state.get("need_clarification") else "planner"
