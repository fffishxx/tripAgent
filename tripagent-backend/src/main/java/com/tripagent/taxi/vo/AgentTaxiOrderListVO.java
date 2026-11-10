package com.tripagent.taxi.vo;

import lombok.Data;

import java.util.List;

@Data
public class AgentTaxiOrderListVO {

    private List<AgentTaxiOrderVO> orders;

    private long total;
}
