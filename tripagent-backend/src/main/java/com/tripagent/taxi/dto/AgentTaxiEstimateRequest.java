package com.tripagent.taxi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentTaxiEstimateRequest {

    @Valid
    @NotNull(message = "pickup 不能为空")
    private TaxiPointRequest pickup;

    @Valid
    @NotNull(message = "destination 不能为空")
    private TaxiPointRequest destination;

    private String city;

    private String vehicleType;
}
