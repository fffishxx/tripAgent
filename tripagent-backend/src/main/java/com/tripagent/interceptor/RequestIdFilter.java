package com.tripagent.interceptor;

import com.tripagent.common.constant.CommonConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 为每个请求生成/透传 X-Request-Id，写入 MDC 与响应头，并记录访问日志。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(CommonConstants.REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId) || !REQUEST_ID_PATTERN.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        MDC.put(CommonConstants.MDC_REQUEST_ID, requestId);
        MDC.put(CommonConstants.MDC_USER_ID, CommonConstants.DEFAULT_LOG_VALUE);
        MDC.put(CommonConstants.MDC_AGENT_ID, CommonConstants.DEFAULT_LOG_VALUE);
        response.setHeader(CommonConstants.REQUEST_ID_HEADER, requestId);

        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            // 拦截器 afterCompletion 会先清掉 MDC，因此访问日志从 request attribute 读取
            String userId = attribute(request, CommonConstants.REQUEST_USER_ID_ATTR);
            String agentId = attribute(request, CommonConstants.REQUEST_AGENT_ID_ATTR);
            log.info("请求完成：{} {}，状态码={}，耗时={}毫秒，用户ID={}，智能体ID={}",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), elapsedMs, userId, agentId);

            MDC.remove(CommonConstants.MDC_REQUEST_ID);
            MDC.remove(CommonConstants.MDC_USER_ID);
            MDC.remove(CommonConstants.MDC_AGENT_ID);
        }
    }

    private String attribute(HttpServletRequest request, String key) {
        Object value = request.getAttribute(key);
        return value == null ? CommonConstants.DEFAULT_LOG_VALUE : String.valueOf(value);
    }
}
