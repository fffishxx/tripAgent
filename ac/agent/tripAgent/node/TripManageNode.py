"""行程与打车订单管理节点。

阶段五支持：
- 保存当前行程
- 查询已保存行程
- 更新已保存行程（基于当前 plan，自动获取最新 version）
- 删除已保存行程
- 查询/取消打车订单
"""

import re
import uuid
from datetime import date, timedelta
from typing import Any, Dict, List, Optional, Tuple

from langchain_core.messages import AIMessage, HumanMessage
from langchain_core.runnables import RunnableConfig

from agent.tripAgent.TripState import TripState
from agent.tripAgent.node.BaseTripNode import BaseTripNode
from agent.tripAgent.tools.java_tools import save_trip_business
from agent.tripAgent.trip_manage_utils import detect_manage_action
from agent.tripAgent.utils import content_to_text
from clients import (
    BackendAuthError,
    BackendConflictError,
    BackendError,
    BackendForbiddenError,
    BackendNotFoundError,
    BackendUnavailableError,
    BackendValidationError,
    backend_client,
)
from config import logger

DATE_PATTERN = re.compile(r"(20\d{2})[-/年](\d{1,2})[-/月](\d{1,2})日?")
_UNSET = object()


class TripManageNode(BaseTripNode):
    """行程/打车订单的自然语言管理入口。"""

    async def execute(self, state: TripState, config: RunnableConfig) -> Dict[str, Any]:
        text = self._last_user_text(state)
        action = detect_manage_action(
            text,
            has_plan=bool(state.get("plan")),
            has_saved_trip=bool(state.get("saved_trip_id")),
        )
        if not action:
            return self._result(
                "请告诉我要保存、查询、更新还是删除行程，"
                "或者查询/取消打车订单。"
            )

        configurable = (config or {}).get("configurable", {}) or {}
        user_token = (configurable.get("user_token") or "").strip()
        user_id = configurable.get("user_id")
        request_id = configurable.get("request_id") or uuid.uuid4().hex
        session_id = configurable.get("thread_id") or ""

        if not user_token or user_id is None:
            return self._result(
                "该操作需要先登录，请登录后再试。",
                auth_required=True,
            )

        ctx = {
            "user_token": user_token,
            "user_id": user_id,
            "request_id": request_id,
            "session_id": session_id,
        }

        try:
            if action == "SAVE_TRIP":
                return await self._save_trip(state, ctx)
            if action == "QUERY_TRIP":
                return await self._query_trips(ctx)
            if action == "UPDATE_TRIP":
                return await self._update_trip(state, ctx)
            if action == "DELETE_TRIP":
                return await self._delete_trip(state, ctx)
            if action == "QUERY_TAXI":
                return await self._query_taxi_orders(ctx)
            if action == "CANCEL_TAXI":
                return await self._cancel_taxi_order(state, ctx)
            return self._result("暂不支持该操作。")
        except BackendAuthError as exc:
            return self._result(str(exc), auth_required=True)
        except BackendValidationError as exc:
            return self._result(f"操作失败：{exc}")
        except BackendConflictError as exc:
            return self._result(f"操作失败：{exc}")
        except BackendForbiddenError:
            return self._result("操作失败：业务服务未授权，请联系管理员。")
        except BackendNotFoundError as exc:
            return self._result(f"操作失败：{exc}")
        except BackendUnavailableError:
            return self._result("业务服务暂时不可用，请稍后重试。")
        except BackendError as exc:
            logger.warning("行程/订单管理失败：%s", exc)
            return self._result(f"操作失败：{exc}")
        except Exception:
            logger.exception("行程/订单管理异常")
            return self._result("操作失败，请稍后重试。")

    # ------------------ 行程 ------------------

    async def _save_trip(self, state: TripState, ctx: Dict[str, str]) -> Dict[str, Any]:
        plan = state.get("plan")
        if not plan:
            return self._result("请先让我为你生成一份行程，然后再保存。")

        days_count = len(plan.get("days") or [])
        dates = self._extract_dates(state, days_count)
        if dates is None:
            return self._result(
                "请告诉我出行开始和结束日期，例如 2026-11-10 到 2026-11-12，"
                "我再帮你保存。"
            )

        start_date, end_date = dates
        destination = (plan.get("destination") or "").strip()
        title = f"{destination}{days_count}日游" if destination and days_count else "我的行程"

        data = await save_trip_business(
            plan=plan,
            start_date=start_date.isoformat(),
            end_date=end_date.isoformat(),
            title=title,
            description="由 TripAgent 生成并保存",
            user_token=ctx["user_token"],
            user_id=ctx["user_id"],
            request_id=ctx["request_id"],
            session_id=ctx["session_id"],
            plan_request_id=state.get("plan_request_id"),
        )

        trip_id = data.get("id")
        replay = bool(data.get("idempotent_replay"))
        version = data.get("version")
        if replay:
            text = f"这份行程已经保存过，行程 ID：{trip_id}，本次未重复创建。"
        else:
            text = f"已保存行程「{title}」，行程 ID：{trip_id}。"
        logger.info("行程保存成功：trip_id=%s, replay=%s", trip_id, replay)
        return self._result(
            text,
            saved_trip_id=trip_id,
            saved_trip_version=version,
        )

    async def _query_trips(self, ctx: Dict[str, str]) -> Dict[str, Any]:
        data = await backend_client.list_trips(
            ctx["user_token"], ctx["user_id"], ctx["request_id"], limit=10)
        trips = data.get("trips") if isinstance(data, dict) else []
        if not trips:
            return self._result("你还没有保存的行程。")
        return self._result(self._format_trip_list(trips))

    async def _update_trip(self, state: TripState, ctx: Dict[str, str]) -> Dict[str, Any]:
        plan = state.get("plan")
        if not plan:
            return self._result("请先让我生成或修改一份行程，然后再更新。")

        trip_id, error = await self._resolve_trip_id(state, ctx)
        if error:
            return self._result(error)

        current = await backend_client.get_trip(
            ctx["user_token"], ctx["user_id"], ctx["request_id"], trip_id)
        if not isinstance(current, dict):
            return self._result("没有找到要更新的行程。")

        days_count = len(plan.get("days") or [])
        dates = self._extract_dates(state, days_count)
        if dates is None:
            dates = self._trip_dates(current)
        if dates is None:
            return self._result(
                "请告诉我更新后的出行开始和结束日期，"
                "例如 2026-11-10 到 2026-11-12。"
            )
        start_date, end_date = dates

        destination = (plan.get("destination") or current.get("destination") or "").strip()
        title = (f"{destination}{days_count}日游"
                 if destination and days_count else current.get("title") or "我的行程")
        payload = {
            "title": title,
            "start_date": start_date.isoformat(),
            "end_date": end_date.isoformat(),
            "description": "由 TripAgent 修改并保存",
            "plan": plan,
            "version": current.get("version"),
        }
        data = await backend_client.update_trip(
            ctx["user_token"], ctx["user_id"], ctx["request_id"], trip_id, payload)
        version = data.get("version") if isinstance(data, dict) else None
        text = f"已更新行程「{title}」，行程 ID：{trip_id}，当前版本：{version}。"
        return self._result(
            text,
            saved_trip_id=str(trip_id),
            saved_trip_version=version,
        )

    async def _delete_trip(self, state: TripState, ctx: Dict[str, str]) -> Dict[str, Any]:
        trip_id, error = await self._resolve_trip_id(state, ctx)
        if error:
            return self._result(error)

        await backend_client.delete_trip(
            ctx["user_token"], ctx["user_id"], ctx["request_id"], trip_id)
        text = f"已删除行程，行程 ID：{trip_id}。"
        return self._result(
            text,
            saved_trip_id=None,
            saved_trip_version=None,
        )

    # ------------------ 打车订单 ------------------

    async def _query_taxi_orders(self, ctx: Dict[str, str]) -> Dict[str, Any]:
        data = await backend_client.list_taxi_orders(
            ctx["user_token"], ctx["user_id"], ctx["request_id"], limit=5)
        orders = data.get("orders") if isinstance(data, dict) else []
        if not orders:
            return self._result("你还没有打车订单。")
        return self._result(self._format_order_list(orders))

    async def _cancel_taxi_order(
            self,
            state: TripState,
            ctx: Dict[str, str],
    ) -> Dict[str, Any]:
        order_id, error = await self._resolve_taxi_order_id(state, ctx)
        if error:
            return self._result(error)

        data = await backend_client.cancel_taxi_order(
            ctx["user_token"], ctx["user_id"], ctx["request_id"], order_id, reason=None)
        status = data.get("status") if isinstance(data, dict) else "CANCELLED"
        order_no = data.get("order_no") if isinstance(data, dict) else ""
        text = f"订单 {order_no or order_id} 已取消，当前状态：{status}。"
        return self._result(text)

    # ------------------ 解析与格式化 ------------------

    async def _resolve_trip_id(
            self,
            state: TripState,
            ctx: Dict[str, str],
    ) -> Tuple[Optional[str], Optional[str]]:
        trip_id = state.get("saved_trip_id")
        if trip_id:
            return str(trip_id), None

        data = await backend_client.list_trips(
            ctx["user_token"], ctx["user_id"], ctx["request_id"], limit=10)
        trips = data.get("trips") if isinstance(data, dict) else []
        if not trips:
            return None, "你还没有保存的行程。"
        if len(trips) == 1:
            return str(trips[0].get("id")), None
        return None, self._format_trip_list(
            trips, "找到多份已保存行程，请先指定要操作的行程：")

    async def _resolve_taxi_order_id(
            self,
            state: TripState,
            ctx: Dict[str, str],
    ) -> Tuple[Optional[str], Optional[str]]:
        taxi_order = state.get("taxi_order") or {}
        order_id = taxi_order.get("java_order_id")
        if order_id:
            return str(order_id), None

        data = await backend_client.list_taxi_orders(
            ctx["user_token"], ctx["user_id"], ctx["request_id"], limit=5)
        orders = data.get("orders") if isinstance(data, dict) else []
        if not orders:
            return None, "你还没有打车订单。"
        if len(orders) == 1:
            return str(orders[0].get("id")), None
        return None, self._format_order_list(
            orders, "找到多份打车订单，请先指定要取消的订单：")

    @staticmethod
    def _format_trip_list(
            trips: List[Dict[str, Any]],
            prefix: str = "你保存的行程：",
    ) -> str:
        lines = [prefix]
        for index, trip in enumerate(trips[:10], 1):
            lines.append(
                f"{index}. {trip.get('title')}"
                f"（{trip.get('start_date')} ~ {trip.get('end_date')}，"
                f"{trip.get('days_count')} 天，ID: {trip.get('id')}）"
            )
        return "\n".join(lines)

    @staticmethod
    def _format_order_list(
            orders: List[Dict[str, Any]],
            prefix: str = "你最近的打车订单：",
    ) -> str:
        lines = [prefix]
        for index, order in enumerate(orders[:5], 1):
            lines.append(
                f"{index}. {order.get('order_no') or order.get('id')} "
                f"{order.get('status_text') or order.get('status')}，"
                f"{order.get('pickup', {}).get('name')} → "
                f"{order.get('destination', {}).get('name')}，"
                f"预估 {order.get('estimated_fare')} 元，ID: {order.get('id')}"
            )
        return "\n".join(lines)

    @staticmethod
    def _trip_dates(trip: Dict[str, Any]) -> Optional[Tuple[date, date]]:
        try:
            return (
                date.fromisoformat(str(trip.get("start_date"))),
                date.fromisoformat(str(trip.get("end_date"))),
            )
        except (TypeError, ValueError):
            return None

    def _extract_dates(
            self,
            state: TripState,
            days_count: int,
    ) -> Optional[Tuple[date, date]]:
        texts = []
        for message in reversed(state.get("messages") or []):
            if isinstance(message, HumanMessage):
                text = content_to_text(message.content)
                if text:
                    texts.append(text)
                if len(texts) >= 3:
                    break

        for text in texts:
            dates = self._parse_dates(text)
            if not dates:
                continue
            if len(dates) >= 2:
                return dates[0], dates[1]
            start = dates[0]
            return start, start + timedelta(days=max(days_count - 1, 0))
        return None

    @staticmethod
    def _parse_dates(text: str):
        dates = []
        for match in DATE_PATTERN.finditer(text or ""):
            try:
                dates.append(date(
                    int(match.group(1)),
                    int(match.group(2)),
                    int(match.group(3)),
                ))
            except ValueError:
                continue
        return dates

    @staticmethod
    def _last_user_text(state: TripState) -> str:
        for message in reversed(state.get("messages") or []):
            if isinstance(message, HumanMessage):
                return content_to_text(message.content)
        return ""

    def _result(
            self,
            text: str,
            auth_required: bool = False,
            saved_trip_id: Any = _UNSET,
            saved_trip_version: Any = _UNSET,
    ) -> Dict[str, Any]:
        metadata: Dict[str, Any] = {}
        if auth_required:
            metadata["auth_required"] = True
        result: Dict[str, Any] = {
            "messages": [AIMessage(content=text, response_metadata=metadata)]
        }
        if saved_trip_id is not _UNSET:
            result["saved_trip_id"] = saved_trip_id
        if saved_trip_version is not _UNSET:
            result["saved_trip_version"] = saved_trip_version
        return result
