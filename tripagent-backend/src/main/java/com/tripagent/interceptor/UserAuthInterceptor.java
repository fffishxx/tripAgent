package com.tripagent.interceptor;

import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
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

/**
 * 用户 JWT 拦截器：解析 Bearer Token，写入 UserContext。
 */
@Component
@RequiredArgsConstructor
public class UserAuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader(CommonConstants.AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith(CommonConstants.BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少登录凭证");
        }

        String token = authorization.substring(CommonConstants.BEARER_PREFIX.length()).trim();
        Long userId = jwtService.parseUserId(token);
        UserContextHolder.set(new UserContext(userId, null, null));

        request.setAttribute(CommonConstants.REQUEST_USER_ID_ATTR, userId);
        request.setAttribute(CommonConstants.REQUEST_USER_TOKEN_ATTR, token);
        MDC.put(CommonConstants.MDC_USER_ID, String.valueOf(userId));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        UserContextHolder.clear();
        MDC.remove(CommonConstants.MDC_USER_ID);
    }
}
