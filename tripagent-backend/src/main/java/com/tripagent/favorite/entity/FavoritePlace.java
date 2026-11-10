package com.tripagent.favorite.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("favorite_place")
public class FavoritePlace {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String placeKey;

    private String externalPoiId;

    private String name;

    private String city;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String category;

    private String typecode;

    private String note;

    private String source;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
