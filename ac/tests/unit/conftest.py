"""Python 离线单元测试环境。

真实 config 包在导入时会创建 NacosClient 并请求 access token，
因此必须在任何应用模块导入前注入最小 config / clients 桩。
"""

import importlib.util
import logging
import sys
import types
from pathlib import Path
from typing import Any, Optional

import pytest

AC_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(AC_ROOT))

_ORIGINAL_MODULES = {name: sys.modules.get(name) for name in ("config", "clients")}


class FakeConfigManager:
    def __init__(self):
        self.values: dict[str, Any] = {}

    def get(self, key_path: str, default=None):
        return self.values.get(key_path, default)

    def get_bool(self, key_path: str, default: bool = False) -> bool:
        value = self.get(key_path, default)
        if isinstance(value, bool):
            return value
        normalized = str(value).strip().lower()
        if normalized in ("true", "1", "yes", "on"):
            return True
        if normalized in ("false", "0", "no", "off"):
            return False
        raise ValueError(f"配置 {key_path} 不是合法布尔值: {value!r}")

    def get_int(self, key_path: str, default=None):
        value = self.get(key_path, default)
        return None if value is None else int(value)

    def get_float(self, key_path: str, default=None):
        value = self.get(key_path, default)
        return None if value is None else float(value)


class FakeBackendError(RuntimeError):
    pass


class FakeBackendAuthError(FakeBackendError):
    pass


class FakeBackendForbiddenError(FakeBackendError):
    pass


class FakeBackendNotFoundError(FakeBackendError):
    pass


class FakeBackendConflictError(FakeBackendError):
    pass


class FakeBackendValidationError(FakeBackendError):
    pass


class FakeBackendUnavailableError(FakeBackendError):
    pass


class FakeBackendClient:
    def __init__(self):
        self.reset()

    def reset(self):
        self.calls: list[tuple] = []
        self.error: Optional[Exception] = None
        self.preference: dict = {"crowd": "family", "budget_level": "comfort"}
        self.trip: dict = {"id": "9001", "version": 0, "title": "测试行程"}
        self.result: dict = {"trips": [], "total": 0}
        self.order: dict = {"id": "7001", "status": "CREATED"}

    def _raise_if_error(self):
        if self.error is not None:
            raise self.error

    async def get_preference(self, user_token, user_id, request_id):
        self._raise_if_error()
        self.calls.append(("get_preference", user_token, user_id, request_id))
        return self.preference

    async def save_trip(self, user_token, user_id, request_id, idempotency_key, payload):
        self._raise_if_error()
        self.calls.append(("save_trip", user_token, user_id, request_id, idempotency_key, payload))
        return self.trip

    async def get_trip(self, user_token, user_id, request_id, trip_id):
        self._raise_if_error()
        self.calls.append(("get_trip", user_token, user_id, request_id, trip_id))
        return self.trip

    async def list_trips(self, user_token, user_id, request_id, keyword=None, limit=10):
        self._raise_if_error()
        self.calls.append(("list_trips", user_token, user_id, request_id, keyword, limit))
        return self.result

    async def update_trip(self, user_token, user_id, request_id, trip_id, payload):
        self._raise_if_error()
        self.calls.append(("update_trip", user_token, user_id, request_id, trip_id, payload))
        return self.trip

    async def delete_trip(self, user_token, user_id, request_id, trip_id):
        self._raise_if_error()
        self.calls.append(("delete_trip", user_token, user_id, request_id, trip_id))
        return {"status": "ok"}

    async def estimate_taxi(self, user_token, user_id, request_id, payload):
        self._raise_if_error()
        self.calls.append(("estimate_taxi", user_token, user_id, request_id, payload))
        return {"vehicle_options": [{"vehicle_type": "快车"}]}

    async def create_taxi_order(self, user_token, user_id, request_id, idempotency_key, payload):
        self._raise_if_error()
        self.calls.append(("create_taxi_order", user_token, user_id, request_id, idempotency_key, payload))
        return self.order

    async def get_taxi_order(self, user_token, user_id, request_id, order_id):
        self._raise_if_error()
        self.calls.append(("get_taxi_order", user_token, user_id, request_id, order_id))
        return self.order

    async def list_taxi_orders(self, user_token, user_id, request_id, status=None, limit=5):
        self._raise_if_error()
        self.calls.append(("list_taxi_orders", user_token, user_id, request_id, status, limit))
        return {"orders": [], "total": 0}

    async def cancel_taxi_order(self, user_token, user_id, request_id, order_id, reason=None):
        self._raise_if_error()
        self.calls.append(("cancel_taxi_order", user_token, user_id, request_id, order_id, reason))
        return {**self.order, "status": "CANCELLED"}


_config_module = types.ModuleType("config")
_config_module.config_manager = FakeConfigManager()
_config_module.logger = logging.getLogger("unit-test")
sys.modules["config"] = _config_module

_clients_module = types.ModuleType("clients")
_clients_module.BackendError = FakeBackendError
_clients_module.BackendAuthError = FakeBackendAuthError
_clients_module.BackendForbiddenError = FakeBackendForbiddenError
_clients_module.BackendNotFoundError = FakeBackendNotFoundError
_clients_module.BackendConflictError = FakeBackendConflictError
_clients_module.BackendValidationError = FakeBackendValidationError
_clients_module.BackendUnavailableError = FakeBackendUnavailableError
_clients_module.backend_client = FakeBackendClient()
sys.modules["clients"] = _clients_module


def _load_module(module_name: str, relative_path: str):
    path = AC_ROOT / relative_path
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载模块: {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


@pytest.fixture(scope="session")
def load_module():
    return _load_module


@pytest.fixture
def fake_config():
    return _config_module.config_manager


@pytest.fixture
def fake_backend_client():
    client = _clients_module.backend_client
    client.reset()
    return client


def pytest_sessionfinish(session, exitstatus):
    """恢复被替换的模块，避免未来同一进程内的集成测试读到桩。"""
    for name, original in _ORIGINAL_MODULES.items():
        if original is None:
            sys.modules.pop(name, None)
        else:
            sys.modules[name] = original
