package com.tripagent.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FrontendDayRequest {

    @NotNull(message = "dayIndex 不能为空")
    private Integer dayIndex;

    @Size(max = 100, message = "当天主题不能超过100个字符")
    private String theme;

    @Valid
    @NotNull(message = "items 不能为空")
    private List<FrontendItemRequest> items;
}
