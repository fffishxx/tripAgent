"""兼容导出。

阶段六曾在这里解析用户 JWT；新协议下用户身份来自 Gateway 的 X-User-Id，
由 RequestFilter 写入可信上下文。新代码请直接使用 ``web.auth``。
"""

from web.auth import get_user_token, optional_user_id, require_user_id

__all__ = ["get_user_token", "optional_user_id", "require_user_id"]
