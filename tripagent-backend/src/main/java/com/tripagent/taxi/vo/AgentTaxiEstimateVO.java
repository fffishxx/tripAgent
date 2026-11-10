package com.tripagent.taxi.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.tripagent.taxi.model.TaxiEstimateResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentTaxiEstimateVO {

    private Integer distanceM;

    private Integer durationMin;

    private String currency;

    private Boolean mock;

    private List<AgentTaxiVehicleOptionVO> vehicleOptions;

    public static AgentTaxiEstimateVO from(TaxiEstimateResult result) {
        List<AgentTaxiVehicleOptionVO> options = result.options().stream()
                .map(option -> new AgentTaxiVehicleOptionVO(
                        option.vehicleType(), option.estimatedFare()))
                .toList();
        return AgentTaxiEstimateVO.builder()
                .distanceM(result.distanceM())
                .durationMin(result.durationMin())
                .currency(result.currency())
                .mock(result.mock())
                .vehicleOptions(options)
                .build();
    }
}
