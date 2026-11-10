package com.tripagent.preference.controller;

import com.tripagent.common.result.Result;
import com.tripagent.context.UserContextHolder;
import com.tripagent.preference.dto.PreferenceUpdateRequest;
import com.tripagent.preference.service.PreferenceService;
import com.tripagent.preference.vo.PreferenceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
@Tag(name = "用户偏好", description = "用户出行偏好查询与更新")
public class PreferenceController {

    private final PreferenceService preferenceService;

    @GetMapping
    @Operation(summary = "查询当前用户偏好")
    public Result<PreferenceVO> get() {
        return Result.success(PreferenceVO.from(
                preferenceService.getOrDefault(UserContextHolder.getRequiredUserId())));
    }

    @PutMapping
    @Operation(summary = "更新当前用户偏好")
    public Result<PreferenceVO> update(@Valid @RequestBody PreferenceUpdateRequest request) {
        return Result.success(PreferenceVO.from(
                preferenceService.update(UserContextHolder.getRequiredUserId(), request)));
    }
}
