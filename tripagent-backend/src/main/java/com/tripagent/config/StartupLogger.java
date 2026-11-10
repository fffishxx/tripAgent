package com.tripagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动/失败日志（中文）。
 */
@Slf4j
@Component
public class StartupLogger {

    @Value("${server.port:8080}")
    private String port;

    @Value("${spring.profiles.active:default}")
    private String profile;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("==================================================");
        log.info("服务启动完毕");
        log.info("服务名称：tripagent-backend");
        log.info("运行环境：{}", profile);
        log.info("服务端口：{}", port);
        log.info("健康检查：http://localhost:{}/api/v1/health", port);
        log.info("接口文档：http://localhost:{}/doc.html", port);
        log.info("==================================================");
    }

    @EventListener(ApplicationFailedEvent.class)
    public void onFailed(ApplicationFailedEvent event) {
        log.error("服务启动失败", event.getException());
    }
}
