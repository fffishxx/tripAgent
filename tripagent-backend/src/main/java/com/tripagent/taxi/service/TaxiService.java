package com.tripagent.taxi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.taxi.constant.TaxiProvider;
import com.tripagent.taxi.constant.TaxiStatus;
import com.tripagent.taxi.dto.AgentTaxiOrderCreateRequest;
import com.tripagent.taxi.dto.TaxiEstimateRequest;
import com.tripagent.taxi.dto.TaxiOrderCreateRequest;
import com.tripagent.taxi.dto.TaxiPointRequest;
import com.tripagent.taxi.entity.TaxiOrder;
import com.tripagent.taxi.mapper.TaxiOrderMapper;
import com.tripagent.taxi.model.TaxiEstimateResult;
import com.tripagent.taxi.model.TaxiOrderSaveResult;
import com.tripagent.taxi.pricing.TaxiPricing;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class TaxiService {

    private static final DateTimeFormatter ORDER_NO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String CURRENCY_CNY = "CNY";

    private final TaxiOrderMapper taxiOrderMapper;
    private final TaxiPricing taxiPricing;

    public TaxiEstimateResult estimate(TaxiEstimateRequest request) {
        return taxiPricing.estimate(
                request.getPickup().getLongitude(),
                request.getPickup().getLatitude(),
                request.getDestination().getLongitude(),
                request.getDestination().getLatitude());
    }

    @Transactional(rollbackFor = Exception.class)
    public TaxiOrder create(Long userId, TaxiOrderCreateRequest request) {
        TaxiOrder order = buildMockOrder(
                userId,
                request.getPickup(),
                request.getDestination(),
                request.getCity(),
                request.getVehicleType());
        return insertWithOrderNoRetry(order);
    }

    /**
     * Agent 创建订单：Java 模拟为主，DIDI_MCP 可选；必须有 Idempotency-Key。
     *
     * <p>与行程创建相同：READ_COMMITTED 让并发重复键冲突后的查询能看到已提交的幂等记录。</p>
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TaxiOrderSaveResult createForAgent(Long userId,
                                              AgentTaxiOrderCreateRequest request,
                                              String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);

        Long existingId = taxiOrderMapper.selectIdByIdempotencyKey(userId, idempotencyKey);
        if (existingId != null) {
            return new TaxiOrderSaveResult(get(userId, existingId), true);
        }

        String provider = normalizeProvider(request.getProvider());
        TaxiOrder order = TaxiProvider.DIDI_MCP.equals(provider)
                ? buildDidiOrder(userId, request)
                : buildMockOrder(userId, request.getPickup(), request.getDestination(),
                request.getCity(), request.getVehicleType());
        return insertWithIdempotency(userId, order, idempotencyKey);
    }

    /**
     * 前端创建订单（可选 Idempotency-Key）：固定 MOCK 主流程，复用 Agent 的幂等索引与回查。
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TaxiOrderSaveResult createWithIdempotency(Long userId,
                                                     TaxiOrderCreateRequest request,
                                                     String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);

        Long existingId = taxiOrderMapper.selectIdByIdempotencyKey(userId, idempotencyKey);
        if (existingId != null) {
            return new TaxiOrderSaveResult(get(userId, existingId), true);
        }

        TaxiOrder order = buildMockOrder(
                userId, request.getPickup(), request.getDestination(),
                request.getCity(), request.getVehicleType());
        return insertWithIdempotency(userId, order, idempotencyKey);
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 64) {
            throw validation("Idempotency-Key 不合法");
        }
    }

    private TaxiOrderSaveResult insertWithIdempotency(Long userId,
                                                      TaxiOrder order,
                                                      String idempotencyKey) {
        order.setIdempotencyKey(idempotencyKey);

        for (int attempt = 0; attempt < 3; attempt++) {
            order.setId(null);
            order.setOrderNo(generateOrderNo());
            try {
                taxiOrderMapper.insert(order);
                return new TaxiOrderSaveResult(order, false);
            } catch (DuplicateKeyException e) {
                Long duplicateId = taxiOrderMapper.selectIdByIdempotencyKey(userId, idempotencyKey);
                if (duplicateId != null) {
                    return new TaxiOrderSaveResult(get(userId, duplicateId), true);
                }
                if (attempt == 2) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成订单号失败，请重试");
                }
            }
        }
        throw new BusinessException(ResultCode.INTERNAL_ERROR, "创建订单失败");
    }

    public TaxiOrder get(Long userId, Long orderId) {
        return getOwned(userId, orderId);
    }

    public Page<TaxiOrder> list(Long userId, String status, long pageNum, long pageSize) {
        LambdaQueryWrapper<TaxiOrder> wrapper = Wrappers.<TaxiOrder>lambdaQuery()
                .eq(TaxiOrder::getUserId, userId);
        if (StringUtils.hasText(status)) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if (!TaxiStatus.isValid(normalized)) {
                throw validation("status 取值不合法");
            }
            wrapper.eq(TaxiOrder::getStatus, normalized);
        }
        wrapper.orderByDesc(TaxiOrder::getCreateTime);
        return taxiOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public TaxiOrder cancel(Long userId, Long orderId, String reason) {
        TaxiOrder order = getOwned(userId, orderId);
        if (TaxiStatus.CANCELLED.equals(order.getStatus())) {
            return order;
        }
        if (TaxiStatus.COMPLETED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATE_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = taxiOrderMapper.update(null, Wrappers.<TaxiOrder>lambdaUpdate()
                .eq(TaxiOrder::getId, orderId)
                .eq(TaxiOrder::getUserId, userId)
                .eq(TaxiOrder::getStatus, TaxiStatus.CREATED)
                .set(TaxiOrder::getStatus, TaxiStatus.CANCELLED)
                .set(TaxiOrder::getCancelReason, trimToNull(reason))
                .set(TaxiOrder::getCancelledTime, now)
                .set(TaxiOrder::getUpdateTime, now)
                .setSql("version = version + 1"));

        if (updated == 0) {
            TaxiOrder latest = getOwned(userId, orderId);
            if (TaxiStatus.CANCELLED.equals(latest.getStatus())) {
                return latest;
            }
            throw new BusinessException(ResultCode.ORDER_STATE_CONFLICT);
        }
        return taxiOrderMapper.selectById(orderId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TaxiOrder complete(Long userId, Long orderId) {
        TaxiOrder order = getOwned(userId, orderId);
        if (TaxiStatus.COMPLETED.equals(order.getStatus())) {
            return order;
        }
        if (TaxiStatus.CANCELLED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATE_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = taxiOrderMapper.update(null, Wrappers.<TaxiOrder>lambdaUpdate()
                .eq(TaxiOrder::getId, orderId)
                .eq(TaxiOrder::getUserId, userId)
                .eq(TaxiOrder::getStatus, TaxiStatus.CREATED)
                .set(TaxiOrder::getStatus, TaxiStatus.COMPLETED)
                .set(TaxiOrder::getCompletedTime, now)
                .set(TaxiOrder::getUpdateTime, now)
                .setSql("version = version + 1"));

        if (updated == 0) {
            TaxiOrder latest = getOwned(userId, orderId);
            if (TaxiStatus.COMPLETED.equals(latest.getStatus())) {
                return latest;
            }
            throw new BusinessException(ResultCode.ORDER_STATE_CONFLICT);
        }
        return taxiOrderMapper.selectById(orderId);
    }

    private TaxiOrder getOwned(Long userId, Long orderId) {
        TaxiOrder order = taxiOrderMapper.selectOne(Wrappers.<TaxiOrder>lambdaQuery()
                .eq(TaxiOrder::getId, orderId)
                .eq(TaxiOrder::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private TaxiOrder insertWithOrderNoRetry(TaxiOrder order) {
        for (int attempt = 0; attempt < 3; attempt++) {
            order.setId(null);
            order.setOrderNo(generateOrderNo());
            try {
                taxiOrderMapper.insert(order);
                return order;
            } catch (DuplicateKeyException e) {
                if (attempt == 2) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成订单号失败，请重试");
                }
            }
        }
        throw new BusinessException(ResultCode.INTERNAL_ERROR, "创建订单失败");
    }

    private TaxiOrder buildMockOrder(Long userId,
                                     TaxiPointRequest pickup,
                                     TaxiPointRequest destination,
                                     String city,
                                     String vehicleType) {
        TaxiEstimateResult estimate = taxiPricing.estimate(
                pickup.getLongitude(), pickup.getLatitude(),
                destination.getLongitude(), destination.getLatitude());
        String selectedVehicle = vehicleType.trim();
        BigDecimal fare = taxiPricing.selectedFare(estimate, selectedVehicle);

        TaxiOrder order = newOrder(userId, pickup, destination, city, selectedVehicle);
        order.setDistanceM(estimate.distanceM());
        order.setDurationMin(estimate.durationMin());
        order.setEstimatedFare(fare);
        order.setProvider(TaxiProvider.MOCK);
        return order;
    }

    private TaxiOrder buildDidiOrder(Long userId, AgentTaxiOrderCreateRequest request) {
        String jumpUrl = trimToNull(request.getJumpUrl());
        if (jumpUrl == null) {
            throw validation("DIDI_MCP 订单需要 jump_url");
        }

        TaxiOrder order = newOrder(
                userId, request.getPickup(), request.getDestination(),
                request.getCity(), request.getVehicleType());
        order.setProvider(TaxiProvider.DIDI_MCP);
        order.setVehicleCategory(trimToNull(request.getVehicleCategory()));
        order.setDistanceM(request.getDistanceM());
        order.setDurationMin(request.getDurationMin());
        order.setEstimatedFare(request.getEstimatedFare());
        order.setCouponFare(request.getCouponFare());
        order.setCurrency(StringUtils.hasText(request.getCurrency())
                ? request.getCurrency().trim() : CURRENCY_CNY);
        order.setProviderEstimateId(trimToNull(request.getProviderEstimateId()));
        order.setJumpUrl(jumpUrl);
        order.setJumpLinks(request.getJumpLinks());
        return order;
    }

    private TaxiOrder newOrder(Long userId,
                               TaxiPointRequest pickup,
                               TaxiPointRequest destination,
                               String city,
                               String vehicleType) {
        TaxiOrder order = new TaxiOrder();
        order.setUserId(userId);
        order.setStatus(TaxiStatus.CREATED);
        order.setPickupName(pickup.getName().trim());
        order.setPickupAddress(trimToNull(pickup.getAddress()));
        order.setPickupLongitude(scale(pickup.getLongitude()));
        order.setPickupLatitude(scale(pickup.getLatitude()));
        order.setDestinationName(destination.getName().trim());
        order.setDestinationAddress(trimToNull(destination.getAddress()));
        order.setDestinationLongitude(scale(destination.getLongitude()));
        order.setDestinationLatitude(scale(destination.getLatitude()));
        order.setCity(trimToNull(city));
        order.setVehicleType(vehicleType.trim());
        order.setCurrency(CURRENCY_CNY);
        order.setRequestId(MDC.get(CommonConstants.MDC_REQUEST_ID));
        order.setVersion(0);
        return order;
    }

    private String normalizeProvider(String provider) {
        String normalized = StringUtils.hasText(provider)
                ? provider.trim().toUpperCase(Locale.ROOT)
                : TaxiProvider.MOCK;
        if (!TaxiProvider.isValid(normalized)) {
            throw validation("provider 取值不合法");
        }
        return normalized;
    }

    private String generateOrderNo() {
        return "TX" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
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
