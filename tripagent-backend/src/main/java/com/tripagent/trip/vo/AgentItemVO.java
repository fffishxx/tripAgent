package com.tripagent.trip.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentItemVO {

    private String poi;

    private List<BigDecimal> coords;

    private String itemType;

    private String address;

    private String timeSlot;

    private String reason;

    private Integer durationMin;

    private String externalPoiId;

    private String notes;
}
