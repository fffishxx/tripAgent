package com.tripagent.user.controller;

import com.tripagent.common.result.Result;
import com.tripagent.context.UserContextHolder;
import com.tripagent.user.dto.UpdateProfileRequest;
import com.tripagent.user.service.UserService;
import com.tripagent.user.vo.UserVO;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "用户信息", description = "当前用户资料")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "当前用户信息")
    public Result<UserVO> me() {
        return Result.success(userService.getProfile(UserContextHolder.getRequiredUserId()));
    }

    @PutMapping("/me")
    @Operation(summary = "修改当前用户资料", description = "只允许修改昵称、头像、邮箱")
    public Result<UserVO> update(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userService.updateProfile(
                UserContextHolder.getRequiredUserId(), request));
    }
}
