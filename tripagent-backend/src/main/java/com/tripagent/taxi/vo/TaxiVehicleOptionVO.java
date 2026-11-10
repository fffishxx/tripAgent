package com.tripagent.taxi.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class TaxiVehicleOptionVO {

    private String vehicleType;

    private BigDecimal estimatedFare;
}
