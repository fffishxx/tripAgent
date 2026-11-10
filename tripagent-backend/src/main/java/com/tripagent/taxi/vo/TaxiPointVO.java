package com.tripagent.taxi.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TaxiPointVO {

    private String name;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;
}
