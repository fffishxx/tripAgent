package com.tripagent.dev;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DevValidateRequest {

    @NotBlank(message = "name 不能为空")
    private String name;

    @NotNull(message = "days 不能为空")
    @Min(value = 1, message = "days 必须大于 0")
    @Max(value = 30, message = "days 不能超过 30")
    private Integer days;
}
