package com.tripagent.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 32, message = "昵称不能超过32个字符")
    private String nickname;

    @Size(max = 512, message = "头像地址不能超过512个字符")
    private String avatarUrl;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过128个字符")
    private String email;
}
