package com.tripagent.preference.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "user_preference", autoResultMap = true)
public class UserPreference {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String crowd;

    private String budgetLevel;

    private String preferredTransport;

    private String travelPace;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> interests;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> foodPreferences;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String accommodationPreference;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String notes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
