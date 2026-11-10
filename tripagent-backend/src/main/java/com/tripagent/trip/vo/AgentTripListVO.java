package com.tripagent.trip.vo;

import lombok.Data;

import java.util.List;

@Data
public class AgentTripListVO {

    private List<AgentTripSummaryVO> trips;

    private long total;
}
