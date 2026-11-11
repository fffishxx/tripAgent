import re
import time

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import Response

from config import config_manager, logger
from util import JWTUtil

# ========================= 不需要服务 Token 的接口 =========================
no_check_list = [
    "/health",          # 容器/探活接口无需验证
]

AUTH_TOKEN_PATH = "/auth/token"
AUTH_TOKEN_WINDOW_SECONDS = 60
AUTH_TOKEN_MAX_REQUESTS = 30

USER_ID_PATTERN = re.compile(r"^[1-9][0-9]{0,18}$")


class RequestFilter(BaseHTTPMiddleware):
    """服务身份 + 可信用户身份中间件。

    新协议：
    - Authorization: Bearer SERVICE_TOKEN   Gateway 应用 JWT
    - X-User-Id / X-User-Token              Java 写入的可信用户身份

    Agent 只验证服务 Token；用户 JWT 不在 Agent 解析。
    """

    def __init__(self, app):
        self.public_key = config_manager.get("jwt.public_key")
        raw_keys = config_manager.get("security.gateway.allowed-app-keys", [])
        if isinstance(raw_keys, str):
            raw_keys = raw_keys.split(",")
        self.allowed_app_keys = {
            str(key).strip() for key in (raw_keys or []) if str(key).strip()
        }

        raw_ips = config_manager.get("security.gateway.auth-token-allowed-ips", [])
        if isinstance(raw_ips, str):
            raw_ips = raw_ips.split(",")
        self.auth_token_allowed_ips = {
            str(ip).strip() for ip in (raw_ips or []) if str(ip).strip()
        }
        self._auth_token_hits: dict[str, list[float]] = {}

        super().__init__(app)

    @staticmethod
    def _extract_bearer(authorization: str | None):
        if not authorization or not authorization.strip():
            return None, False
        parts = authorization.strip().split(None, 1)
        if len(parts) != 2 or parts[0].lower() != "bearer" or not parts[1].strip():
            return None, True
        return parts[1].strip(), False

    def verify_service_token(self, token: str):
        """校验 Gateway 应用 JWT，返回 payload；非法返回 None。"""
        try:
            payload = JWTUtil.verify_token(token, self.public_key)
        except Exception as exc:
            logger.error("服务 Token 校验异常：%s", exc)
            return None
        if not isinstance(payload, dict):
            return None
        if self.allowed_app_keys and payload.get("app_key") not in self.allowed_app_keys:
            logger.warning("服务 Token 的 app_key 不在白名单：%s", payload.get("app_key"))
            return None
        return payload

    async def dispatch(self, request: Request, call_next):
        path = request.url.path
        logger.debug("【RequestFilter】接收到请求路径，path = %s", path)

        if path == AUTH_TOKEN_PATH:
            return await self._handle_auth_token(request, call_next)

        if path in no_check_list:
            return await call_next(request)

        # ========================= 校验请求来源 =========================
        if request.headers.get("request-from") != "agent-center-gateway":
            return Response(status_code=401, content="请求只能通过网关转发，不能直接访问！")

        # ========================= 校验服务 Token =========================
        authorization = request.headers.get("authorization")
        token, malformed = self._extract_bearer(authorization)
        if malformed:
            return Response(status_code=401, content="服务凭证格式不正确")
        if not token:
            return Response(status_code=401, content="缺少服务凭证")
        payload = self.verify_service_token(token)
        if payload is None:
            return Response(status_code=401, content="服务凭证无效或已过期")

        # ========================= 可信用户身份 =========================
        user_id_header = (request.headers.get("x-user-id") or "").strip()
        user_token = (request.headers.get("x-user-token") or "").strip()

        if bool(user_id_header) != bool(user_token):
            return Response(status_code=401, content="用户身份不完整")

        user_id = None
        if user_id_header:
            if not USER_ID_PATTERN.fullmatch(user_id_header):
                return Response(status_code=400, content="X-User-Id 格式不正确")
            user_id = int(user_id_header)

        request.state.user_id = user_id
        request.state.user_token = user_token or ""
        request.state.service_app_key = payload.get("app_key")
        request.state.service_authenticated = True

        return await call_next(request)

    async def _handle_auth_token(self, request: Request, call_next):
        """保护 /auth/token：可选 IP 白名单 + 简单进程内限流。"""
        client_ip = request.client.host if request.client else ""
        if self.auth_token_allowed_ips and client_ip not in self.auth_token_allowed_ips:
            logger.warning("拒绝 /auth/token：客户端 IP 不在白名单 ip=%s", client_ip)
            return Response(status_code=403, content="来源地址不允许申请服务凭证")

        now = time.monotonic()
        recent = [
            hit for hit in self._auth_token_hits.get(client_ip, [])
            if now - hit < AUTH_TOKEN_WINDOW_SECONDS
        ]
        if len(recent) >= AUTH_TOKEN_MAX_REQUESTS:
            logger.warning("拒绝 /auth/token：请求过于频繁 ip=%s", client_ip)
            return Response(status_code=429, content="请求过于频繁，请稍后重试")
        recent.append(now)
        self._auth_token_hits[client_ip] = recent
        return await call_next(request)
