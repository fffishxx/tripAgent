package com.tripagent.trip.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FrontendTripSummaryVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer daysCount;

    private String source;

    private Integer version;

    private LocalDateTime updateTime;
}
