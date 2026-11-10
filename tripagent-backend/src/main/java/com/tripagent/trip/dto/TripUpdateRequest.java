package com.tripagent.trip.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TripUpdateRequest extends TripBaseRequest {

    @NotNull(message = "version 不能为空")
    private Integer version;
}
