package com.tripagent.trip.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.tripagent.trip.entity.PlanExtras;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentPlanRequest {

    private String destination;

    @Valid
    @NotEmpty
    private List<AgentDayRequest> days;

    private PlanExtras.LodgingArea lodgingArea;

    @Valid
    @Size(max = 20)
    private List<PlanExtras.DiningSuggestion> diningSuggestions;

    private PlanExtras.BudgetEstimate budgetEstimate;

    private PlanExtras.Assumptions assumptions;

    private String validationStatus;

    @Valid
    @Size(max = 50)
    private List<PlanExtras.Warning> warnings;
}
