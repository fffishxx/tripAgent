package com.tripagent.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public abstract class TripBaseRequest {

    @Size(max = 100, message = "行程名称不能超过100个字符")
    private String title;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "开始日期，必填；缺失返回 40002")
    private LocalDate startDate;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "结束日期，必填；缺失返回 40002")
    private LocalDate endDate;

    @Size(max = 1000, message = "行程描述不能超过1000个字符")
    private String description;

    @Valid
    @NotNull(message = "plan 不能为空")
    private FrontendPlanRequest plan;
}
