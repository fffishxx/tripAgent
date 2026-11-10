package com.tripagent.taxi.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "taxi_order", autoResultMap = true)
public class TaxiOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;

    private Long userId;

    private String status;

    private String pickupName;

    private String pickupAddress;

    private BigDecimal pickupLongitude;

    private BigDecimal pickupLatitude;

    private String destinationName;

    private String destinationAddress;

    private BigDecimal destinationLongitude;

    private BigDecimal destinationLatitude;

    private String city;

    private String vehicleType;

    private String vehicleCategory;

    private Integer distanceM;

    private Integer durationMin;

    private BigDecimal estimatedFare;

    private BigDecimal couponFare;

    private String currency;

    private String provider;

    private String providerEstimateId;

    private String jumpUrl;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> jumpLinks;

    private String requestId;

    private String idempotencyKey;

    private String cancelReason;

    private LocalDateTime cancelledTime;

    private LocalDateTime completedTime;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
