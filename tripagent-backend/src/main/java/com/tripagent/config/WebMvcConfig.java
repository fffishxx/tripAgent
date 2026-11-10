package com.tripagent.config;

import com.tripagent.interceptor.AgentServiceInterceptor;
import com.tripagent.interceptor.OptionalUserAuthInterceptor;
import com.tripagent.interceptor.UserAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserAuthInterceptor userAuthInterceptor;
    private final OptionalUserAuthInterceptor optionalUserAuthInterceptor;
    private final AgentServiceInterceptor agentServiceInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 前端业务 API：必须登录
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/health",
                        "/api/v1/health/**",
                        "/api/v1/dev",
                        "/api/v1/dev/**",
                        "/api/agent/**",       // Agent 内部接口走 X-Service-Token 认证
                        "/api/v1/agent/**",    // Java 代理入口，允许匿名聊天
                        "/doc.html",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/favicon.ico",
                        "/error"
                );

        // Java -> Gateway -> Agent 代理：聊天/热门问题/匿名停止允许匿名
        registry.addInterceptor(optionalUserAuthInterceptor)
                .addPathPatterns("/api/v1/agent/**");

        // Python Agent -> Java 内部接口：服务 Token + 用户 Token
        registry.addInterceptor(agentServiceInterceptor)
                .addPathPatterns("/api/agent/**");
    }
}
