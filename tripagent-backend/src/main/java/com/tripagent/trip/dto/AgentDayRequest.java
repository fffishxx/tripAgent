package com.tripagent.trip.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentDayRequest {

    private Integer dayIndex;

    private String theme;

    @Valid
    private List<AgentItemRequest> items;
}
