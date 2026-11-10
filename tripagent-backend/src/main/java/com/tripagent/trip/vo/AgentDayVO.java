package com.tripagent.trip.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentDayVO {

    private Integer dayIndex;

    private String theme;

    private List<AgentItemVO> items;
}
