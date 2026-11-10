package com.tripagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户 JWT 配置。
 *
 * <p>私钥默认不放在仓库内；空白时从 ~/.tripagent/keys/user-jwt-private.pem 读取。
 * 公钥默认从 classpath:keys/user-jwt-public.pem 读取。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.user-jwt")
public class UserJwtProperties {

    private String privateKey = "";
    private String publicKey = "";
    private String issuer = "tripagent-java";
    private String audience = "tripagent";
    private long expireHours = 24;
}
