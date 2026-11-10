package com.tripagent.favorite.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.tripagent.favorite.entity.FavoritePlace;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FavoriteVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

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

    private Boolean duplicate;

    private LocalDateTime createTime;

    public static FavoriteVO from(FavoritePlace place, Boolean duplicate) {
        if (place == null) {
            return null;
        }
        return FavoriteVO.builder()
                .id(place.getId())
                .placeKey(place.getPlaceKey())
                .externalPoiId(place.getExternalPoiId())
                .name(place.getName())
                .city(place.getCity())
                .address(place.getAddress())
                .longitude(place.getLongitude())
                .latitude(place.getLatitude())
                .category(place.getCategory())
                .typecode(place.getTypecode())
                .note(place.getNote())
                .source(place.getSource())
                .duplicate(duplicate)
                .createTime(place.getCreateTime())
                .build();
    }
}
