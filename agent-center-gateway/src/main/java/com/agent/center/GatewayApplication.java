package com.agent.center;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

@Slf4j
@EnableScheduling
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) throws UnknownHostException {
        SpringApplication app = new SpringApplicationBuilder(GatewayApplication.class).build(args);
        Environment env = app.run(args).getEnvironment();
        String protocol = "http";
        if (env.getProperty("server.ssl.key-store") != null) {
            protocol = "https";
        }
        log.info("==================================================");
        log.info("网关服务启动完毕");
        log.info("服务名称：{}", env.getProperty("spring.application.name"));
        log.info("本机地址：{}://localhost:{}", protocol, env.getProperty("server.port"));
        log.info("外部地址：{}://{}:{}", protocol,
                InetAddress.getLocalHost().getHostAddress(), env.getProperty("server.port"));
        log.info("运行环境：{}", Arrays.toString(env.getActiveProfiles()));
        log.info("健康检查：{}://localhost:{}/health", protocol, env.getProperty("server.port"));
        log.info("==================================================");
    }
}
