package com.tripagent.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应 tripagent.user 表。
 *
 * <p>手机号是登录标识；username 为预留字段，可为空。</p>
 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String phone;

    private String passwordHash;

    private String nickname;

    private String username;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String avatarUrl;

    private Integer status;

    private LocalDateTime lastLoginTime;

    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
