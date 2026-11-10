package com.tripagent.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tripagent.favorite.entity.FavoritePlace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FavoritePlaceMapper extends BaseMapper<FavoritePlace> {

    /**
     * 包含已逻辑删除记录，用于重复收藏/恢复。
     */
    @Select("SELECT * FROM favorite_place WHERE user_id = #{userId} AND place_key = #{placeKey} LIMIT 1")
    FavoritePlace selectAnyByUserAndKey(@Param("userId") Long userId,
                                        @Param("placeKey") String placeKey);

    /**
     * 包含已逻辑删除记录，用于幂等删除判断。
     */
    @Select("SELECT * FROM favorite_place WHERE id = #{id} AND user_id = #{userId} LIMIT 1")
    FavoritePlace selectAnyById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 恢复已删除的收藏并刷新快照与时间。
     */
    @Update("UPDATE favorite_place SET "
            + "external_poi_id = #{f.externalPoiId}, name = #{f.name}, city = #{f.city}, "
            + "address = #{f.address}, longitude = #{f.longitude}, latitude = #{f.latitude}, "
            + "category = #{f.category}, typecode = #{f.typecode}, note = #{f.note}, "
            + "source = #{f.source}, deleted = 0, create_time = NOW(), update_time = NOW() "
            + "WHERE id = #{f.id}")
    int reviveById(@Param("f") FavoritePlace favorite);
}
