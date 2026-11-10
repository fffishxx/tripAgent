package com.tripagent.preference.controller;

import com.tripagent.common.result.Result;
import com.tripagent.context.UserContextHolder;
import com.tripagent.preference.service.PreferenceService;
import com.tripagent.preference.vo.AgentPreferenceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/v1/preferences")
@RequiredArgsConstructor
@Tag(name = "Agent 偏好", description = "Python Agent 查询当前用户偏好")
public class AgentPreferenceController {

    private final PreferenceService preferenceService;

    @GetMapping
    @Operation(summary = "查询当前用户偏好（Agent）")
    public Result<AgentPreferenceVO> get() {
        return Result.success(AgentPreferenceVO.from(
                preferenceService.getOrDefault(UserContextHolder.getRequiredUserId())));
    }
}
