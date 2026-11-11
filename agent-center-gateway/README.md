# agent-center-gateway

TripAgent V2 的外部 Spring Cloud Gateway。负责：

1. 校验 Java 后端内部调用（`X-Internal-Token`）。
2. 清理外部传入的 `X-User-Id`/`X-User-Token`。
3. 用 AppKey + AppSecret 向 Agent `/auth/token` 获取并缓存服务 Token。
4. 将服务 Token 与可信用户身份转发给 Python Agent。
5. 原有 `/acs/**` 路由、限流、熔断保留。

## 请求头契约

Java → Gateway：

```http
X-Internal-Token: <内部共享 Token>
X-User-Id: <Java 从用户 JWT 解析>
X-User-Token: <用户 JWT，Agent 只透传>
X-Request-Id: <链路 ID>
```

Gateway → Agent：

```http
Authorization: Bearer <缓存的服务 Token>
X-User-Id: <可信用户 ID>
X-User-Token: <用户 JWT>
X-Request-Id: <链路 ID>
request-from: agent-center-gateway
```

匿名请求不带 `X-User-Id` / `X-User-Token`。

## 配置

`application.yml`：

```yaml
gateway:
  internal-token: ${GATEWAY_INTERNAL_TOKEN:}
  agent:
    auth-url: ${AGENT_AUTH_URL:http://127.0.0.1:18089/auth/token}
    app-key: ${AGENT_APP_KEY:}
    app-secret: ${AGENT_APP_SECRET:}
    refresh-margin-seconds: ${AGENT_TOKEN_REFRESH_MARGIN:300}
```

### 本地开发 `.env`

本地启动使用 `agent-center-gateway/.env`（已忽略，不提交）：

```bash
cp .env.example .env
# 填写 GATEWAY_INTERNAL_TOKEN、AGENT_APP_KEY/SECRET、NACOS_*、REDIS_*
```

`application-local.yml` 通过 `spring.config.import=optional:file:.env[.properties]` 自动读取。

| 变量 | 说明 |
|---|---|
| `GATEWAY_INTERNAL_TOKEN` | 与 Java `GATEWAY_INTERNAL_TOKEN` 一致 |
| `AGENT_APP_KEY` | 对应 `agent_center.app_info.app_key` |
| `AGENT_APP_SECRET` | 对应 `agent_center.app_info.app_secret` |
| `AGENT_AUTH_URL` | Agent `/auth/token`，例如 `http://127.0.0.1:18089/auth/token` |
| `AGENT_TOKEN_REFRESH_MARGIN` | 提前刷新秒数，默认 300 |
| `NACOS_*` | Nacos 地址/认证/服务发现 namespace/group/IP |
| `REDIS_*` | Gateway 限流 Redis |

`NACOS_DISCOVERY_IP` 无默认值，必须显式设置。Gateway 默认 AppKey/AppSecret/内部 Token 为空，缺失时不会使用仓库默认凭据。

> `deploy/.env` 是 Docker Compose 的部署配置，**Gateway 不会自动读取它**；外部 Gateway 部署时仍需在自己的启动环境/部署配置中设置相同值。

## 构建与运行

```bash
mvn -q -DskipTests package
# 在 agent-center-gateway 目录运行，application-local.yml 会读取同目录 .env
java -jar target/agent-center-gateway-1.0-SNAPSHOT.jar
```

也可以用环境变量覆盖 `.env` 中的同名值；环境变量优先级更高。

端口 10086；存活探针：

```http
GET http://localhost:10086/health
```

## 服务 Token 缓存

- `ServiceTokenManager` 调 Agent `/auth/token`，按 `expire_hours` 缓存。
- 过期前 `refresh-margin-seconds` 主动刷新。
- Agent 返回 401 时 `AuthFilter` 失效缓存；Java 代理对未开始流的请求重试一次。
- 日志不打印完整服务 Token。

## 安全边界

- Gateway 不公网暴露；只允许 Java 内部网络访问。
- 外部请求即使伪造 `X-User-Id`，没有 `X-Internal-Token` 也会 401。
- Gateway 删除外部用户头后，只重新写入经过内部 Token 校验的值。
- 用户 JWT 由 Java 最终验签，Gateway/Agent 不解析。

## 测试

```bash
mvn -q test
```

当前覆盖：缺失内部 Token、用户头不完整、有效内部调用、匿名调用、健康检查放行。
