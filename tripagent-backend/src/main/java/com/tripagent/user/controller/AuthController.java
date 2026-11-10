package com.tripagent.user.controller;

import com.tripagent.common.result.Result;
import com.tripagent.user.dto.LoginRequest;
import com.tripagent.user.dto.RegisterRequest;
import com.tripagent.user.service.UserService;
import com.tripagent.user.vo.LoginVO;
import com.tripagent.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "用户认证", description = "手机号注册与登录")
@SecurityRequirements
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "注册", description = "手机号 + 密码注册，自动创建默认偏好")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "手机号 + 密码登录，返回用户 JWT")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }
}
