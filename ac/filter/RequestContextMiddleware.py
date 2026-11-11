"""请求上下文 ASGI 中间件。

使用纯 ASGI 实现（不包裹 BaseHTTPMiddleware），避免对 SSE 流式响应产生缓冲；
负责 X-Request-Id 的透传/生成、响应头回写、请求耗时日志和 contextvars 生命周期。
"""

import time

from common.RequestContext import (
    REQUEST_ID_HEADER,
    normalize_request_id,
    reset_request_id,
    set_request_id,
)
from config import logger


class RequestContextMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope.get("type") != "http":
            await self.app(scope, receive, send)
            return

        raw_id = None
        for key, value in scope.get("headers") or []:
            if key.lower() == b"x-request-id":
                try:
                    raw_id = value.decode("latin-1")
                except Exception:
                    raw_id = None
                break

        request_id = normalize_request_id(raw_id)
        token = set_request_id(request_id)

        method = scope.get("method", "-")
        path = scope.get("path", "-")
        started = time.monotonic()
        status_holder = {"status": 0}

        async def send_with_request_id(message):
            if message.get("type") == "http.response.start":
                status_holder["status"] = int(message.get("status") or 0)
                headers = list(message.get("headers") or [])
                if not any(key.lower() == b"x-request-id" for key, _ in headers):
                    headers.append((REQUEST_ID_HEADER.encode("latin-1"), request_id.encode("latin-1")))
                    message["headers"] = headers
            await send(message)

        try:
            await self.app(scope, receive, send_with_request_id)
        except Exception:
            logger.exception("HTTP 请求异常: %s %s", method, path)
            raise
        finally:
            elapsed_ms = int((time.monotonic() - started) * 1000)
            status = status_holder["status"] or 500
            logger.info("HTTP %s %s -> %s (%dms)", method, path, status, elapsed_ms)
            reset_request_id(token)
