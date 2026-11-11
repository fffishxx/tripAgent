"""请求上下文（contextvars）。

只依赖标准库，供日志过滤器、ASGI 中间件和 Agent 使用，避免循环导入。
"""

import contextvars
import re
import uuid
from typing import Optional

REQUEST_ID_HEADER = "X-Request-Id"
REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{1,64}$")

_request_id_var: contextvars.ContextVar[Optional[str]] = contextvars.ContextVar(
    "tripagent_request_id", default=None
)

ContextToken = contextvars.Token


def normalize_request_id(value: Optional[str]) -> str:
    """校验并返回 request_id；缺失/非法时生成新的 hex ID。"""
    candidate = (value or "").strip()
    if candidate and REQUEST_ID_PATTERN.fullmatch(candidate):
        return candidate
    return uuid.uuid4().hex


def get_request_id() -> Optional[str]:
    return _request_id_var.get()


def set_request_id(request_id: str) -> ContextToken:
    return _request_id_var.set(request_id)


def reset_request_id(token: ContextToken) -> None:
    _request_id_var.reset(token)
