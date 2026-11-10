package com.tripagent.trip.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FrontendTripItemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private Integer sequenceNo;

    private String itemType;

    private String name;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String timeSlot;

    private Integer durationMin;

    private String reason;

    private String externalPoiId;

    private String notes;
}
