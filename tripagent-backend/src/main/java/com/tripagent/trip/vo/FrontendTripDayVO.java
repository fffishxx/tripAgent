package com.tripagent.trip.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class FrontendTripDayVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private Integer dayIndex;

    private LocalDate dayDate;

    private String theme;

    private List<FrontendTripItemVO> items;
}
