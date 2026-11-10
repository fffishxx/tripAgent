package com.tripagent.interceptor;

import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.config.AgentServiceProperties;
import com.tripagent.context.UserContext;
import com.tripagent.context.UserContextHolder;
import com.tripagent.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Agent 内部接口认证：服务身份 + 用户身份。
 *
 * <p>Python Agent 调用 /api/agent/** 时必须携带：</p>
 * <pre>
 * X-Service-Token: 服务共享密钥
 * X-User-Token:    用户 JWT（Agent 只透传）
 * X-User-Id:       必须与 X-User-Token.sub 一致
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class AgentServiceInterceptor implements HandlerInterceptor {

    private static final Pattern AGENT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final AgentServiceProperties properties;
    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String actualServiceToken = request.getHeader(CommonConstants.SERVICE_TOKEN_HEADER);
        if (!StringUtils.hasText(actualServiceToken)
                || !constantTimeEquals(actualServiceToken, properties.getServiceToken())) {
            throw new BusinessException(ResultCode.SERVICE_AUTH_FAILED);
        }

        String userToken = request.getHeader(CommonConstants.USER_TOKEN_HEADER);
        if (!StringUtils.hasText(userToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少用户凭证");
        }
        String userIdHeader = request.getHeader(CommonConstants.USER_ID_HEADER);
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少用户身份");
        }

        Long tokenUserId = jwtService.parseUserId(userToken.trim());
        long userId;
        try {
            userId = Long.parseLong(userIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户身份格式不正确");
        }
        if (!Long.valueOf(userId).equals(tokenUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "用户身份不一致");
        }

        UserContextHolder.set(new UserContext(userId, null, null));
        request.setAttribute(CommonConstants.REQUEST_USER_ID_ATTR, userId);
        request.setAttribute(CommonConstants.REQUEST_USER_TOKEN_ATTR, userToken.trim());

        String agentId = request.getHeader(CommonConstants.AGENT_ID_HEADER);
        if (!StringUtils.hasText(agentId) || !AGENT_ID_PATTERN.matcher(agentId).matches()) {
            agentId = CommonConstants.DEFAULT_LOG_VALUE;
        }
        request.setAttribute(CommonConstants.REQUEST_AGENT_ID_ATTR, agentId);

        MDC.put(CommonConstants.MDC_USER_ID, String.valueOf(userId));
        MDC.put(CommonConstants.MDC_AGENT_ID, agentId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        UserContextHolder.clear();
        MDC.remove(CommonConstants.MDC_USER_ID);
        MDC.remove(CommonConstants.MDC_AGENT_ID);
    }

    private boolean constantTimeEquals(String actual, String expected) {
        if (!StringUtils.hasText(expected)) {
            return false;
        }
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
