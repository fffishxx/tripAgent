from pathlib import Path
from typing import Dict, Any
import os
import re
from util import YamlLoader, get_project_root
from common import *

# 支持 ${VAR} 与 ${VAR:default}；default 可以包含冒号（如 URL）
_ENV_PLACEHOLDER_PATTERN = re.compile(
    r"^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}$", re.DOTALL)


class ConfigManager:
    """
    单例配置管理器，用于加载、获取和管理全局配置。

    特性：
    - 单例模式，确保全局只有一个实例
    - 支持 YAML 配置文件加载
    - 支持嵌套 key 路径访问，例如 'database.host'
    - 支持 ${ENV_VAR} 形式的环境变量替换
    """

    _instance = None
    _config = None  # 存储加载后的全局配置字典

    def __new__(cls):
        """单例模式实现，确保全局仅有一个 ConfigManager 实例"""
        if cls._instance is None:
            cls._instance = super(ConfigManager, cls).__new__(cls)
        return cls._instance

    def load_config(self, file_path="application.yml", default=None, required_keys=None):
        """
        加载 YAML 配置文件，并存储到全局变量 `_config`。

        如果 `_config` 已经加载过，则直接返回已加载内容。

        Args:
            file_path (str): 配置文件路径，相对于项目根目录
            default (dict, optional): 默认值
            required_keys (list, optional): 必须存在的 key 列表，缺失会抛出异常

        Returns:
            dict: 加载后的配置字典
        """
        if self._config is None:
            self._config = YamlLoader.load(
                file_path=get_project_root() / file_path,
                default=default,
                required_keys=required_keys
            )
        return self._config

    def get_config(self) -> Dict[str, Any]:
        """
        获取全局配置字典。

        Raises:
            RuntimeError: 如果配置尚未加载
        """
        if self._config is None:
            raise RuntimeError("配置尚未加载，请先调用 load_config 方法")
        return self._config

    def get(self, key_path: str, default=None):
        """
        根据点分路径获取配置值，例如 'database.host'。

        支持 ${ENV_VAR} 与 ${ENV_VAR:inline_default}：
        - 环境变量优先；
        - 环境变量未设置时使用 inline_default；
        - 没有 inline_default 时使用 fallback（本方法的 default 参数）。
        placeholder 默认按字符串返回，数值/布尔请使用 get_int/get_float/get_bool。

        Args:
            key_path (str): 配置 key 路径，支持嵌套
            default: 如果找不到 key 或非 placeholder 的环境变量未设置时返回的默认值

        Returns:
            Any: 配置值或默认值
        """
        if self._config is None:
            raise RuntimeError("配置尚未加载，请先调用 load_config 方法")

        keys = key_path.split('.')
        current = self._config

        try:
            # 遍历嵌套字典获取值
            for key in keys:
                current = current[key]
            return self._resolve_value(current, default)
        except (KeyError, TypeError):
            return default

    def _resolve_value(self, value: Any, fallback=None):
        """解析单个 ${ENV_VAR:default} 占位符。"""
        if not isinstance(value, str):
            return value

        match = _ENV_PLACEHOLDER_PATTERN.fullmatch(value.strip())
        if not match:
            return value

        env_name = match.group(1)
        inline_default = match.group(2)

        if env_name in os.environ:
            return os.environ[env_name]
        if inline_default is not None:
            return inline_default
        return fallback

    def get_bool(self, key_path: str, default: bool = False) -> bool:
        """按布尔语义读取配置；非法值抛出 ValueError。"""
        value = self.get(key_path, default)
        if isinstance(value, bool):
            return value
        if isinstance(value, (int, float)) and value in (0, 1):
            return bool(value)
        if isinstance(value, str):
            normalized = value.strip().lower()
            if normalized in ("true", "1", "yes", "on"):
                return True
            if normalized in ("false", "0", "no", "off"):
                return False
        raise ValueError(f"配置 {key_path} 不是合法布尔值: {value!r}")

    def get_int(self, key_path: str, default=None):
        """按整数语义读取配置；非法值抛出 ValueError。"""
        value = self.get(key_path, default)
        if value is None:
            return None
        try:
            return int(value)
        except (TypeError, ValueError) as exc:
            raise ValueError(f"配置 {key_path} 不是合法整数: {value!r}") from exc

    def get_float(self, key_path: str, default=None):
        """按浮点语义读取配置；非法值抛出 ValueError。"""
        value = self.get(key_path, default)
        if value is None:
            return None
        try:
            return float(value)
        except (TypeError, ValueError) as exc:
            raise ValueError(f"配置 {key_path} 不是合法浮点数: {value!r}") from exc


def load_env_file(path: Path | str | None = None) -> None:
    """加载同目录 .env（KEY=VALUE），已有环境变量优先，不覆盖。"""
    env_path = Path(path) if path is not None else get_project_root() / ".env"
    if not env_path.exists():
        return

    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export "):].strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key or key in os.environ:
            continue
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        os.environ[key] = value


# ---------------- 全局配置管理器实例 ----------------
config_manager = ConfigManager()
load_env_file()
config_manager.load_config()

if __name__ == "__main__":
    print(config_manager.get(SERVER_PORT)) # 输出：18089
    print(config_manager.get(SERVER_HOST)) # 输出：0.0.0.0
    print(config_manager.get(JWT_EXPIRE_HOURS))
