# Agent Center（Python FastAPI + LangGraph）

TripAgent V2 的 Python Agent 服务，负责意图识别、出行规划、路线/打车/知识问答、LangGraph 工作流与 Java 业务接口调用。

## 技术栈

- Python 3.13
- FastAPI + Uvicorn
- LangGraph / LangChain / langchain-openai
- PostgreSQL（LangGraph Checkpointer / 会话历史）
- MySQL（`agent_center`：应用与会话元数据）
- Redis（流式输出停止标记）
- Nacos（提示词配置 + 服务注册）
- 高德 Web 服务、DeepSeek/OpenAI Compatible、可选滴滴 MCP
- Langfuse（可选 LLM 观测）

## 目录

```text
agent/              智能体与 LangGraph 节点/工具
clients/            Java 后端客户端（X-User-Token / X-Service-Token）
common/             常量与请求上下文
config/             配置、日志、数据库/Redis/Nacos/Langfuse
dao/                应用与会话 DAO
filter/             Gateway 校验、request_id ASGI 中间件
web/                FastAPI 应用与路由
sql/                agent_center 初始化 SQL
tests/              pytest 单元/集成测试
```

## 配置

`application.yml` 只保留 `${ENV_VAR:default}` 占位符，不再包含真实密钥；本地真实值放在 `ac/.env`（已忽略，不提交）。

```bash
cp .env.example .env
# 编辑 .env：DB_URL、JWT_*、OPENAI_API_KEY、AMAP_KEY、NACOS_* 等
```

容器配置模板位于 `../deploy/agent/application.yml`，由 Compose 通过环境变量注入。

`ConfigManager` 支持：

- 启动时自动读取 `ac/.env`（已有环境变量优先，不覆盖）；
- `${VAR}` / `${VAR:default}` 环境变量占位符；
- `get_bool/get_int/get_float` 显式类型读取；
- 环境变量优先级高于 inline default。

关键配置：

- `DB_URL`：MySQL `agent_center`
- `POSTGRES_URL`：PostgreSQL Checkpoint
- `REDIS_*`：Redis
- `NACOS_*`：Nacos 地址/认证/命名空间；`NACOS_DISCOVERY_IP` 必须显式设置
- `JAVA_BACKEND_BASE_URL` / `AGENT_SERVICE_TOKEN`
- `AGENT_ALLOWED_APP_KEYS`：允许调用 Agent 的 Gateway app_key（逗号分隔）
- `OPENAI_API_KEY`、`AMAP_KEY`、`DIDI_MCP_KEY`（可选）、`LANGFUSE_*`

## 启动

```bash
pip install -r requirements.txt
python main.py
```

默认端口 `18089`。启动前确保外部 Nacos/Redis、MySQL、PostgreSQL 可用；Nacos 需已有 8 个提示词 data-id。

## 测试

```bash
# 离线单测（不联网，当前 60 项）
python -m pytest tests/unit -q

# 跨服务集成（需 Java 8080 + Python 18089 + Nacos/MySQL/PG/Redis）
python -m pytest tests/integration -m "integration and not e2e" -q

# 完整自然语言 E2E（需 Java + Gateway 10086 + Python + LLM/Amap）
RUN_E2E=1 python -m pytest tests/integration -m e2e -q
```

测试依赖：`pip install -r requirements-test.txt`。

## 阶段七变更

- Agent 验证 Gateway 服务 Token（`Authorization: Bearer SERVICE_TOKEN`）与 app_key 白名单。
- 用户身份来自可信 `X-User-Id`；`X-User-Token` 只透传给 Java，Agent 不解析。
- `/session/**` 使用可信 `X-User-Id`；匿名使用前端生成 UUID，不创建 `chat_session`。
- Agent → Java 使用 `X-Service-Token + X-User-Token + X-User-Id`，不再用 Authorization。
- `X-Request-Id` 统一透传/生成，日志包含 request_id；工具调用记录耗时与结果。
- `/health` 提供无 Gateway 鉴权的容器探活。
- Java 业务调用路径、幂等键与错误映射见 `../docs/project/AGENT_ARCHITECTURE.md` 第 6 节。
