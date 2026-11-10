package com.tripagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python Agent 调 Java 时使用的服务身份密钥。
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.agent")
public class AgentServiceProperties {

    private String serviceToken = "";
}
