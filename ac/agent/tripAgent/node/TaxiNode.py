import uuid
from datetime import datetime
from typing import Any, Dict, List

from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableConfig

from agent.tripAgent import prompts
from agent.tripAgent.AmapClient import amap_client
from agent.tripAgent.didi_mcp import DidiMCPError, didi_mcp
from agent.tripAgent.location import resolve_place
from agent.tripAgent.node.BaseTripNode import BaseTripNode
from agent.tripAgent.taxi_utils import find_vehicle, last_user_text
from agent.tripAgent.tools.java_tools import create_taxi_order_business
from agent.tripAgent.TripState import TripState
from clients import (
    BackendAuthError,
    BackendConflictError,
    BackendError,
    BackendValidationError,
    backend_client,
)
from config import config_manager, logger


class TaxiNode(BaseTripNode):
    """
    一键打车（阶段五：Java 模拟打车为主，滴滴 MCP 可选）。

    默认流程：
    1. 解析起点/终点坐标
    2. 调 Java Agent API 查询模拟车型与价格
    3. 用户选择车型后调 Java 创建模拟订单，并把 Java 订单卡片推给前端
    4. 用户之后可通过行程管理节点查询/取消订单

    配置 taxi.use-didi=true 且已配置滴滴 MCP 时，保留原有滴滴估价/跳转链接分支。
    """

    async def execute(self, state: TripState, config: RunnableConfig) -> Dict[str, Any]:
        pickup_name = (state.get("origin") or "").strip()
        destination_name = (state.get("destination") or "").strip()
        city_name = (state.get("city") or "").strip()

        if not pickup_name or not destination_name:
            return self._error(prompts.TAXI_CLARIFY)

        pickup = await resolve_place(pickup_name, city_name)
        destination = await resolve_place(destination_name, city_name)
        if not pickup or not destination:
            return self._error(prompts.TAXI_FAILED.format(
                origin=pickup_name,
                destination=destination_name,
            ))

        use_didi = config_manager.get_bool("taxi.use-didi", False) and didi_mcp.is_configured()
        if use_didi:
            return await self._didi_order(
                state, pickup_name, destination_name, city_name, pickup, destination, config
            )
        return await self._java_order(
            state, pickup_name, destination_name, city_name, pickup, destination, config
        )

    # ------------------ Java 模拟打车（主流程） ------------------

    async def _java_order(
            self,
            state: TripState,
            pickup_name: str,
            destination_name: str,
            city_name: str,
            pickup: Dict[str, Any],
            destination: Dict[str, Any],
            config: RunnableConfig,
    ) -> Dict[str, Any]:
        configurable = (config.get("configurable") or {})
        user_token = (configurable.get("user_token") or "").strip()
        user_id = configurable.get("user_id")
        request_id = configurable.get("request_id") or uuid.uuid4().hex
        session_id = configurable.get("thread_id") or ""

        if not user_token or user_id is None:
            return self._auth_required("打车需要先登录，请登录后再试。")

        pending = self._pending_estimate(state, pickup_name, destination_name)
        selected_text = (state.get("vehicle_type") or "").strip()

        if pending:
            vehicles = pending.get("vehicles") or []
            if not selected_text:
                matched = find_vehicle(last_user_text(state), vehicles)
                if matched:
                    selected_text = matched.get("name") or ""
            selected = find_vehicle(selected_text, vehicles) if selected_text else None
            if selected:
                return await self._create_java_order(
                    pending, selected, city_name, user_token, user_id, request_id, session_id)
            return self._java_selection_card(pending, request_id)

        city = city_name or pickup.get("city") or destination.get("city") or ""
        estimate_payload = {
            "pickup": {
                "name": pickup_name,
                "longitude": pickup["coords"][0],
                "latitude": pickup["coords"][1],
            },
            "destination": {
                "name": destination_name,
                "longitude": destination["coords"][0],
                "latitude": destination["coords"][1],
            },
            "city": city,
            "vehicle_type": selected_text or None,
        }
        try:
            estimate = await backend_client.estimate_taxi(
                user_token, user_id, request_id, estimate_payload)
        except BackendAuthError as exc:
            return self._auth_required(str(exc))
        except BackendError as exc:
            logger.warning("Java 打车估价失败：%s", exc)
            return self._error(f"打车服务暂时不可用：{exc}")

        options = self._map_java_options(estimate.get("vehicle_options") or [])
        if not options:
            return self._error("打车服务未返回可用车型，请稍后重试。")

        pending = {
            "pickup": pickup_name,
            "destination": destination_name,
            "city": city,
            "pickup_coords": pickup["coords"],
            "destination_coords": destination["coords"],
            "vehicles": options,
            "estimate": estimate,
            "operation_id": uuid.uuid4().hex,
        }
        selected = find_vehicle(selected_text, options) if selected_text else None
        if selected:
            return await self._create_java_order(
                pending, selected, city_name, user_token, user_id, request_id, session_id)
        return self._java_selection_card(pending, request_id)

    async def _create_java_order(
            self,
            pending: Dict[str, Any],
            selected: Dict[str, Any],
            city_name: str,
            user_token: str,
            user_id: int,
            request_id: str,
            session_id: str,
    ) -> Dict[str, Any]:
        pickup_coords = pending.get("pickup_coords") or []
        destination_coords = pending.get("destination_coords") or []
        payload = {
            "pickup": {
                "name": pending.get("pickup"),
                "longitude": pickup_coords[0],
                "latitude": pickup_coords[1],
            },
            "destination": {
                "name": pending.get("destination"),
                "longitude": destination_coords[0],
                "latitude": destination_coords[1],
            },
            "city": pending.get("city") or city_name or "",
            "vehicle_type": selected.get("name"),
            "provider": "MOCK",
        }
        try:
            data = await create_taxi_order_business(
                payload=payload,
                user_token=user_token,
                user_id=user_id,
                request_id=request_id,
                session_id=session_id,
                taxi_request_id=pending.get("operation_id"),
            )
        except BackendAuthError as exc:
            return self._auth_required(str(exc))
        except BackendConflictError as exc:
            return self._error(f"创建订单失败：{exc}")
        except BackendValidationError as exc:
            return self._error(f"创建订单失败：{exc}")
        except BackendError as exc:
            logger.warning("Java 创建打车订单失败：%s", exc)
            return self._error(f"打车服务暂时不可用：{exc}")

        order = self._java_order_card(data, pending, selected, request_id)
        fare = order.get("estimated_fare")
        fare_text = f"预估车费 {float(fare):.2f} 元" if fare is not None else "车费以实际为准"
        text = (
            f"已为你呼叫车辆：{pending.get('pickup')} → {pending.get('destination')}，"
            f"全程约 {order.get('distance_km')} 公里，预计 {order.get('duration_min')} 分钟，"
            f"{fare_text}。订单号 {order.get('order_id')}。"
        )
        message = AIMessage(
            content=text,
            response_metadata={"taxi_order": order, "request_id": request_id},
        )
        logger.info("Java 打车订单创建：%s %s -> %s",
                    order.get("order_id"), pending.get("pickup"), pending.get("destination"))
        return {
            "taxi_order": order,
            "taxi_request_id": request_id,
            "taxi_estimate": None,
            "vehicle_type": None,
            "messages": [message],
        }

    def _java_selection_card(
            self,
            pending: Dict[str, Any],
            request_id: str,
    ) -> Dict[str, Any]:
        options = pending.get("vehicles") or []
        estimate = pending.get("estimate") or {}
        order = {
            "order_id": self._order_id(),
            "provider": "mock",
            "provider_name": "Java 模拟打车",
            "status": "SELECT_VEHICLE",
            "status_text": "请选择车型",
            "pickup": pending.get("pickup"),
            "destination": pending.get("destination"),
            "city": pending.get("city") or "",
            "vehicle_options": options,
            "distance_km": round((estimate.get("distance_m") or 0) / 1000, 1),
            "duration_min": estimate.get("duration_min"),
            "currency": "CNY",
            "simulated": True,
            "note": "请回复车型名称，随后创建 Java 模拟订单",
            "request_id": request_id,
            "created_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }
        lines = [
            f"{index}. {self._vehicle_text(vehicle)}"
            for index, vehicle in enumerate(options, 1)
        ]
        text = (
            f"打车预估（{pending.get('pickup')} → {pending.get('destination')}）：\n"
            + "\n".join(lines)
            + "\n请回复你想乘坐的车型名称（例如“快车”），我再为你创建 Java 模拟订单。"
        )
        message = AIMessage(
            content=text,
            response_metadata={"taxi_order": order, "request_id": request_id},
        )
        return {
            "taxi_order": order,
            "taxi_request_id": request_id,
            "taxi_estimate": pending,
            "messages": [message],
        }

    @staticmethod
    def _map_java_options(options: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        result = []
        for option in options or []:
            name = (option.get("vehicle_type") or "").strip()
            if not name:
                continue
            result.append({
                "name": name,
                "price": option.get("estimated_fare"),
                "coupon_price": None,
                "category": None,
            })
        return result

    @staticmethod
    def _java_order_card(
            data: Dict[str, Any],
            pending: Dict[str, Any],
            selected: Dict[str, Any],
            request_id: str,
    ) -> Dict[str, Any]:
        order_id = data.get("order_no") or data.get("id")
        return {
            "order_id": order_id,
            "java_order_id": data.get("id"),
            "provider": "mock",
            "provider_name": "Java 模拟打车",
            "status": data.get("status") or "CREATED",
            "status_text": data.get("status_text") or "已创建",
            "pickup": pending.get("pickup"),
            "destination": pending.get("destination"),
            "city": data.get("city") or pending.get("city") or "",
            "vehicle_type": data.get("vehicle_type") or selected.get("name"),
            "vehicle_options": pending.get("vehicles") or [],
            "distance_km": round((data.get("distance_m") or 0) / 1000, 1),
            "duration_min": data.get("duration_min"),
            "estimated_fare": data.get("estimated_fare"),
            "coupon_fare": data.get("coupon_fare"),
            "currency": data.get("currency") or "CNY",
            "simulated": True,
            "note": "Java 模拟订单，未接入真实打车平台",
            "request_id": request_id,
            "created_at": data.get("create_time") or datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }

    @staticmethod
    def _auth_required(message: str) -> Dict[str, Any]:
        return {"messages": [AIMessage(
            content=message,
            response_metadata={"auth_required": True},
        )]}

    # ------------------ 滴滴 MCP 基础版 ------------------

    async def _didi_order(
            self,
            state: TripState,
            pickup_name: str,
            destination_name: str,
            city_name: str,
            pickup: Dict[str, Any],
            destination: Dict[str, Any],
            config: RunnableConfig,
    ) -> Dict[str, Any]:
        request_id = (config.get("configurable") or {}).get("request_id")
        pending = self._pending_estimate(state, pickup_name, destination_name)
        selected_text = (state.get("vehicle_type") or "").strip()

        if not selected_text and pending:
            matched = find_vehicle(last_user_text(state), pending.get("vehicles") or [])
            if matched:
                selected_text = matched.get("name") or ""

        if pending:
            vehicles = pending.get("vehicles") or []
            trace_id = pending.get("trace_id")
            pickup_coords = pending.get("pickup_coords") or pickup["coords"]
            destination_coords = pending.get("destination_coords") or destination["coords"]
            if not vehicles:
                pending = None

        if not pending:
            try:
                estimate = await didi_mcp.estimate(pickup, destination)
            except DidiMCPError as e:
                logger.warning("滴滴 MCP 估价失败：%s", e)
                return self._error(f"滴滴打车服务暂时不可用：{e}")

            vehicles = estimate.get("vehicles") or []
            trace_id = estimate.get("trace_id")
            pickup_coords = pickup["coords"]
            destination_coords = destination["coords"]

            if not vehicles:
                raw = (estimate.get("text") or "").strip()
                if raw:
                    logger.warning("滴滴 MCP 未解析出车型，原始返回：%s", raw[:500])
                return self._error("滴滴暂未返回可用车型，请稍后重试。")

        selected = find_vehicle(selected_text, vehicles) if selected_text else None
        if not selected:
            # 未指定车型或选择不匹配：展示车型选择卡，等待用户选择
            return self._selection_card(
                pickup_name, destination_name, city_name, pickup, destination,
                vehicles, trace_id, request_id,
            )

        try:
            link = await didi_mcp.generate_link(
                {"name": pickup_name, "coords": pickup_coords},
                {"name": destination_name, "coords": destination_coords},
                category=selected.get("category"),
            )
        except DidiMCPError as e:
            logger.warning("滴滴 MCP 生成打车链接失败：%s", e)
            return self._error(f"滴滴打车服务暂时不可用：{e}")

        jump_url = link.get("url")
        if not jump_url:
            return self._error("滴滴未返回打车跳转链接，请稍后重试。")

        options = self._vehicle_options(vehicles)
        order = {
            "order_id": self._order_id(),
            "provider": "didi_mcp",
            "provider_name": "滴滴出行",
            "status": "PENDING_JUMP",
            "status_text": "请在滴滴 App/小程序中完成叫车",
            "pickup": pickup_name,
            "destination": destination_name,
            "city": city_name or pickup.get("city") or destination.get("city") or "",
            "vehicle_type": selected.get("name"),
            "vehicle_options": options,
            "estimated_fare": self._vehicle_price(selected),
            "coupon_fare": selected.get("coupon_price"),
            "currency": "CNY",
            "jump_url": jump_url,
            "jump_links": link.get("links") or {},
            "estimate_trace_id": trace_id,
            "simulated": False,
            "note": "通过滴滴 MCP 生成跳转链接，实际下单在滴滴 App/小程序完成",
            "request_id": request_id,
            "created_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }
        logger.info("生成滴滴打车链接：%s %s -> %s | %s", order["order_id"],
                    pickup_name, destination_name, selected.get("name"))

        vehicle_text = self._vehicle_text(selected)
        text = (
            f"已为你生成滴滴叫车链接：{pickup_name} → {destination_name}，"
            f"{vehicle_text}。\n"
            f"请点击订单卡片中的链接前往滴滴 App/小程序完成叫车。"
        )
        message = AIMessage(
            content=text,
            response_metadata={"taxi_order": order, "request_id": request_id},
        )

        return {
            "taxi_order": order,
            "taxi_request_id": request_id,
            "taxi_estimate": None,
            "vehicle_type": None,
            "messages": [message],
        }

    def _selection_card(
            self,
            pickup_name: str,
            destination_name: str,
            city_name: str,
            pickup: Dict[str, Any],
            destination: Dict[str, Any],
            vehicles: List[Dict[str, Any]],
            trace_id: Any,
            request_id: Any,
    ) -> Dict[str, Any]:
        """返回车型选择卡，等待用户回复车型名称"""
        options = self._vehicle_options(vehicles)
        order = {
            "order_id": self._order_id(),
            "provider": "didi_mcp",
            "provider_name": "滴滴出行",
            "status": "SELECT_VEHICLE",
            "status_text": "请选择车型",
            "pickup": pickup_name,
            "destination": destination_name,
            "city": city_name or pickup.get("city") or destination.get("city") or "",
            "vehicle_options": options,
            "estimate_trace_id": trace_id,
            "currency": "CNY",
            "simulated": False,
            "note": "请回复车型名称，随后生成滴滴叫车链接",
            "request_id": request_id,
            "created_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }

        lines = [
            f"{index}. {self._vehicle_text(vehicle)}"
            for index, vehicle in enumerate(vehicles, 1)
        ]
        text = (
            f"滴滴打车预估（{pickup_name} → {destination_name}）：\n"
            + "\n".join(lines)
            + "\n请回复你想乘坐的车型名称（例如“特惠快车”），我再为你生成滴滴叫车链接。"
        )
        message = AIMessage(
            content=text,
            response_metadata={"taxi_order": order, "request_id": request_id},
        )

        pending_state = {
            "pickup": pickup_name,
            "destination": destination_name,
            "city": order["city"],
            "pickup_coords": pickup["coords"],
            "destination_coords": destination["coords"],
            "vehicles": options,
            "trace_id": trace_id,
        }
        return {
            "taxi_order": order,
            "taxi_request_id": request_id,
            "taxi_estimate": pending_state,
            "messages": [message],
        }

    @staticmethod
    def _pending_estimate(
            state: TripState,
            pickup_name: str,
            destination_name: str,
    ) -> Dict[str, Any] | None:
        """只有起终点一致时，才复用上一轮车型预估"""
        pending = state.get("taxi_estimate")
        if not pending:
            return None
        if (pending.get("pickup") or "").strip() != pickup_name:
            return None
        if (pending.get("destination") or "").strip() != destination_name:
            return None
        return pending

    @staticmethod
    def _vehicle_options(vehicles: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        return [
            {
                "name": vehicle.get("name") or "",
                "price": vehicle.get("price_value") if vehicle.get("price_value") is not None
                else vehicle.get("price"),
                "coupon_price": vehicle.get("coupon_price"),
                "category": str(vehicle.get("category")) if vehicle.get("category") is not None else None,
            }
            for vehicle in vehicles
        ]

    @staticmethod
    def _vehicle_price(vehicle: Dict[str, Any]) -> Any:
        price = vehicle.get("price_value")
        return price if price is not None else vehicle.get("price")

    @classmethod
    def _vehicle_text(cls, vehicle: Dict[str, Any]) -> str:
        name = vehicle.get("name") or "车型"
        price = cls._vehicle_price(vehicle)
        coupon = vehicle.get("coupon_price")
        text = f"{name} 约 {price:.0f} 元" if isinstance(price, (int, float)) else name
        if coupon is not None:
            text += f"（券后约 {coupon:.0f} 元）"
        return text

    @staticmethod
    def _order_id() -> str:
        return "TX" + uuid.uuid4().hex[:12].upper()

    @staticmethod
    def _error(message: str) -> Dict[str, Any]:
        return {"messages": [AIMessage(content=message)]}
