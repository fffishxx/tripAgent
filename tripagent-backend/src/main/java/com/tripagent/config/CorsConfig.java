package com.tripagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 可选的 CORS 白名单。
 *
 * <p>默认不开启（生产推荐同源反向代理）；只有显式配置
 * security.cors.allowed-origins 时才注册 CORS，避免默认放开跨域。</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String allowedOrigins;

    public CorsConfig(@Value("${security.cors.allowed-origins:}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!StringUtils.hasText(allowedOrigins)) {
            return;
        }
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        if (origins.length == 0) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders(
                        "Authorization",
                        "Content-Type",
                        "X-Request-Id",
                        "Idempotency-Key")
                .exposedHeaders("X-Request-Id")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
