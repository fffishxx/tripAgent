package com.tripagent.common.constant;

/**
 * 全局通用常量。
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String AGENT_ID_HEADER = "X-Agent-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_TOKEN_HEADER = "X-User-Token";
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_AGENT_ID = "agentId";
    public static final String REQUEST_USER_ID_ATTR = "tripagent.request.userId";
    public static final String REQUEST_USER_TOKEN_ATTR = "tripagent.request.userToken";
    public static final String REQUEST_AGENT_ID_ATTR = "tripagent.request.agentId";
    public static final String DEFAULT_LOG_VALUE = "-";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String BEARER_PREFIX = "Bearer ";

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
}
