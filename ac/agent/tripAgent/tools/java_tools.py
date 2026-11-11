"""Java 业务后端确定性调用。

保存行程、创建打车订单等有副作用的操作不暴露为 LLM 工具，
统一由 TripManageNode / TaxiNode 在用户显式命令后调用本模块的确定性函数。
"""

import hashlib
import json
import uuid
from typing import Any, Dict, Optional

from clients import BackendAuthError, backend_client


def build_save_idempotency_key(
        session_id: str,
        plan_request_id: Optional[str],
        plan: Dict[str, Any],
        start_date: str,
        end_date: str,
) -> str:
    """同一份计划 + 同一日期 + 同一次生成请求 => 同一个幂等键。"""
    plan_json = json.dumps(plan, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    source = (
        f"{session_id or ''}|save_trip|{plan_request_id or ''}|"
        f"{start_date}|{end_date}|{plan_json}"
    )
    return hashlib.sha256(source.encode("utf-8")).hexdigest()[:32]


async def save_trip_business(
        plan: Dict[str, Any],
        start_date: str,
        end_date: str,
        title: str = "",
        description: str = "",
        user_token: str = "",
        user_id: Optional[int] = None,
        request_id: str = "",
        session_id: str = "",
        plan_request_id: Optional[str] = None,
) -> Dict[str, Any]:
    """保存行程的确定性业务入口，供 TripManageNode 使用。"""
    if not user_token or user_id is None:
        raise BackendAuthError("请先登录后再保存行程")

    payload = {
        "title": title or "",
        "start_date": start_date,
        "end_date": end_date,
        "description": description or "由 TripAgent 生成并保存",
        "plan": plan,
    }
    idempotency_key = build_save_idempotency_key(
        session_id, plan_request_id, plan, start_date, end_date)
    data = await backend_client.save_trip(
        user_token, user_id, request_id or uuid.uuid4().hex, idempotency_key, payload)
    return data or {}


def build_taxi_idempotency_key(
        session_id: str,
        taxi_request_id: Optional[str],
        payload: Dict[str, Any],
) -> str:
    payload_json = json.dumps(payload, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    source = f"{session_id or ''}|create_taxi_order|{taxi_request_id or ''}|{payload_json}"
    return hashlib.sha256(source.encode("utf-8")).hexdigest()[:32]


async def create_taxi_order_business(
        payload: Dict[str, Any],
        user_token: str = "",
        user_id: Optional[int] = None,
        request_id: str = "",
        session_id: str = "",
        taxi_request_id: Optional[str] = None,
) -> Dict[str, Any]:
    """打车下单的确定性业务入口，供 TaxiNode 使用。"""
    if not user_token or user_id is None:
        raise BackendAuthError("请先登录后再打车")
    idempotency_key = build_taxi_idempotency_key(
        session_id, taxi_request_id, payload)
    data = await backend_client.create_taxi_order(
        user_token, user_id, request_id or uuid.uuid4().hex, idempotency_key, payload)
    return data or {}
