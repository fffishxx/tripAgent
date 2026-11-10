# tripagent-backend

TripAgent V2 的 Java 业务后端（阶段七：Java → Gateway → Agent 身份链路）。

## 技术栈

- Java 17（本机可用 JDK 21，以 `release 17` 编译）
- Spring Boot 3.2.10
- Spring MVC + Spring Validation
- MyBatis-Plus 3.5.11
- MySQL 8
- Knife4j / OpenAPI 3
- Lombok
- JJWT（RS256 用户 JWT）

## 数据库

业务库：`tripagent`（与 Python Agent 的 `agent_center` 库隔离）。

- 表结构参考：`src/main/resources/sql/schema.sql`
- 该脚本不会自动执行（`spring.sql.init.mode=never`），已有库请勿直接重复执行。
- 连接信息支持环境变量注入：

```bash
export DB_URL="jdbc:mysql://127.0.0.1:3306/tripagent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
export DB_USERNAME=root
export DB_PASSWORD=your-db-password
```

生产环境请使用环境变量/密钥管理，不要把数据库密码提交到公共仓库。
本地可复制 `.env.example` 为 `.env`（已忽略），`application.yml` 会通过 `spring.config.import` 自动读取；`DB_PASSWORD`、`AGENT_SERVICE_TOKEN`、`GATEWAY_INTERNAL_TOKEN` 不再有仓库默认值。

## 用户 JWT 密钥

- 私钥默认：`~/.tripagent/keys/user-jwt-private.pem`（仓库外，不提交）
- 公钥默认：`src/main/resources/keys/user-jwt-public.pem`（可提交，Java 自身校验；阶段七起 Python 不再校验用户 JWT）
- 可通过 `USER_JWT_PRIVATE_KEY` / `USER_JWT_PUBLIC_KEY` 覆盖；支持 `file:`、`classpath:` 或普通路径。

首次生成：

```bash
mkdir -p ~/.tripagent/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ~/.tripagent/keys/user-jwt-private.pem
openssl rsa -pubout -in ~/.tripagent/keys/user-jwt-private.pem -out ~/.tripagent/keys/user-jwt-public.pem
cp ~/.tripagent/keys/user-jwt-public.pem src/main/resources/keys/user-jwt-public.pem
```

## 启动

```bash
# 确保 JAVA_HOME 指向 JDK 17+，例如 D:\develop\jdk21
mvn clean package -DskipTests
GATEWAY_BASE_URL=http://127.0.0.1:10086 \
GATEWAY_INTERNAL_TOKEN=dev-gateway-internal-token \
java -jar target/tripagent-backend-0.1.0-SNAPSHOT.jar
```

或开发模式：

```bash
GATEWAY_BASE_URL=http://127.0.0.1:10086 \
GATEWAY_INTERNAL_TOKEN=dev-gateway-internal-token \
mvn spring-boot:run
```

默认端口 `8080`，可用 `SERVER_PORT` 覆盖。聊天/会话代理需要外部 Gateway（默认 10086）运行。

启动成功后会打印中文日志：

```text
服务启动完毕
服务名称：tripagent-backend
运行环境：dev
服务端口：8080
健康检查：http://localhost:8080/api/v1/health
接口文档：http://localhost:8080/doc.html
```

> Spring Boot/Tomcat/Hibernate 等第三方框架自身的日志仍为英文。

## 阶段二验证地址

| 用途 | 地址 |
|---|---|
| 应用健康 | `GET /api/v1/health` |
| 数据库连通 + MyBatis-Plus 查询 | `GET /api/v1/health/db` |
| Knife4j 文档 | `http://localhost:8080/doc.html` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| MyBatis-Plus CRUD 验证（dev） | `GET /api/v1/dev/mapper-crud` |
| 参数校验验证（dev） | `POST /api/v1/dev/validate` |
| 业务异常验证（dev） | `GET /api/v1/dev/error` |
| 未知异常验证（dev） | `GET /api/v1/dev/error-500` |

`/api/v1/dev/**` 仅在 `dev` profile 下存在。

参数校验示例：

```bash
curl -X POST http://localhost:8080/api/v1/dev/validate \
  -H 'Content-Type: application/json' \
  -d '{"name":"","days":0}'
```

## 阶段三业务接口

除注册/登录、健康检查、文档外，均需：

```text
Authorization: Bearer <user-jwt>
```

| 用途 | 地址 |
|---|---|
| 注册 | `POST /api/v1/auth/register` |
| 登录 | `POST /api/v1/auth/login` |
| 当前用户 | `GET/PUT /api/v1/users/me` |
| 偏好 | `GET/PUT /api/v1/preferences` |
| 行程 | `POST/GET /api/v1/trips`，`GET/PUT/DELETE /api/v1/trips/{tripId}` |
| 收藏 | `POST/GET /api/v1/favorites`，`DELETE /api/v1/favorites/{favoriteId}` |
| 打车估价 | `POST /api/v1/taxi/estimates` |
| 打车订单 | `POST/GET /api/v1/taxi/orders`，`GET /api/v1/taxi/orders/{orderId}` |
| 取消订单 | `POST /api/v1/taxi/orders/{orderId}/cancel` |
| 模拟完成 | `POST /api/v1/taxi/orders/{orderId}/complete` |

