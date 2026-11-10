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
 * 可选用户认证：用于聊天、热门问题、匿名停止。
 *
 * <p>无 Authorization 时按匿名放行；带了 Authorization 就必须是有效用户 JWT，
 * 不会把“无效 Token”静默降级为匿名。</p>
 */
@Component
@RequiredArgsConstructor
public class OptionalUserAuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader(CommonConstants.AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization)) {
            UserContextHolder.clear();
            return true;
        }
        if (!authorization.startsWith(CommonConstants.BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录凭证格式不正确");
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
