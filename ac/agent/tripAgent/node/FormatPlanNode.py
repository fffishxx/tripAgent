from datetime import date
from typing import Any, Dict, List, Optional

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.runnables import RunnableConfig

from agent.prompts import system_prompt_config
from agent.tripAgent.AmapClient import amap_client
from agent.tripAgent.constants import DEFAULT_BUDGET_LEVEL, DEFAULT_CROWD, DEFAULT_DAYS
from agent.tripAgent.node.BaseTripNode import BaseTripNode
from agent.tripAgent.schemas import TravelPlan
from agent.tripAgent.TripState import TripState
from agent.tripAgent.utils import (
    content_to_text,
    current_turn_messages,
    extract_json,
    preference_context,
    previous_plan,
)
from agent.tripAgent.validation import parse_coords
from config import logger
from util import JsonUtil

ITEM_TYPES = {"ATTRACTION", "ACTIVITY", "MEAL", "HOTEL", "TRANSPORT", "OTHER"}


class FormatPlanNode(BaseTripNode):
    """
    结构化输出节点：
    1. 汇总本轮工具结果（并做兜底检索）
    2. 调用支持结构化输出的 LLM 生成行程
    3. 规范化天数/坐标，补齐缺失坐标
    4. 写入 plan，并生成一条用于会话历史的 AIMessage
    """

    def __init__(self, provider: str = "openai"):
        super().__init__(provider)
        self.plan_llm = self.create_structured_llm(TravelPlan, 0.3, "TripPlanFormat")

    async def execute(self, state: TripState, config: RunnableConfig) -> Dict[str, Any]:
        destination = state.get("destination") or ""
        days = int(state.get("days") or DEFAULT_DAYS)
        crowd = state.get("crowd") or DEFAULT_CROWD
        assumptions = state.get("assumptions") or {
            "days": days,
            "crowd": crowd,
            "budget_level": DEFAULT_BUDGET_LEVEL,
            "notes": [],
        }
        request_id = (config.get("configurable") or {}).get("request_id")
        last_plan = previous_plan(state, destination)

        # 汇总工具结果，并在工具未调用/失败时做兜底检索
        tool_data = self._collect_tool_data(state)
        tool_data = await self._ensure_tool_data(
            tool_data,
            destination=destination,
            adcode=state.get("city_adcode"),
        )

        params = {
            "destination": destination,
            "days": days,
            "crowd": crowd,
            "today": date.today().isoformat(),
            "assumption_notes": "；".join(assumptions.get("notes") or []) or "无",
            "previous_plan": JsonUtil.to_str(last_plan) if last_plan else "无",
            "tool_data": JsonUtil.to_str(tool_data),
        }
        system_prompt = system_prompt_config.format_plan_prompt.format(**params)
        preference_text = preference_context(
            state.get("user_preference") or {},
            state.get("budget_level"),
        )
        if preference_text:
            system_prompt += (
                "\n\n用户偏好（来自 Java 业务系统，优先级高于上文默认值）："
                + preference_text
            )
        human_message = HumanMessage(content=f"请生成 {destination} {days} 天行程。")

        try:
            result: TravelPlan = await self.plan_llm.ainvoke(
                [SystemMessage(content=system_prompt), human_message],
                config,
            )
            plan = result.model_dump()
        except Exception as e:
            logger.warning("结构化行程输出失败，尝试 JSON 兜底：%s", e)
            plan = await self._fallback_plan(system_prompt, human_message, config)

        plan = self._normalize_plan(plan, destination, days)
        await self._resolve_coords(plan, destination, tool_data)

        if not any(day.get("items") for day in plan.get("days") or []):
            raise RuntimeError("行程生成失败：未能生成有效的景点安排，请稍后重试")

        plan.update({
            "destination": destination,
            "assumptions": assumptions,
            "validation_status": "pending",
            "warnings": [],
        })

        summary = (
            f"已为你生成{destination} {days} 天行程，"
            f"正在后台校验景点之间的交通路线。"
        )
        message = AIMessage(
            content=summary,
            response_metadata={"trip_plan": plan, "request_id": request_id},
        )

        return {
            "plan": plan,
            "plan_request_id": request_id,
            "messages": [message],
            "validation_status": "pending",
            "warnings": [],
        }

    # ------------------ 工具结果汇总 ------------------

    def _collect_tool_data(self, state: TripState) -> Dict[str, List[Dict[str, Any]]]:
        """从本轮 ToolMessage 中汇总高德工具返回的数据"""
        data: Dict[str, List[Dict[str, Any]]] = {"weather": [], "poi": [], "food": []}
        seen_names: Dict[str, set] = {"poi": set(), "food": set()}

        for message in current_turn_messages(state):
            if not isinstance(message, ToolMessage):
                continue

            try:
                payload = message.content
                if isinstance(payload, str):
                    payload = JsonUtil.to_obj(payload)
                if not isinstance(payload, dict):
                    continue
            except Exception:
                # 工具错误信息不是 JSON，直接跳过
                continue

            if message.name == "weather_query":
                data["weather"].extend(payload.get("forecasts") or [])
            elif message.name in ("poi_search", "food_search"):
                key = "food" if message.name == "food_search" else "poi"
                for poi in payload.get("pois") or []:
                    name = poi.get("name")
                    if not name or name in seen_names[key]:
                        continue
                    seen_names[key].add(name)
                    data[key].append(poi)

        data["poi"] = data["poi"][:20]
        data["food"] = data["food"][:10]
        return data

    async def _ensure_tool_data(
            self,
            tool_data: Dict[str, List[Dict[str, Any]]],
            destination: str,
            adcode: Optional[str],
    ) -> Dict[str, List[Dict[str, Any]]]:
        """工具结果不足时做兜底检索，保证结构化输出有真实 POI 坐标可用"""
        if not tool_data.get("weather") and adcode:
            try:
                tool_data["weather"] = await amap_client.weather(adcode)
            except Exception as e:
                logger.warning("兜底天气查询失败：%s", e)

        if not tool_data.get("poi"):
            try:
                tool_data["poi"] = await amap_client.search_poi(
                    keywords=f"{destination} 景点",
                    city=destination,
                    limit=10,
                )
            except Exception as e:
                logger.warning("兜底 POI 搜索失败：%s", e)

        if not tool_data.get("food"):
            try:
                tool_data["food"] = await amap_client.search_poi(
                    keywords=f"{destination} 美食",
                    city=destination,
                    types="050000",
                    limit=8,
                )
            except Exception as e:
                logger.warning("兜底美食搜索失败：%s", e)

        if not tool_data.get("poi"):
            raise RuntimeError("未能获取目的地景点数据，请检查 AMAP_KEY 配置或网络后重试")

        return tool_data

    # ------------------ JSON 兜底 ------------------

    async def _fallback_plan(
            self,
            system_prompt: str,
            human_message: HumanMessage,
            config: RunnableConfig,
    ) -> Dict[str, Any]:
        """结构化行程 JSON 兜底"""
        fallback_prompt = f"{system_prompt}\n\n{system_prompt_config.format_json_instruction}"
        response = await self.chat_llm().ainvoke(
            [SystemMessage(content=fallback_prompt), human_message],
            config,
        )
        data = extract_json(content_to_text(response.content))
        if data is None:
            raise RuntimeError("行程生成失败：模型未返回有效 JSON")
        return TravelPlan.model_validate(data).model_dump()

    # ------------------ 行程规范化 ------------------

    def _normalize_plan(
            self,
            plan: Dict[str, Any],
            destination: str,
            days: int,
    ) -> Dict[str, Any]:
        """规范化行程：天数连续、字段补全、坐标解析"""
        raw_days = plan.get("days") or []
        normalized_days = []

        for index, day in enumerate(raw_days[:days]):
            if not isinstance(day, dict):
                continue

            items = []
            for item in day.get("items") or []:
                if not isinstance(item, dict):
                    continue
                poi = item.get("poi")
                if not poi:
                    continue
                items.append({
                    "poi": poi,
                    "coords": parse_coords(item.get("coords")),
                    "time_slot": item.get("time_slot") or "",
                    "reason": item.get("reason") or "",
                    "duration_min": self._safe_int(item.get("duration_min"), 60),
                    "item_type": self._normalize_item_type(item.get("item_type")),
                    "address": self._clean_text(item.get("address")),
                    "external_poi_id": self._clean_text(item.get("external_poi_id")),
                    "notes": self._clean_text(item.get("notes")),
                })

            normalized_days.append({
                "day_index": index + 1,
                "theme": day.get("theme") or f"第{index + 1}天",
                "items": items,
            })

        # 补足天数，保证与 assumptions 一致
        while len(normalized_days) < days:
            normalized_days.append({
                "day_index": len(normalized_days) + 1,
                "theme": "自由活动",
                "items": [],
            })

        plan["days"] = normalized_days

        lodging = plan.get("lodging_area")
        if not isinstance(lodging, dict) or not lodging.get("name"):
            plan["lodging_area"] = {
                "name": destination,
                "reason": "交通便利，便于串联主要景点",
            }
        else:
            lodging.setdefault("reason", "")

        dining = plan.get("dining_suggestions")
        plan["dining_suggestions"] = dining if isinstance(dining, list) else []

        budget = plan.get("budget_estimate")
        if not isinstance(budget, dict) or not budget.get("total"):
            plan["budget_estimate"] = {
                "total": "以实际消费为准",
                "breakdown": "交通/餐饮/门票/住宿",
            }
        else:
            budget.setdefault("breakdown", "")

        return plan

    async def _resolve_coords(
            self,
            plan: Dict[str, Any],
            destination: str,
            tool_data: Optional[Dict[str, Any]] = None,
    ):
        """
        补齐缺失坐标与 POI 元数据：优先使用本轮工具结果，缺失时按地点名称回查高德 POI。

        方案要求每个 item 必须带 coords（校验任务的输入），
        因此仍然无法补齐坐标的地点会从行程中移除。
        """
        poi_index = self._index_tool_pois(tool_data or {})

        for day in plan.get("days") or []:
            valid_items = []

            for item in day.get("items") or []:
                poi_name = (item.get("poi") or "").strip()
                meta = poi_index.get(poi_name)
                coords = parse_coords(item.get("coords"))
                if not coords and meta:
                    coords = parse_coords(meta.get("coords"))

                if not coords and poi_name:
                    try:
                        pois = await amap_client.search_poi(poi_name, destination, limit=1)
                        if pois:
                            meta = pois[0]
                            coords = parse_coords(meta.get("coords"))
                    except Exception as e:
                        logger.debug("补全 %s 坐标失败：%s", poi_name, e)

                if coords:
                    item["coords"] = coords
                    self._fill_poi_metadata(item, meta)
                    valid_items.append(item)
                else:
                    logger.warning("行程地点 %s 缺少坐标，已跳过", item.get("poi"))

            day["items"] = valid_items

    @staticmethod
    def _index_tool_pois(tool_data: Dict[str, Any]) -> Dict[str, Dict[str, Any]]:
        """按地点名称索引本轮工具返回的 POI/餐饮数据。"""
        index: Dict[str, Dict[str, Any]] = {}
        for key in ("poi", "food"):
            for poi in tool_data.get(key) or []:
                if not isinstance(poi, dict):
                    continue
                name = (poi.get("name") or "").strip()
                if name and name not in index:
                    index[name] = poi
        return index

    @classmethod
    def _fill_poi_metadata(
            cls,
            item: Dict[str, Any],
            meta: Optional[Dict[str, Any]],
    ) -> None:
        """用工具结果补齐 item 的地址、POI ID、类型；不覆盖模型已给出的合法值。"""
        if meta:
            if not item.get("address"):
                item["address"] = cls._clean_text(meta.get("address"))
            if not item.get("external_poi_id"):
                item["external_poi_id"] = cls._clean_text(meta.get("id"))
            if not item.get("item_type"):
                item["item_type"] = cls._item_type_from_typecode(
                    meta.get("typecode"), meta.get("type"))
        item["item_type"] = item.get("item_type") or "ATTRACTION"

    @staticmethod
    def _normalize_item_type(value: Any) -> Optional[str]:
        if not isinstance(value, str):
            return None
        normalized = value.strip().upper()
        return normalized if normalized in ITEM_TYPES else None

    @staticmethod
    def _item_type_from_typecode(typecode: Any, type_text: Any) -> str:
        code = (typecode or "").strip() if isinstance(typecode, str) else ""
        if code.startswith("05"):
            return "MEAL"
        if code.startswith("10"):
            return "HOTEL"
        if code.startswith("15"):
            return "TRANSPORT"
        if code.startswith("11"):
            return "ATTRACTION"

        text = type_text if isinstance(type_text, str) else ""
        if "餐饮" in text or "美食" in text:
            return "MEAL"
        if "住宿" in text or "酒店" in text:
            return "HOTEL"
        if "交通" in text:
            return "TRANSPORT"
        return "ATTRACTION"

    @staticmethod
    def _clean_text(value: Any) -> Optional[str]:
        if not isinstance(value, str):
            return None
        stripped = value.strip()
        return stripped or None

    @staticmethod
    def _safe_int(value: Any, default: int) -> int:
        try:
            return int(float(value))
        except (TypeError, ValueError):
            return default
