package com.tripagent.favorite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.favorite.dto.FavoriteAddRequest;
import com.tripagent.favorite.entity.FavoritePlace;
import com.tripagent.favorite.mapper.FavoritePlaceMapper;
import com.tripagent.favorite.model.FavoriteAddResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private static final String DEFAULT_SOURCE = "MANUAL";

    private final FavoritePlaceMapper favoritePlaceMapper;

    @Transactional(rollbackFor = Exception.class)
    public FavoriteAddResult add(Long userId, FavoriteAddRequest request) {
        String name = request.getName().trim();
        String externalPoiId = trimToNull(request.getExternalPoiId());
        boolean hasLongitude = request.getLongitude() != null;
        boolean hasLatitude = request.getLatitude() != null;

        if (hasLongitude != hasLatitude) {
            throw validation("经纬度必须同时提供或同时为空");
        }
        if (!StringUtils.hasText(externalPoiId) && !hasLongitude) {
            throw validation("外部 POI ID 与经纬度至少提供一种");
        }

        String placeKey = generatePlaceKey(
                externalPoiId, request.getCity(), name,
                request.getLongitude(), request.getLatitude());

        FavoritePlace existing = favoritePlaceMapper.selectAnyByUserAndKey(userId, placeKey);
        if (existing != null) {
            if (Integer.valueOf(0).equals(existing.getDeleted())) {
                return new FavoriteAddResult(existing, true);
            }
            fillSnapshot(existing, request, externalPoiId, name);
            favoritePlaceMapper.reviveById(existing);
            FavoritePlace revived = favoritePlaceMapper.selectById(existing.getId());
            return new FavoriteAddResult(revived == null ? existing : revived, false);
        }

        FavoritePlace favorite = new FavoritePlace();
        favorite.setUserId(userId);
        favorite.setPlaceKey(placeKey);
        favorite.setDeleted(0);
        fillSnapshot(favorite, request, externalPoiId, name);
        try {
            favoritePlaceMapper.insert(favorite);
            return new FavoriteAddResult(favorite, false);
        } catch (DuplicateKeyException e) {
            FavoritePlace duplicate = favoritePlaceMapper.selectAnyByUserAndKey(userId, placeKey);
            if (duplicate != null && Integer.valueOf(0).equals(duplicate.getDeleted())) {
                return new FavoriteAddResult(duplicate, true);
            }
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "收藏冲突，请重试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long favoriteId) {
        FavoritePlace favorite = favoritePlaceMapper.selectAnyById(favoriteId, userId);
        if (favorite == null) {
            throw new BusinessException(ResultCode.FAVORITE_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(favorite.getDeleted())) {
            return;
        }
        favoritePlaceMapper.deleteById(favoriteId);
    }

    public Page<FavoritePlace> list(Long userId, String city, long pageNum, long pageSize) {
        LambdaQueryWrapper<FavoritePlace> wrapper = Wrappers.<FavoritePlace>lambdaQuery()
                .eq(FavoritePlace::getUserId, userId);
        if (StringUtils.hasText(city)) {
            wrapper.eq(FavoritePlace::getCity, city.trim());
        }
        wrapper.orderByDesc(FavoritePlace::getCreateTime);
        return favoritePlaceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    private void fillSnapshot(FavoritePlace favorite,
                              FavoriteAddRequest request,
                              String externalPoiId,
                              String name) {
        favorite.setExternalPoiId(externalPoiId);
        favorite.setName(name);
        favorite.setCity(trimToNull(request.getCity()));
        favorite.setAddress(trimToNull(request.getAddress()));
        favorite.setLongitude(scale(request.getLongitude()));
        favorite.setLatitude(scale(request.getLatitude()));
        favorite.setCategory(trimToNull(request.getCategory()));
        favorite.setTypecode(trimToNull(request.getTypecode()));
        favorite.setNote(trimToNull(request.getNote()));
        favorite.setSource(StringUtils.hasText(request.getSource())
                ? request.getSource().trim() : DEFAULT_SOURCE);
    }

    private String generatePlaceKey(String externalPoiId,
                                    String city,
                                    String name,
                                    BigDecimal longitude,
                                    BigDecimal latitude) {
        if (StringUtils.hasText(externalPoiId)) {
            return "amap:" + externalPoiId;
        }
        String cityPart = trimToNull(city) == null ? "" : city.trim();
        String raw = cityPart + "|" + name + "|"
                + scale(longitude).toPlainString() + "|"
                + scale(latitude).toPlainString();
        return "geo:" + sha256Hex(raw);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }
}
