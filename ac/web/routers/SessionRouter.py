from typing import Optional

from fastapi import APIRouter, Depends, HTTPException

from config import logger
from dao import chat_session_dao  # ChatSession 数据访问对象
from web.auth import optional_user_id, require_user_id
from web.routers.ChatRouter import AGENTS  # 全局智能体实例字典

# ========================= 创建路由对象 =========================
session_router = APIRouter()

NO_CHANGE_MESSAGE = {"status": "ok", "message": "No change"}


def _get_agent_or_404(agent_id: int):
    agent = AGENTS.get(agent_id)
    if agent is None:
        raise HTTPException(status_code=404, detail=f"Agent not found (agentId={agent_id})")
    return agent


def _ensure_user_match(requested_user_id: Optional[int], current_user_id: int) -> None:
    """查询参数中的 user_id 只能与 Gateway 可信身份一致；不一致返回 403。"""
    if requested_user_id is not None and requested_user_id != current_user_id:
        raise HTTPException(status_code=403, detail="无权访问该用户数据")


# ========================= 创建新会话 =========================
@session_router.post("")
async def create_session(
        n: int,
        agent_id: int,
        user_id: Optional[int] = None,
        current_user_id: int = Depends(require_user_id),
):
    """
    创建新的聊天会话，并返回会话VO。

    必须登录；user_id 由 Gateway 的 X-User-Id 决定，查询参数仅用于兼容校验。
    匿名用户不创建 chat_session（前端生成 UUID 直接调 /chat）。
    """
    _get_agent_or_404(agent_id)
    _ensure_user_match(user_id, current_user_id)
    return chat_session_dao.create_session(n, agent_id, current_user_id)


# ========================= 获取热门问题 =========================
@session_router.get("/hot")
async def get_hots(n: int, agent_id: int):
    _get_agent_or_404(agent_id)
    return chat_session_dao.hot_question(n, agent_id)


# ========================= 查询会话细节 =========================
@session_router.get("/{agent_id}/{user_id}/{session_id}")
async def session_detail(
        agent_id: int,
        user_id: int,
        session_id: str,
        current_user_id: int = Depends(require_user_id),
):
    """
    查询某个具体会话的详情。

    必须登录，且只能查询自己的会话；他人/匿名会话返回 404，避免泄露存在性。
    """
    agent = _get_agent_or_404(agent_id)
    _ensure_user_match(user_id, current_user_id)

    owner = chat_session_dao.get_session_owner(session_id, agent_id)
    if owner is None or owner != current_user_id:
        raise HTTPException(status_code=404, detail="会话不存在或无权访问")

    return await agent.session_detail(current_user_id, session_id)


# ========================= 停止会话接口 =========================
@session_router.post("/stop")
def stop(
        session_id: str,
        agent_id: int,
        current_user_id: Optional[int] = Depends(optional_user_id),
):
    """
    停止指定 session 的对话。

    已登录用户的会话必须由本人停止；匿名/无记录会话允许凭 session_id 停止。
    用户身份来自 RequestFilter 校验后的可信 X-User-Id。
    """
    agent = _get_agent_or_404(agent_id)
    owner = chat_session_dao.get_session_owner(session_id, agent_id)

    if owner is not None and owner > 0:
        if current_user_id is None:
            raise HTTPException(status_code=401, detail="请先登录后再操作")
        if owner != current_user_id:
            raise HTTPException(status_code=404, detail="会话不存在或无权访问")

    logger.debug("【SessionRouter】停止会话：sessionId=%s", session_id)
    agent.stop(session_id)
    return {"status": "ok"}


# ========================= 查询历史会话 =========================
@session_router.get("/history")
def get_history(
        agent_id: int,
        user_id: Optional[int] = None,
        current_user_id: int = Depends(require_user_id),
):
    _get_agent_or_404(agent_id)
    _ensure_user_match(user_id, current_user_id)
    return chat_session_dao.query_history_session(
        agent_id=agent_id, user_id=current_user_id)


# ========================= 删除历史会话 =========================
@session_router.delete("/history")
async def delete_history(
        agent_id: int,
        session_id: str,
        user_id: Optional[int] = None,
        current_user_id: int = Depends(require_user_id),
):
    """删除会话元数据并清理 LangGraph Checkpointer 线程。"""
    agent = _get_agent_or_404(agent_id)
    _ensure_user_match(user_id, current_user_id)

    owner = chat_session_dao.get_session_owner(session_id, agent_id)
    if owner is None or owner != current_user_id:
        # 他人/不存在的会话不暴露差异
        return NO_CHANGE_MESSAGE

    result = chat_session_dao.delete_history_session(
        agent_id=agent_id, user_id=current_user_id, session_id=session_id)
    await agent.delete_session(session_id)
    return result


# ========================= 更新历史会话标题 =========================
@session_router.put("/history")
def update_history(
        agent_id: int,
        session_id: str,
        title: str,
        user_id: Optional[int] = None,
        current_user_id: int = Depends(require_user_id),
):
    _get_agent_or_404(agent_id)
    _ensure_user_match(user_id, current_user_id)

    owner = chat_session_dao.get_session_owner(session_id, agent_id)
    if owner is None or owner != current_user_id:
        return NO_CHANGE_MESSAGE

    return chat_session_dao.update_history_session(
        agent_id=agent_id,
        user_id=current_user_id,
        session_id=session_id,
        title=title,
    )
