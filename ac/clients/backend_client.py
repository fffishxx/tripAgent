"""Java 业务后端统一 HTTP 客户端。

所有 Agent 业务工具通过本客户端调用 Java；工具本身不再拼 HTTP 请求。
用户 Token 只从 RunnableConfig 传入，不能作为 LLM 工具参数。
"""

import asyncio
import time
from typing import Any, Dict, Optional

import httpx

from config import config_manager, logger


class BackendError(RuntimeError):
    """Java 业务接口异常基类。"""

    def __init__(self, message: str, status_code: Optional[int] = None, code: Optional[int] = None):
        super().__init__(message)
        self.status_code = status_code
        self.code = code


class BackendAuthError(BackendError):
    """用户身份无效/过期。"""


class BackendForbiddenError(BackendError):
    """服务身份或权限不足。"""


class BackendNotFoundError(BackendError):
    """资源不存在或无权访问。"""


class BackendConflictError(BackendError):
    """版本冲突、状态冲突等。"""


class BackendValidationError(BackendError):
    """业务参数校验失败。"""


class BackendUnavailableError(BackendError):
    """网络异常、超时或 Java 5xx。"""


class BackendClient:
    """封装 Java Agent API 的异步 HTTP 客户端。"""

    def __init__(
            self,
            base_url: Optional[str] = None,
            service_token: Optional[str] = None,
            agent_id: Optional[int] = None,
            connect_timeout: Optional[float] = None,
            read_timeout: Optional[float] = None,
            max_retries: Optional[int] = None,
    ):
        self.base_url = (
            base_url if base_url is not None
            else (config_manager.get("java.backend.base-url", "http://127.0.0.1:8080") or "")
        ).rstrip("/")
        self.service_token = (
            service_token if service_token is not None
            else (config_manager.get("java.backend.service-token", "") or "")
        )
        self.agent_id = int(
            agent_id if agent_id is not None
            else (config_manager.get("java.backend.agent-id", 1004) or 1004)
        )
        self.connect_timeout = float(
            connect_timeout if connect_timeout is not None
            else (config_manager.get("java.backend.connect-timeout", 3) or 3)
        )
        self.read_timeout = float(
            read_timeout if read_timeout is not None
            else (config_manager.get("java.backend.read-timeout", 10) or 10)
        )
        self.max_retries = int(
            max_retries if max_retries is not None
            else (config_manager.get("java.backend.max-retries", 2) or 2)
        )
        self._client: Optional[httpx.AsyncClient] = None

    # ------------------ 生命周期 ------------------

    async def start(self):
        if self._client is None or self._client.is_closed:
            timeout = httpx.Timeout(self.read_timeout, connect=self.connect_timeout)
            self._client = httpx.AsyncClient(base_url=self.base_url, timeout=timeout)
        if not self.base_url:
            logger.warning("Java backend base-url 未配置，Agent 业务工具将不可用")
        if not self.service_token:
            logger.warning("Java backend service-token 未配置，Agent API 将被拒绝")

    async def close(self):
        if self._client is not None and not self._client.is_closed:
            await self._client.aclose()
        self._client = None

    # ------------------ 通用请求 ------------------

    async def _request(
            self,
            method: str,
            path: str,
            user_token: str,
            user_id: int,
            request_id: str,
            json_body: Optional[Dict[str, Any]] = None,
            params: Optional[Dict[str, Any]] = None,
            idempotency_key: Optional[str] = None,
            retry: bool = True,
    ) -> Any:
        if not user_token or user_id is None:
            raise BackendAuthError("请先登录后再使用该功能")
        if not self.base_url:
            raise BackendUnavailableError("Java 业务服务地址未配置")

        await self.start()
        headers = {
            "X-User-Token": user_token,
            "X-User-Id": str(user_id),
            "X-Service-Token": self.service_token,
            "X-Request-Id": request_id,
            "X-Agent-Id": str(self.agent_id),
            "Content-Type": "application/json",
        }
        if idempotency_key:
            headers["Idempotency-Key"] = idempotency_key

        attempts = self.max_retries + 1 if retry else 1
        last_error: Optional[Exception] = None

        for attempt in range(attempts):
            started = time.monotonic()
            try:
                response = await self._client.request(
                    method,
                    path,
                    headers=headers,
                    json=json_body,
                    params=params,
                )
                elapsed_ms = int((time.monotonic() - started) * 1000)
                logger.info(
                    "Java backend %s %s -> %s (%sms) request_id=%s",
                    method, path, response.status_code, elapsed_ms, request_id,
                )

                if response.status_code >= 500 and attempt < attempts - 1:
                    await asyncio.sleep(0.5 * (attempt + 1))
                    continue

                return self._parse_response(response)
            except (httpx.TimeoutException, httpx.TransportError) as e:
                last_error = e
                logger.warning(
                    "Java backend %s %s 网络异常: %s request_id=%s",
                    method, path, type(e).__name__, request_id,
                )
                if attempt < attempts - 1:
                    await asyncio.sleep(0.5 * (attempt + 1))
                    continue
                raise BackendUnavailableError("业务服务暂时不可用，请稍后重试") from e

        raise BackendUnavailableError("业务服务暂时不可用，请稍后重试") from last_error

    @staticmethod
    def _parse_response(response: httpx.Response) -> Any:
        try:
            payload = response.json()
        except Exception as exc:
            raise BackendError(
                f"业务服务返回格式异常（HTTP {response.status_code}）",
                response.status_code,
            ) from exc

        if not isinstance(payload, dict):
            raise BackendError(
                f"业务服务返回格式异常（HTTP {response.status_code}）",
                response.status_code,
            )

        code = payload.get("code")
        message = payload.get("message") or f"业务服务返回 HTTP {response.status_code}"

        if response.status_code == 401:
            raise BackendAuthError(message, response.status_code, code)
        if response.status_code == 403:
            raise BackendForbiddenError(message, response.status_code, code)
        if response.status_code == 404:
            raise BackendNotFoundError(message, response.status_code, code)
        if response.status_code == 409:
            raise BackendConflictError(message, response.status_code, code)
        if response.status_code == 400:
            raise BackendValidationError(message, response.status_code, code)
        if response.status_code >= 500:
            raise BackendUnavailableError(message, response.status_code, code)
        if code not in (0, None):
            raise BackendError(message, response.status_code, code)

        return payload.get("data")

    # ------------------ 偏好 ------------------

    async def get_preference(self, user_token: str, user_id: int, request_id: str) -> Any:
        return await self._request(
            "GET", "/api/agent/v1/preferences", user_token, user_id, request_id,
        )

    # ------------------ 行程 ------------------

    async def save_trip(
            self,
            user_token: str,
            user_id: int,
            request_id: str,
            idempotency_key: str,
            payload: Dict[str, Any],
    ) -> Any:
        return await self._request(
            "POST",
            "/api/agent/v1/trips",
            user_token,
            user_id,
            request_id,
            json_body=payload,
            idempotency_key=idempotency_key,
        )

    async def list_trips(
            self,
            user_token: str,
            user_id: int,
            request_id: str,
            keyword: Optional[str] = None,
            limit: int = 10,
    ) -> Any:
        params = {"limit": limit}
        if keyword:
            params["keyword"] = keyword
        return await self._request(
            "GET", "/api/agent/v1/trips", user_token, user_id, request_id, params=params,
        )

    async def get_trip(self, user_token: str, user_id: int, request_id: str, trip_id: str) -> Any:
        return await self._request(
            "GET", f"/api/agent/v1/trips/{trip_id}", user_token, user_id, request_id,
        )

    async def update_trip(
            self,
            user_token: str,
            user_id: int,
            request_id: str,
            trip_id: str,
            payload: Dict[str, Any],
    ) -> Any:
        return await self._request(
            "PUT",
            f"/api/agent/v1/trips/{trip_id}",
            user_token,
            user_id,
            request_id,
            json_body=payload,
        )

    async def delete_trip(self, user_token: str, user_id: int, request_id: str, trip_id: str) -> Any:
        return await self._request(
            "DELETE", f"/api/agent/v1/trips/{trip_id}", user_token, user_id, request_id,
        )

    # ------------------ 打车 ------------------

    async def estimate_taxi(
            self,
            user_token: str,
            user_id: int,
            request_id: str,
            payload: Dict[str, Any],
    ) -> Any:
        return await self._request(
            "POST", "/api/agent/v1/taxi/estimates", user_token, user_id, request_id,
            json_body=payload,
        )

    async def create_taxi_order(
            self,
            user_token: str,
            user_id: int,
            request_id: str,
            idempotency_key: str,
            payload: Dict[str, Any],
    ) -> Any:
        return await self._request(
            "POST",
            "/api/agent/v1/taxi/orders",
            user_token,
            user_id,
            request_id,
            json_body=payload,
            idempotency_key=idempotency_key,
        )

    async def list_taxi_orders(
            self,
            user_token: str,
            user_id: int,
            request_id: str,
            status: Optional[str] = None,
            limit: int = 5,
    ) -> Any:
        params = {"limit": limit}
        if status:
            params["status"] = status
        return await self._request(
            "GET", "/api/agent/v1/taxi/orders", user_token, user_id, request_id, params=params,
        )

    async def get_taxi_order(self, user_token: str, user_id: int, request_id: str, order_id: str) -> Any:
        return await self._request(
            "GET", f"/api/agent/v1/taxi/orders/{order_id}", user_token, user_id, request_id,
        )

    async def cancel_taxi_order(
            self,
            user_token: str,
            user_id: int,
            request_id: str,
            order_id: str,
            reason: Optional[str] = None,
    ) -> Any:
        return await self._request(
            "POST",
            f"/api/agent/v1/taxi/orders/{order_id}/cancel",
            user_token,
            user_id,
            request_id,
            json_body={"reason": reason} if reason else None,
        )


# 全局客户端；由 TripAgent 在 init/destroy 中启动/关闭
backend_client = BackendClient()
