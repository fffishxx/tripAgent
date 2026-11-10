package com.tripagent.common.controller;

import com.tripagent.common.result.Result;
import com.tripagent.user.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 阶段二基础设施健康检查；数据库检查通过 MyBatis-Plus 实际查询完成。
 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "系统健康", description = "启动状态与数据库连通性检查")
public class HealthController {

    private final UserMapper userMapper;

    @GetMapping
    @Operation(summary = "应用健康检查")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("application", "tripagent-backend");
        return Result.success(data);
    }

    @GetMapping("/db")
    @Operation(summary = "数据库连通性检查", description = "通过 MyBatis-Plus 查询用户数量")
    public Result<Map<String, Object>> dbHealth() {
        long userCount = userMapper.selectCount(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("database", "UP");
        data.put("userCount", userCount);
        return Result.success(data);
    }
}
