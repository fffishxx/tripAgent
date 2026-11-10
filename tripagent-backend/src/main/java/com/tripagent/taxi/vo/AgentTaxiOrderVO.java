package com.tripagent.taxi.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.tripagent.taxi.constant.TaxiStatus;
import com.tripagent.taxi.entity.TaxiOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentTaxiOrderVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String orderNo;

    private String status;

    private String statusText;

    private TaxiPointVO pickup;

    private TaxiPointVO destination;

    private String city;

    private String vehicleType;

    private String vehicleCategory;

    private Integer distanceM;

    private Integer durationMin;

    private BigDecimal estimatedFare;

    private BigDecimal couponFare;

    private String currency;

    private String provider;

    private String jumpUrl;

    private Map<String, String> jumpLinks;

    private String cancelReason;

    private LocalDateTime cancelledTime;

    private LocalDateTime completedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean idempotentReplay;

    public static AgentTaxiOrderVO from(TaxiOrder order, boolean replay) {
        if (order == null) {
            return null;
        }
        AgentTaxiOrderVO vo = new AgentTaxiOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusText(TaxiStatus.text(order.getStatus()));
        vo.setPickup(TaxiPointVO.builder()
                .name(order.getPickupName())
                .address(order.getPickupAddress())
                .longitude(order.getPickupLongitude())
                .latitude(order.getPickupLatitude())
                .build());
        vo.setDestination(TaxiPointVO.builder()
                .name(order.getDestinationName())
                .address(order.getDestinationAddress())
                .longitude(order.getDestinationLongitude())
                .latitude(order.getDestinationLatitude())
                .build());
        vo.setCity(order.getCity());
        vo.setVehicleType(order.getVehicleType());
        vo.setVehicleCategory(order.getVehicleCategory());
        vo.setDistanceM(order.getDistanceM());
        vo.setDurationMin(order.getDurationMin());
        vo.setEstimatedFare(order.getEstimatedFare());
        vo.setCouponFare(order.getCouponFare());
        vo.setCurrency(order.getCurrency());
        vo.setProvider(order.getProvider());
        vo.setJumpUrl(order.getJumpUrl());
        vo.setJumpLinks(order.getJumpLinks());
        vo.setCancelReason(order.getCancelReason());
        vo.setCancelledTime(order.getCancelledTime());
        vo.setCompletedTime(order.getCompletedTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());
        vo.setIdempotentReplay(replay);
        return vo;
    }
}
