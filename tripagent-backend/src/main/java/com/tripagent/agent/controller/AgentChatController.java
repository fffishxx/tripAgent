package com.tripagent.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.agent.client.GatewayAgentClient;
import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.context.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 前端聊天入口：Java 校验用户身份后，经 Gateway 转发到 Agent。
 *
 * <p>该接口使用可选认证：未登录可匿名规划/问答；登录时把可信
 * user_id/user_token 交给 Gateway。SSE 原样透传，不包统一 Result。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "Agent 聊天代理", description = "Java -> Gateway -> FastAPI Agent")
public class AgentChatController {

    private final GatewayAgentClient gatewayAgentClient;
    private final ObjectMapper objectMapper;

    @PostMapping("/chat")
    @Operation(summary = "聊天（SSE 透传）")
    public void chat(@Valid @RequestBody ChatRequest request,
                     HttpServletRequest servletRequest,
                     HttpServletResponse response) {
        Long userId = UserContextHolder.getUserId();
        String userToken = (String) servletRequest.getAttribute(CommonConstants.REQUEST_USER_TOKEN_ATTR);
        if (userId != null && userToken == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态不完整");
        }

        String requestId = MDC.get(CommonConstants.MDC_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", request.question());
        payload.put("sessionId", request.sessionId());
        payload.put("agentId", request.agentId() == null ? 1004 : request.agentId());

        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "请求序列化失败");
        }

        final HttpResponse<InputStream> upstream;
        try {
            upstream = gatewayAgentClient.stream(
                    "/acs/chat", json, userId, userToken, requestId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "聊天请求被中断");
        } catch (IOException e) {
            log.error("调用网关聊天接口失败：{}", e.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Agent 服务暂时不可用");
        }

        int status = upstream.statusCode();
        if (status >= 200 && status < 300) {
            response.setStatus(status);
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("X-Accel-Buffering", "no");
            try (InputStream inputStream = upstream.body()) {
                inputStream.transferTo(response.getOutputStream());
                response.getOutputStream().flush();
            } catch (IOException e) {
                log.debug("SSE 连接结束：{}", e.getMessage());
            }
            return;
        }

        String errorBody;
        try (InputStream inputStream = upstream.body()) {
            errorBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            errorBody = "{\"code\":" + status + ",\"message\":\"Agent 服务调用失败\"}";
        }
        String upstreamType = upstream.headers().firstValue("Content-Type").orElse("");
        response.setStatus(status);
        response.setContentType(upstreamType.isBlank() ? MediaType.APPLICATION_JSON_VALUE : upstreamType);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getOutputStream().write(errorBody.getBytes(StandardCharsets.UTF_8));
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.debug("写出智能体错误响应失败：{}", e.getMessage());
        }
    }

    public record ChatRequest(
            @NotBlank(message = "question 不能为空") String question,
            @NotBlank(message = "sessionId 不能为空") String sessionId,
            Integer agentId
    ) {
    }
}
