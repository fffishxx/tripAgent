package com.tripagent.taxi.model;

import java.math.BigDecimal;
import java.util.List;

public record TaxiEstimateResult(
        int distanceM,
        int durationMin,
        String currency,
        boolean mock,
        List<VehicleOption> options
) {
    public record VehicleOption(String vehicleType, BigDecimal estimatedFare) {
    }
}
