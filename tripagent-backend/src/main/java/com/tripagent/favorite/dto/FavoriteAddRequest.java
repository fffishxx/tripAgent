package com.tripagent.favorite.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FavoriteAddRequest {

    @Size(max = 64, message = "高德 POI ID 不能超过64个字符")
    private String externalPoiId;

    @NotBlank(message = "地点名称不能为空")
    @Size(max = 128, message = "地点名称不能超过128个字符")
    private String name;

    @Size(max = 64, message = "城市不能超过64个字符")
    private String city;

    @Size(max = 255, message = "地址不能超过255个字符")
    private String address;

    @DecimalMin(value = "-180.0", message = "经度必须在 -180~180 之间")
    @DecimalMax(value = "180.0", message = "经度必须在 -180~180 之间")
    private BigDecimal longitude;

    @DecimalMin(value = "-90.0", message = "纬度必须在 -90~90 之间")
    @DecimalMax(value = "90.0", message = "纬度必须在 -90~90 之间")
    private BigDecimal latitude;

    @Size(max = 64, message = "地点类型不能超过64个字符")
    private String category;

    @Size(max = 20, message = "高德类型码不能超过20个字符")
    private String typecode;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String note;

    @Pattern(regexp = "TRIP|SEARCH|MANUAL", message = "source 取值不合法")
    private String source;
}
