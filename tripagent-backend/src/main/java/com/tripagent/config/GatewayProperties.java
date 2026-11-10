package com.tripagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Java 调用 Gateway 的配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private String baseUrl = "http://127.0.0.1:10086";

    /**
     * 与 Gateway 的 gateway.internal-token 保持一致。
     */
    private String internalToken = "";
}
