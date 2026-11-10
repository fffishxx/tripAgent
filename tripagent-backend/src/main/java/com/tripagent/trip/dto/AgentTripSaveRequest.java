package com.tripagent.trip.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentTripSaveRequest {

    private String title;

    private LocalDate startDate;

    private LocalDate endDate;

    private String description;

    @Valid
    @NotNull
    private AgentPlanRequest plan;

    /**
     * 更新时必填；创建时忽略。
     */
    private Integer version;
}
