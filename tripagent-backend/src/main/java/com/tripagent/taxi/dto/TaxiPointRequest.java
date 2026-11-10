package com.tripagent.taxi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxiPointRequest {

    @NotBlank(message = "地点名称不能为空")
    @Size(max = 128, message = "地点名称不能超过128个字符")
    private String name;

    @Size(max = 255, message = "地址不能超过255个字符")
    private String address;

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度必须在 -180~180 之间")
    @DecimalMax(value = "180.0", message = "经度必须在 -180~180 之间")
    private BigDecimal longitude;

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度必须在 -90~90 之间")
    @DecimalMax(value = "90.0", message = "纬度必须在 -90~90 之间")
    private BigDecimal latitude;
}
