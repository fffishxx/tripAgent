package com.tripagent.trip.vo;

import com.tripagent.trip.dto.PlanExtrasDTO;
import lombok.Data;

import java.util.List;

@Data
public class FrontendPlanVO {

    private String destination;

    private List<FrontendTripDayVO> days;

    private PlanExtrasDTO.LodgingAreaDTO lodgingArea;

    private List<PlanExtrasDTO.DiningSuggestionDTO> diningSuggestions;

    private PlanExtrasDTO.BudgetEstimateDTO budgetEstimate;

    private PlanExtrasDTO.AssumptionsDTO assumptions;

    private String validationStatus;

    private List<PlanExtrasDTO.WarningDTO> warnings;
}
