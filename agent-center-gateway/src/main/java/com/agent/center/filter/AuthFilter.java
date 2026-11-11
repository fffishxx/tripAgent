package com.agent.center.filter;

import com.agent.center.auth.ServiceTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Gateway 认证过滤器。
 *
 * <p>只信任携带 X-Internal-Token 的 Java 内部调用；清理外部传入的
 * X-User-Id/X-User-Token，使用 AppKey/AppSecret 获取并缓存 Agent 服务 Token，
 * 最终向下游转发：</p>
 *
 * <pre>
 * Authorization: Bearer SERVICE_TOKEN
 * X-User-Id: 10001
 * X-User-Token: USER_JWT
 * X-Request-Id: ...
 * request-from: agent-center-gateway
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_TOKEN_HEADER = "X-User-Token";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_FROM_HEADER = "request-from";

    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[1-9][0-9]{0,18}$");

    private final ServiceTokenManager serviceTokenManager;

    @Value("${gateway.internal-token:}")
    private String internalToken;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();
        // 只放行网关自身的健康检查与熔断降级。
        // /auth/token 不能被跳过：/acs/auth/token 经 StripPrefix 后会变成 /auth/token，
        // 否则外部客户端可绕过内部 Token 直接申请 Agent 服务 Token。
        if (path.startsWith("/fallback") || path.startsWith("/health")) {
            return chain.filter(exchange);
        }

        String providedInternalToken = exchange.getRequest().getHeaders().getFirst(INTERNAL_TOKEN_HEADER);
        if (!constantTimeEquals(providedInternalToken, internalToken)) {
            log.warn("拒绝非 Java 内部调用：路径={}", path);
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        String userIdHeader = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
        String userToken = exchange.getRequest().getHeaders().getFirst(USER_TOKEN_HEADER);

        Long userId = null;
        if (StringUtils.hasText(userIdHeader)) {
            if (!USER_ID_PATTERN.matcher(userIdHeader.trim()).matches()) {
                log.warn("非法 X-User-Id：路径={}", path);
                return reject(exchange, HttpStatus.BAD_REQUEST);
            }
            if (!StringUtils.hasText(userToken)) {
                log.warn("存在 X-User-Id 但缺少 X-User-Token：路径={}", path);
                return reject(exchange, HttpStatus.UNAUTHORIZED);
            }
            userId = Long.valueOf(userIdHeader.trim());
        } else if (StringUtils.hasText(userToken)) {
            log.warn("存在 X-User-Token 但缺少 X-User-Id：路径={}", path);
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        final Long trustedUserId = userId;
        final String trustedUserToken = userToken == null ? null : userToken.trim();

        return serviceTokenManager.getToken()
                .flatMap(serviceToken -> {
                    ServerWebExchange mutated = exchange.mutate()
                            .request(builder -> builder.headers(headers -> {
                                headers.remove(AUTHORIZATION_HEADER);
                                headers.remove(INTERNAL_TOKEN_HEADER);
                                headers.remove(USER_ID_HEADER);
                                headers.remove(USER_TOKEN_HEADER);
                                headers.remove(REQUEST_FROM_HEADER);

                                headers.set(AUTHORIZATION_HEADER, "Bearer " + serviceToken);
                                headers.set(REQUEST_FROM_HEADER, "agent-center-gateway");
                                if (trustedUserId != null) {
                                    headers.set(USER_ID_HEADER, String.valueOf(trustedUserId));
                                    headers.set(USER_TOKEN_HEADER, trustedUserToken);
                                }
                            }))
                            .build();

                    return chain.filter(mutated);
                })
                .doOnSuccess(ignored -> {
                    if (exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED) {
                        serviceTokenManager.invalidate();
                    }
                })
                .onErrorResume(error -> {
                    log.error("获取智能体服务令牌失败：{}", error.getMessage());
                    return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    private boolean constantTimeEquals(String actual, String expected) {
        if (!StringUtils.hasText(actual) || !StringUtils.hasText(expected)) {
            return false;
        }
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
