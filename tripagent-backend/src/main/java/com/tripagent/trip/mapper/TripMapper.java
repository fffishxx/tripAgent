package com.tripagent.trip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tripagent.trip.entity.Trip;
import com.tripagent.trip.model.TripIdempotencyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TripMapper extends BaseMapper<Trip> {

    /**
     * 查询包含已逻辑删除记录的 deleted 标记，用于幂等/重复删除判断。
     */
    @Select("SELECT deleted FROM trip WHERE id = #{id} AND user_id = #{userId} LIMIT 1")
    Integer selectDeletedFlag(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 按 (userId, idempotencyKey) 查询，包含已逻辑删除记录。
     */
    @Select("SELECT id, deleted FROM trip "
            + "WHERE user_id = #{userId} AND idempotency_key = #{key} LIMIT 1")
    TripIdempotencyRow selectIdempotencyRow(@Param("userId") Long userId,
                                            @Param("key") String key);
}
