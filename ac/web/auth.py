"""可信请求身份依赖。

RequestFilter 先验证 Gateway 服务 Token，然后把 X-User-Id / X-User-Token
写入 request.state。路由只能通过本模块读取身份，禁止直接读取原始 Header。
"""

from typing import Optional

from fastapi import HTTPException, Request


def optional_user_id(request: Request) -> Optional[int]:
    return getattr(request.state, "user_id", None)


def get_user_token(request: Request) -> str:
    return getattr(request.state, "user_token", "") or ""


def require_user_id(request: Request) -> int:
    user_id = optional_user_id(request)
    if user_id is None:
        raise HTTPException(status_code=401, detail="请先登录后再使用该功能")
    return user_id
