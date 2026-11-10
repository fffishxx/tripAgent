package com.tripagent.taxi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tripagent.taxi.entity.TaxiOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaxiOrderMapper extends BaseMapper<TaxiOrder> {

    @Select("SELECT id FROM taxi_order "
            + "WHERE user_id = #{userId} AND idempotency_key = #{key} LIMIT 1")
    Long selectIdByIdempotencyKey(@Param("userId") Long userId,
                                  @Param("key") String key);
}