行程更新使用整单替换，必须携带当前 `version`；版本冲突返回业务码 `40902`。

## 阶段四 Agent API

Python Agent 使用 `/api/agent/v1/**`，数据字段为 snake_case。阶段七起所有请求需要：

```http
X-User-Token: <user-jwt>
X-User-Id: <user-id>
X-Service-Token: <agent-service-token>
X-Request-Id: <request-id>
X-Agent-Id: 1004
```

Java 会校验服务 Token、用户 JWT，并验证 `X-User-Id` 与 JWT `sub` 一致。
`Authorization` 只用于前端业务接口，不再用于 Python → Java。

创建类接口还必须携带：

```http
Idempotency-Key: <1~64 位幂等键>
```

| 用途 | 地址 |
|---|---|
| 查询偏好 | `GET /api/agent/v1/preferences` |
| 保存行程 | `POST /api/agent/v1/trips` |
| 查询最近行程 | `GET /api/agent/v1/trips?keyword=&limit=` |
| 行程详情 | `GET /api/agent/v1/trips/{tripId}` |
| 修改行程 | `PUT /api/agent/v1/trips/{tripId}`（需 `version`） |
| 删除行程 | `DELETE /api/agent/v1/trips/{tripId}` |
| 打车估价 | `POST /api/agent/v1/taxi/estimates` |
| 创建打车订单 | `POST /api/agent/v1/taxi/orders` |
| 查询订单 | `GET /api/agent/v1/taxi/orders`、`GET /api/agent/v1/taxi/orders/{orderId}` |
| 取消订单 | `POST /api/agent/v1/taxi/orders/{orderId}/cancel` |

- 创建行程/订单重复请求返回首次结果，`idempotent_replay=true`。
- 服务 Token 默认值仅用于开发：`security.agent.service-token` 与 Python `java.backend.service-token` 必须一致；生产环境通过 `AGENT_SERVICE_TOKEN` 注入。
- 阶段七起 Agent 不再校验用户 JWT；Java 用户 JWT 私钥/公钥由 Java 自己使用，外部 Gateway 需要配置 `AGENT_APP_KEY/AGENT_APP_SECRET` 和 `GATEWAY_INTERNAL_TOKEN`。

## 阶段三验收脚本

应用启动后执行（脚本默认访问 `http://localhost:8080`）：

```bash
python scripts/verify_stage3.py
# 或指定端口
BASE_URL=http://localhost:18080 python scripts/verify_stage3.py
```

脚本会创建测试手机号 `19900000001`、`19900000002` 及其测试数据；执行后可清理：

```bash
mysql -u root -p < scripts/cleanup_stage3_test_data.sql
```

## 阶段四验收脚本

`verify_stage4_agent_api.py` 只验证 Java Agent API（需要 Java 8080 + MySQL）：

```bash
BASE_URL=http://localhost:8080 python scripts/verify_stage4_agent_api.py
```

`verify_stage4_e2e.py` 验证完整跨服务闭环（需要 Java 8080 + Gateway 10086 + Python Agent 18089 + Nacos/MySQL/PG/Redis）：

```bash
python scripts/verify_stage4_e2e.py
```

清理测试数据：

```bash
mysql -u root -p < scripts/cleanup_stage4_test_data.sql
```

阶段四 E2E 会验证：自然语言生成行程 → 保存 → 重复保存 → Java 侧只有一份行程。

## 阶段五能力

- **用户偏好接入规划**
  - `ParamFillNode` 调 Java Agent API 查询偏好
  - 本轮用户明确指定的人群优先；否则使用偏好 crowd/budget
  - Planner / FormatPlan 提示词追加偏好上下文（兴趣、饮食、节奏、住宿、备注）
- **自然语言行程管理**
  - 保存、查询、更新、删除行程
  - 保存/更新会维护 `saved_trip_id` 与 `saved_trip_version`
  - 更新时先读取最新 version，再整单替换；删除为逻辑删除
- **自然语言打车**
  - Java 模拟打车为主流程：估价 → 车型选择 → 创建 Java 订单
  - 支持查询/取消打车订单
  - 滴滴 MCP 保留为可选分支：`taxi.use-didi: true` 且已配置滴滴 MCP 时启用
- **匿名访问**
  - 匿名可规划/问答
  - 保存行程、打车等业务操作返回 SSE `eventType=2002`（AUTH_REQUIRED），前端可据此弹登录框
