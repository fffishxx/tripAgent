package com.tripagent.taxi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentTaxiOrderCreateRequest {

    @Valid
    @NotNull(message = "pickup 不能为空")
    private TaxiPointRequest pickup;

    @Valid
    @NotNull(message = "destination 不能为空")
    private TaxiPointRequest destination;

    private String city;

    @NotBlank(message = "vehicle_type 不能为空")
    private String vehicleType;

    private String vehicleCategory;

    private Integer distanceM;

    private Integer durationMin;

    private BigDecimal estimatedFare;

    private BigDecimal couponFare;

    private String currency;

    /**
     * MOCK（默认）或 DIDI_MCP（可选）。
     */
    private String provider;

    private String providerEstimateId;

    private String jumpUrl;

    private Map<String, String> jumpLinks;
}
