package com.agent.center.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;

/**
 * Gateway 调 Agent 的服务 Token 管理。
 *
 * <p>使用现有 AppKey + AppSecret 向 Agent /auth/token 换取应用 JWT，
 * 按 expire_hours 缓存并在过期前刷新；Agent 返回 401 时调用 invalidate()。</p>
 */
@Slf4j
@Component
public class ServiceTokenManager {

    private final WebClient webClient = WebClient.builder().build();
    private volatile CachedToken cachedToken;

    @Value("${gateway.agent.auth-url:http://127.0.0.1:18089/auth/token}")
    private String authUrl;

    @Value("${gateway.agent.app-key:}")
    private String appKey;

    @Value("${gateway.agent.app-secret:}")
    private String appSecret;

    @Value("${gateway.agent.refresh-margin-seconds:300}")
    private long refreshMarginSeconds;

    /**
     * 获取可用服务 Token；缓存可用时直接返回，否则在 boundedElastic 线程刷新。
     */
    public Mono<String> getToken() {
        CachedToken current = cachedToken;
        if (current != null && current.isValid()) {
            return Mono.just(current.token());
        }
        return Mono.fromCallable(this::refreshBlocking)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public void invalidate() {
        cachedToken = null;
    }

    private synchronized String refreshBlocking() {
        CachedToken current = cachedToken;
        if (current != null && current.isValid()) {
            return current.token();
        }
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(appSecret)) {
            throw new IllegalStateException("Gateway 未配置 Agent AppKey/AppSecret");
        }

        Map<String, Object> response = webClient.post()
                .uri(authUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("app_key", appKey, "app_secret", appSecret))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { })
                .block(Duration.ofSeconds(5));

        if (response == null || !StringUtils.hasText(String.valueOf(response.get("token")))) {
            throw new IllegalStateException("Agent /auth/token 未返回 token");
        }

        String token = String.valueOf(response.get("token"));
        long expireHours = parseLong(response.get("expire_hours"), 1L);
        long ttlSeconds = Math.max(60L, expireHours * 3600L - Math.max(0L, refreshMarginSeconds));
        cachedToken = new CachedToken(token, System.currentTimeMillis() + ttlSeconds * 1000L);
        log.info("智能体服务令牌已刷新，有效期={}秒", ttlSeconds);
        return token;
    }

    private long parseLong(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? defaultValue : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private record CachedToken(String token, long expiresAtMillis) {
        boolean isValid() {
            return System.currentTimeMillis() < expiresAtMillis;
        }
    }
}