- **注意**
  - 阶段七会话身份改由 Gateway 的 `X-User-Id` 传递；Agent 不再解析用户 JWT
  - 滴滴可选分支目前保留原有估价/跳转逻辑，Java 订单持久化以 Java 模拟主流程为准

## 阶段六/七能力

- **Java Agent 代理**
  - `POST /api/v1/agent/chat`（SSE，可选登录）
  - `/api/v1/agent/sessions/**`（会话创建/详情/历史/删除/更新/停止）
  - Java 校验 USER_JWT 后，经外部 Gateway 转发 `X-User-Id` / `X-User-Token`
- **会话鉴权**
  - Agent 校验 Gateway 服务 Token，从可信 `X-User-Id` 取用户
  - 他人会话详情 404、历史删除 no-change；删除历史同步清理 Checkpointer 线程
  - 匿名使用前端生成 UUID，不创建 `chat_session`
- **统一可观测性**
  - Python `X-Request-Id` 中间件 + 日志 request_id；Agent 工具调用记录耗时/结果
  - Java 访问日志包含 requestId/userId/agentId/耗时
- **测试**
  - Gateway 单元：7 项
  - Java JUnit5/Mockito：52 项
  - Python pytest 单元：60 项
  - Java API 集成：18 项
  - Python 跨服务集成：15 项
  - 完整自然语言 E2E：1 项（可选）
- **并发幂等修复**
  - 创建行程/订单使用 `READ_COMMITTED`，避免并发重复键冲突后误报 500
- **Docker 部署**
  - `deploy/docker-compose.yml` 最小四服务；外部 Nacos/Redis；配置已通过 `docker compose config`

详细说明见根目录：

- `../docs/project/JAVA_BACKEND.md`
- `../docs/project/ARCHITECTURE.md`
- `../docs/project/DEPLOYMENT.md`
- `../docs/project/AGENT_ARCHITECTURE.md`
- `../docs/progress/CURRENT.md`

## 阶段六/七测试

```bash
# Gateway 单元测试
cd agent-center-gateway
JAVA_HOME=D:/develop/jdk21 PATH="D:/develop/jdk21/bin:$PATH" mvn -q test

# Java 单元测试（需要 JDK 17+）
cd tripagent-backend
JAVA_HOME=D:/develop/jdk21 PATH="D:/develop/jdk21/bin:$PATH" mvn -q test

# Java Agent API 集成测试（Java 8080 + MySQL）
D:/develop/anaconda3/envs/ac_env_3135/python.exe -m pytest tests/integration -m integration -q
```

当前结果：Gateway 7、Java 单测 52、Java API 集成 18 项通过。
新链路 E2E：`python scripts/verify_stage5_e2e.py`（需 Java 8080 + Gateway 10086 + Python 18089 + Nacos/MySQL/PG/Redis）。
测试数据清理：

```bash
mysql -u root -p tripagent < scripts/cleanup_stage6_test_data.sql
mysql -u root -p < scripts/cleanup_stage5_test_data.sql
```

## 统一响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "...",
  "timestamp": "2026-11-06 15:30:00"
}
```

- `X-Request-Id` 请求头会被透传；缺失时自动生成。
- 参数错误、业务异常、未知异常都返回该结构，未知异常不泄露堆栈。

## 当前范围

阶段五已完成：

- 用户认证、偏好、行程、收藏、模拟打车业务
- Agent API（snake_case）、服务 Token、创建幂等
- `backend_client`、`java_tools`
- 用户偏好注入规划上下文（ParamFill + Planner + FormatPlan）
- 行程保存/查询/更新/删除自然语言闭环
- Java 模拟打车：估价、车型选择、下单、查询、取消
- 匿名用户业务操作 SSE 2002 登录提示
- 阶段五 E2E 与清理脚本

阶段六已完成：

- IDOR 修复、统一 request_id 日志、并发幂等事务隔离
- Java 单元/API 集成、Python 单元/跨服务集成/E2E 测试
- 最小四服务 Docker Compose、配置模板、密钥脚本
- 架构/部署/测试/工具链路文档

阶段七已完成：

- Java 聊天/会话代理（含 SSE）
- Java → Gateway 内部 Token 认证
- Gateway 获取/缓存 Agent 服务 Token，清理外部用户头
- Agent 服务 Token + 可信 X-User-Id；Agent 不再解析用户 JWT
- Agent → Java 使用 X-User-Token + X-User-Id + X-Service-Token
- 匿名聊天、用户隔离、伪造 X-User-Id、Token 复用测试

前端 Vue 3 已实现，见 `../tripagent-frontend/README.md`；当前剩余：

- Nginx 统一入口（部署文档已给出要求）
- 滴滴可选分支的 Java 订单持久化统一（当前按产品决策只生成跳转链接，不持久化）
- Docker 镜像实机构建（当前开发机 Docker daemon 未运行）

设计文档见项目根目录 `docs/project/`。
