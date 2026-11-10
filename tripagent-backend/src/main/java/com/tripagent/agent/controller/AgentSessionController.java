package com.tripagent.agent.controller;

import com.tripagent.agent.client.GatewayAgentClient;
import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.context.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * 前端会话管理入口：Java 校验身份后，经 Gateway 转发到 Agent。
 *
 * <p>热门问题、匿名停止允许匿名；其余会话接口必须登录。</p>
 */
@RestController
@RequestMapping("/api/v1/agent/sessions")
@RequiredArgsConstructor
@Tag(name = "Agent 会话代理", description = "Java -> Gateway -> FastAPI Agent")
public class AgentSessionController {

    private final GatewayAgentClient gatewayAgentClient;

    @PostMapping
    @Operation(summary = "创建会话")
    public ResponseEntity<String> create(
            @RequestParam(defaultValue = "1") int n,
            @RequestParam(name = "agent_id", defaultValue = "1004") int agentId,
            HttpServletRequest request) {
        Long userId = UserContextHolder.getRequiredUserId();
        String path = UriComponentsBuilder.fromPath("/acs/session")
                .queryParam("n", n)
                .queryParam("agent_id", agentId)
                .queryParam("user_id", userId)
                .build().encode().toUriString();
        return forward("POST", path, null, userId, requiredToken(request), requestId());
    }

    @GetMapping("/hot")
    @Operation(summary = "热门问题")
    public ResponseEntity<String> hot(
            @RequestParam(defaultValue = "1") int n,
            @RequestParam(name = "agent_id", defaultValue = "1004") int agentId,
            HttpServletRequest request) {
        String path = UriComponentsBuilder.fromPath("/acs/session/hot")
                .queryParam("n", n)
                .queryParam("agent_id", agentId)
                .build().encode().toUriString();
        Long userId = UserContextHolder.getUserId();
        return forward("GET", path, null, userId,
                userId == null ? null : token(request), requestId());
    }

    @GetMapping("/{agentId}/{sessionId}")
    @Operation(summary = "会话详情")
    public ResponseEntity<String> detail(
            @PathVariable int agentId,
            @PathVariable String sessionId,
            HttpServletRequest request) {
        Long userId = UserContextHolder.getRequiredUserId();
        String path = "/acs/session/" + agentId + "/" + userId + "/" + sessionId;
        return forward("GET", path, null, userId, requiredToken(request), requestId());
    }

    @GetMapping("/history")
    @Operation(summary = "会话历史")
    public ResponseEntity<String> history(
            @RequestParam(name = "agent_id", defaultValue = "1004") int agentId,
            HttpServletRequest request) {
        Long userId = UserContextHolder.getRequiredUserId();
        String path = UriComponentsBuilder.fromPath("/acs/session/history")
                .queryParam("agent_id", agentId)
                .queryParam("user_id", userId)
                .build().encode().toUriString();
        return forward("GET", path, null, userId, requiredToken(request), requestId());
    }

    @DeleteMapping("/history")
    @Operation(summary = "删除会话历史")
    public ResponseEntity<String> deleteHistory(
            @RequestParam(name = "agent_id", defaultValue = "1004") int agentId,
            @RequestParam(name = "session_id") String sessionId,
            HttpServletRequest request) {
        Long userId = UserContextHolder.getRequiredUserId();
        String path = UriComponentsBuilder.fromPath("/acs/session/history")
                .queryParam("agent_id", agentId)
                .queryParam("user_id", userId)
                .queryParam("session_id", sessionId)
                .build().encode().toUriString();
        return forward("DELETE", path, null, userId, requiredToken(request), requestId());
    }

    @PutMapping("/history")
    @Operation(summary = "更新会话标题")
    public ResponseEntity<String> updateHistory(
            @RequestParam(name = "agent_id", defaultValue = "1004") int agentId,
            @RequestParam(name = "session_id") String sessionId,
            @RequestParam String title,
            HttpServletRequest request) {
        Long userId = UserContextHolder.getRequiredUserId();
        String path = UriComponentsBuilder.fromPath("/acs/session/history")
                .queryParam("agent_id", agentId)
                .queryParam("user_id", userId)
                .queryParam("session_id", sessionId)
                .queryParam("title", title)
                .build().encode().toUriString();
        return forward("PUT", path, null, userId, requiredToken(request), requestId());
    }

    @PostMapping("/stop")
    @Operation(summary = "停止会话（允许匿名）")
    public ResponseEntity<String> stop(
            @RequestParam(name = "session_id") String sessionId,
            @RequestParam(name = "agent_id", defaultValue = "1004") int agentId,
            HttpServletRequest request) {
        Long userId = UserContextHolder.getUserId();
        String path = UriComponentsBuilder.fromPath("/acs/session/stop")
                .queryParam("session_id", sessionId)
                .queryParam("agent_id", agentId)
                .build().encode().toUriString();
        return forward("POST", path, null, userId,
                userId == null ? null : token(request), requestId());
    }

    private ResponseEntity<String> forward(String method,
                                           String path,
                                           String body,
                                           Long userId,
                                           String userToken,
                                           String requestId) {
        try {
            HttpResponse<String> upstream = gatewayAgentClient.json(
                    method, path, body, userId, userToken, requestId);
            MediaType contentType = MediaType.APPLICATION_JSON;
            String upstreamType = upstream.headers().firstValue("Content-Type").orElse("");
            if (!upstreamType.isBlank()) {
                try {
                    contentType = MediaType.parseMediaType(upstreamType);
                } catch (Exception ignored) {
                    contentType = MediaType.APPLICATION_JSON;
                }
            }
            return ResponseEntity.status(upstream.statusCode())
                    .contentType(contentType)
                    .body(upstream.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "会话请求被中断");
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "会话服务暂时不可用");
        }
    }

    private String requiredToken(HttpServletRequest request) {
        String token = token(request);
        if (token == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态不完整");
        }
        return token;
    }

    private String token(HttpServletRequest request) {
        return (String) request.getAttribute(CommonConstants.REQUEST_USER_TOKEN_ATTR);
    }

    private String requestId() {
        String requestId = MDC.get(CommonConstants.MDC_REQUEST_ID);
        return (requestId == null || requestId.isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : requestId;
    }
}
