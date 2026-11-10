package com.tripagent.user.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.tripagent.user.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String phone;

    private String nickname;

    private String email;

    private String avatarUrl;

    private Integer status;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    public static UserVO from(User user) {
        return UserVO.builder()
                .userId(user.getId())
                .phone(user.getPhone())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .lastLoginTime(user.getLastLoginTime())
                .createTime(user.getCreateTime())
                .build();
    }
}
