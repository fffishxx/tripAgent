package com.tripagent.trip.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentTripDetailVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer daysCount;

    private String description;

    private String source;

    private Integer version;

    private AgentPlanVO plan;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean idempotentReplay;
}
