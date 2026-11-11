from typing import AsyncIterable, Optional

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from agent.BaseAgent import make_sse_event  # SSE 事件格式化工具
from config import logger
from dao import chat_session_dao
from agent import AGENTS  # 全局智能体实例字典
from web.auth import get_user_token, optional_user_id

# ========================= 创建路由对象 =========================
chat_router = APIRouter()

# ========================= 请求体模型 =========================
class ChatRequest(BaseModel):
    """用户发送给智能体的请求体。

    用户身份由 Gateway 的 X-User-Id 传递，不再放在请求体中。
    """
    question: str
    sessionId: str
    agentId: int = 1004

# ========================= SSE 错误流 =========================
async def error_stream(message: str) -> AsyncIterable[str]:
    yield make_sse_event(404, message)

# ========================= SSE 响应封装 =========================
def stream(data: AsyncIterable[str]) -> StreamingResponse:
    return StreamingResponse(
        data,
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache"}
    )

# ========================= 流式对话接口 =========================
@chat_router.post("")
async def chat(
        req: ChatRequest,
        user_id: Optional[int] = Depends(optional_user_id),
        user_token: str = Depends(get_user_token),
):
    """
    用户发起流式对话。

    - user_id/user_token 来自 RequestFilter 验证后的可信请求上下文；
    - 匿名请求 user_id 为 None，user_token 为空；
    - Agent 不解析用户 JWT，仅把 user_token 透传给 Java 业务接口。
    """
    logger.info("【ChatRouter】收到请求：question = %s, sessionId = %s, agentId = %s",
                req.question, req.sessionId, req.agentId)

    owner = chat_session_dao.get_session_owner(req.sessionId, req.agentId)
    if owner is not None and owner != 0 and owner != user_id:
        logger.warning("会话归属校验失败：sessionId=%s, owner=%s, user_id=%s",
                       req.sessionId, owner, user_id)
        raise HTTPException(status_code=403, detail="无权访问该会话")

    agent = AGENTS.get(req.agentId, None)
    if agent is None:
        return stream(error_stream(f"Agent not found (agentId={req.agentId})"))

    return stream(agent.execute(req.question, req.sessionId, user_token, user_id))
