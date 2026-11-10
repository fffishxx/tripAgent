package com.tripagent.trip.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 行程级扩展信息，以 snake_case JSON 存入 trip.plan_extras。
 */
@Data
public class PlanExtras {

    @JsonProperty("lodging_area")
    private LodgingArea lodgingArea;

    @JsonProperty("dining_suggestions")
    private List<DiningSuggestion> diningSuggestions;

    @JsonProperty("budget_estimate")
    private BudgetEstimate budgetEstimate;

    private Assumptions assumptions;

    @JsonProperty("validation_status")
    private String validationStatus;

    private List<Warning> warnings;

    @Data
    public static class LodgingArea {
        private String name;
        private String reason;
    }

    @Data
    public static class DiningSuggestion {
        private String area;
        private String cuisine;
        private String note;
    }

    @Data
    public static class BudgetEstimate {
        private String total;
        private String breakdown;
    }

    @Data
    public static class Assumptions {
        private Integer days;
        private String crowd;
        @JsonProperty("budget_level")
        private String budgetLevel;
        private List<String> notes;
    }

    @Data
    public static class Warning {
        @JsonProperty("day_index")
        private Integer dayIndex;
        private String from;
        private String to;
        @JsonProperty("walk_min")
        private Integer walkMin;
        @JsonProperty("distance_km")
        private BigDecimal distanceKm;
        private String message;
    }
}
