package com.agent.center.fallback;

import cn.hutool.json.JSONUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class FallbackController {

    @RequestMapping("/fallback/ac")
    public Mono<ResponseEntity<String>> courseFallback() {
        var message = Map.of(
                "status", "503",
                "message", "AgentCenter service is unavailable now, please try later.");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(JSONUtil.toJsonStr(message)));
    }
}