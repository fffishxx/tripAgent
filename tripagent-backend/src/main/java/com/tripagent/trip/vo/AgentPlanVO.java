package com.tripagent.trip.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.tripagent.trip.entity.PlanExtras;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentPlanVO {

    private String destination;

    private List<AgentDayVO> days;

    private PlanExtras.LodgingArea lodgingArea;

    private List<PlanExtras.DiningSuggestion> diningSuggestions;

    private PlanExtras.BudgetEstimate budgetEstimate;

    private PlanExtras.Assumptions assumptions;

    private String validationStatus;

    private List<PlanExtras.Warning> warnings;
}
