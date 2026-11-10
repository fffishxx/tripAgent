package com.tripagent.trip.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FrontendTripDetailVO {

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

    private FrontendPlanVO plan;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean idempotentReplay;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
