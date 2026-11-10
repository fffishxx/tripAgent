package com.tripagent.agent.client;

import com.tripagent.common.constant.CommonConstants;
import com.tripagent.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 调用外部 Gateway 的 HTTP 客户端。
 *
 * <p>Java 不直接访问 Agent；只携带 X-Internal-Token 与可信用户头调用 Gateway，
 * 由 Gateway 换取并附加 Agent 服务 Token。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayAgentClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration JSON_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(300);

    private final GatewayProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    /**
     * SSE/流式请求。
     */
    public HttpResponse<InputStream> stream(String path,
                                            String jsonBody,
                                            Long userId,
                                            String userToken,
                                            String requestId)
            throws IOException, InterruptedException {
        HttpRequest request = buildRequest("POST", path, jsonBody, userId, userToken, requestId,
                STREAM_TIMEOUT);
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 401) {
            closeQuietly(response.body());
            // Gateway 收到 401 会失效服务 Token；重试一次以获取新 Token
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        }
        log.info("网关流式调用 {}，状态码={}", path, response.statusCode());
        return response;
    }

    /**
     * JSON 请求。
     */
    public HttpResponse<String> json(String method,
                                     String path,
                                     String jsonBody,
                                     Long userId,
                                     String userToken,
                                     String requestId)
            throws IOException, InterruptedException {
        HttpRequest request = buildRequest(method, path, jsonBody, userId, userToken, requestId,
                JSON_TIMEOUT);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        log.info("网关请求 {} {}，状态码={}", method, path, response.statusCode());
        return response;
    }

    private HttpRequest buildRequest(String method,
                                     String path,
                                     String jsonBody,
                                     Long userId,
                                     String userToken,
                                     String requestId,
                                     Duration timeout) {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("未配置 gateway.base-url");
        }
        String baseUrl = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        URI uri = URI.create(baseUrl + path);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header(CommonConstants.INTERNAL_TOKEN_HEADER,
                        StringUtils.hasText(properties.getInternalToken()) ? properties.getInternalToken() : "");
        if (StringUtils.hasText(requestId)) {
            builder.header(CommonConstants.REQUEST_ID_HEADER, requestId);
        }
        if (userId != null) {
            builder.header(CommonConstants.USER_ID_HEADER, String.valueOf(userId));
            if (StringUtils.hasText(userToken)) {
                builder.header(CommonConstants.USER_TOKEN_HEADER, userToken);
            }
        }

        HttpRequest.BodyPublisher bodyPublisher = StringUtils.hasText(jsonBody)
                ? HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)
                : HttpRequest.BodyPublishers.noBody();
        builder.method(method, bodyPublisher);
        return builder.build();
    }

    private void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // ignore
        }
    }
}
