package com.tripagent.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FrontendPlanRequest {

    @NotBlank(message = "目的地不能为空")
    @Size(max = 64, message = "目的地不能超过64个字符")
    private String destination;

    @Valid
    @NotEmpty(message = "days 不能为空")
    private List<FrontendDayRequest> days;

    @Valid
    private PlanExtrasDTO.LodgingAreaDTO lodgingArea;

    @Valid
    @Size(max = 20, message = "diningSuggestions 最多20项")
    private List<PlanExtrasDTO.DiningSuggestionDTO> diningSuggestions;

    @Valid
    private PlanExtrasDTO.BudgetEstimateDTO budgetEstimate;

    @Valid
    private PlanExtrasDTO.AssumptionsDTO assumptions;

    @Size(max = 20, message = "validationStatus 不能超过20个字符")
    private String validationStatus;

    @Valid
    @Size(max = 50, message = "warnings 最多50项")
    private List<PlanExtrasDTO.WarningDTO> warnings;
}
