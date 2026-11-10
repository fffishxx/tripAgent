package com.tripagent.trip.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("trip_item")
public class TripItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tripId;

    private Long tripDayId;

    private Integer sequenceNo;

    private String itemType;

    private String name;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String timeSlot;

    private Integer durationMin;

    private String reason;

    private String externalPoiId;

    private String notes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
