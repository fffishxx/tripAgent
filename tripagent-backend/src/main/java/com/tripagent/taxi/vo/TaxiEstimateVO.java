package com.tripagent.taxi.vo;

import com.tripagent.taxi.model.TaxiEstimateResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TaxiEstimateVO {

    private Integer distanceM;

    private Integer durationMin;

    private String currency;

    private Boolean mock;

    private List<TaxiVehicleOptionVO> vehicleOptions;

    public static TaxiEstimateVO from(TaxiEstimateResult result) {
        List<TaxiVehicleOptionVO> options = result.options().stream()
                .map(option -> new TaxiVehicleOptionVO(
                        option.vehicleType(), option.estimatedFare()))
                .toList();
        return TaxiEstimateVO.builder()
                .distanceM(result.distanceM())
                .durationMin(result.durationMin())
                .currency(result.currency())
                .mock(result.mock())
                .vehicleOptions(options)
                .build();
    }
}
