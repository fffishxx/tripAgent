package com.tripagent.trip.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FrontendItemRequest {

    @Pattern(regexp = "ATTRACTION|ACTIVITY|MEAL|HOTEL|TRANSPORT|OTHER",
            message = "itemType 取值不合法")
    private String itemType;

    @NotBlank(message = "景点名称不能为空")
    @Size(max = 128, message = "景点名称不能超过128个字符")
    private String name;

    @Size(max = 255, message = "地址不能超过255个字符")
    private String address;

    @DecimalMin(value = "-180.0", message = "经度必须在 -180~180 之间")
    @DecimalMax(value = "180.0", message = "经度必须在 -180~180 之间")
    private BigDecimal longitude;

    @DecimalMin(value = "-90.0", message = "纬度必须在 -90~90 之间")
    @DecimalMax(value = "90.0", message = "纬度必须在 -90~90 之间")
    private BigDecimal latitude;

    @Pattern(regexp = "上午|中午|下午|晚上", message = "timeSlot 取值不合法")
    private String timeSlot;

    @Min(value = 1, message = "durationMin 必须大于0")
    @Max(value = 1440, message = "durationMin 不能超过1440")
    private Integer durationMin;

    @Size(max = 500, message = "安排理由不能超过500个字符")
    private String reason;

    @Size(max = 64, message = "高德 POI ID 不能超过64个字符")
    private String externalPoiId;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String notes;
}
