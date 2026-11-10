package com.tripagent.taxi.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.taxi.constant.TaxiProvider;
import com.tripagent.taxi.constant.TaxiStatus;
import com.tripagent.taxi.dto.AgentTaxiOrderCreateRequest;
import com.tripagent.taxi.dto.TaxiOrderCreateRequest;
import com.tripagent.taxi.dto.TaxiPointRequest;
import com.tripagent.taxi.entity.TaxiOrder;
import com.tripagent.taxi.mapper.TaxiOrderMapper;
import com.tripagent.taxi.model.TaxiEstimateResult;
import com.tripagent.taxi.model.TaxiOrderSaveResult;
import com.tripagent.taxi.pricing.TaxiPricing;
import com.tripagent.support.MybatisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaxiServiceTest {

    private TaxiOrderMapper taxiOrderMapper;
    private TaxiPricing taxiPricing;
    private TaxiService taxiService;

    private TaxiEstimateResult estimate;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initTableInfo(TaxiOrder.class);
        taxiOrderMapper = mock(TaxiOrderMapper.class);
        taxiPricing = mock(TaxiPricing.class);
        taxiService = new TaxiService(taxiOrderMapper, taxiPricing);
        when(taxiOrderMapper.selectIdByIdempotencyKey(any(), any())).thenReturn(null);

        estimate = new TaxiEstimateResult(3000, 10, "CNY", true,
                List.of(new TaxiEstimateResult.VehicleOption("快车", new BigDecimal("25.00"))));
        when(taxiPricing.estimate(any(BigDecimal.class), any(BigDecimal.class),
                any(BigDecimal.class), any(BigDecimal.class))).thenReturn(estimate);
        when(taxiPricing.selectedFare(eq(estimate), eq("快车")))
                .thenReturn(new BigDecimal("25.00"));
        when(taxiOrderMapper.insert(any(TaxiOrder.class))).thenAnswer(invocation -> {
            TaxiOrder order = invocation.getArgument(0);
            order.setId(77L);
            return 1;
        });
    }

    @Test
    void estimateDelegatesToPricing() {
        assertThat(taxiService.estimate(estimateRequest()).distanceM()).isEqualTo(3000);
        verify(taxiPricing).estimate(any(BigDecimal.class), any(BigDecimal.class),
                any(BigDecimal.class), any(BigDecimal.class));
    }

    @Test
    void agentCreateBuildsMockOrder() {
        when(taxiOrderMapper.selectIdByIdempotencyKey(1L, "taxi-key")).thenReturn(null);

        TaxiOrderSaveResult result = taxiService.createForAgent(
                1L, orderRequest(TaxiProvider.MOCK), "taxi-key");

        assertThat(result.replay()).isFalse();
        TaxiOrder order = result.order();
        assertThat(order.getId()).isEqualTo(77L);
        assertThat(order.getStatus()).isEqualTo(TaxiStatus.CREATED);
        assertThat(order.getProvider()).isEqualTo(TaxiProvider.MOCK);
        assertThat(order.getVehicleType()).isEqualTo("快车");
        assertThat(order.getEstimatedFare()).isEqualByComparingTo("25.00");
        assertThat(order.getIdempotencyKey()).isEqualTo("taxi-key");
        assertThat(order.getOrderNo()).startsWith("TX");
        verify(taxiOrderMapper).insert(any(TaxiOrder.class));
    }

    @Test
    void agentCreateReplaysExistingOrder() {
        when(taxiOrderMapper.selectIdByIdempotencyKey(1L, "taxi-key")).thenReturn(5L);
        when(taxiOrderMapper.selectOne(any())).thenReturn(order(5L, TaxiStatus.CREATED));

        TaxiOrderSaveResult result = taxiService.createForAgent(
                1L, orderRequest(TaxiProvider.MOCK), "taxi-key");

        assertThat(result.replay()).isTrue();
        assertThat(result.order().getId()).isEqualTo(5L);
        verify(taxiOrderMapper, never()).insert(any(TaxiOrder.class));
    }

    @Test
    void agentCreateRejectsInvalidProvider() {
        AgentTaxiOrderCreateRequest request = orderRequest("UBER");

        assertCode(() -> taxiService.createForAgent(1L, request, "taxi-key"),
                ResultCode.VALIDATION_ERROR);
        verify(taxiOrderMapper, never()).insert(any(TaxiOrder.class));
    }

    @Test
    void agentCreateRejectsDidiWithoutJumpUrl() {
        AgentTaxiOrderCreateRequest request = orderRequest(TaxiProvider.DIDI_MCP);
        request.setJumpUrl(null);

        assertCode(() -> taxiService.createForAgent(1L, request, "taxi-key"),
                ResultCode.VALIDATION_ERROR);
    }

    @Test
    void frontendCreateWithIdempotencyBuildsMockOrder() {
        when(taxiOrderMapper.selectIdByIdempotencyKey(1L, "front-key")).thenReturn(null);

        TaxiOrderSaveResult result = taxiService.createWithIdempotency(
                1L, frontendRequest(), "front-key");

        assertThat(result.replay()).isFalse();
        TaxiOrder order = result.order();
        assertThat(order.getId()).isEqualTo(77L);
        assertThat(order.getStatus()).isEqualTo(TaxiStatus.CREATED);
        assertThat(order.getProvider()).isEqualTo(TaxiProvider.MOCK);
        assertThat(order.getVehicleType()).isEqualTo("快车");
        assertThat(order.getEstimatedFare()).isEqualByComparingTo("25.00");
        assertThat(order.getIdempotencyKey()).isEqualTo("front-key");
        verify(taxiOrderMapper).insert(any(TaxiOrder.class));
    }

    @Test
    void frontendCreateWithIdempotencyReplaysExistingOrder() {
        when(taxiOrderMapper.selectIdByIdempotencyKey(1L, "front-key")).thenReturn(5L);
        when(taxiOrderMapper.selectOne(any())).thenReturn(order(5L, TaxiStatus.CREATED));

        TaxiOrderSaveResult result = taxiService.createWithIdempotency(
                1L, frontendRequest(), "front-key");

        assertThat(result.replay()).isTrue();
        assertThat(result.order().getId()).isEqualTo(5L);
        verify(taxiOrderMapper, never()).insert(any(TaxiOrder.class));
    }

    @Test
    void frontendCreateWithIdempotencyRejectsBlankKey() {
        assertCode(() -> taxiService.createWithIdempotency(1L, frontendRequest(), " "),
                ResultCode.VALIDATION_ERROR);
        verify(taxiOrderMapper, never()).insert(any(TaxiOrder.class));
    }

    @Test
    void cancelCreatedOrder() {
        TaxiOrder order = order(5L, TaxiStatus.CREATED);
        when(taxiOrderMapper.selectOne(any())).thenReturn(order);
        when(taxiOrderMapper.update(isNull(), any())).thenReturn(1);
        TaxiOrder cancelled = order(5L, TaxiStatus.CANCELLED);
        when(taxiOrderMapper.selectById(5L)).thenReturn(cancelled);

        TaxiOrder result = taxiService.cancel(1L, 5L, "不去了");

        assertThat(result.getStatus()).isEqualTo(TaxiStatus.CANCELLED);
        verify(taxiOrderMapper).update(isNull(), any());
    }

    @Test
    void cancelIsIdempotentWhenAlreadyCancelled() {
        TaxiOrder order = order(5L, TaxiStatus.CANCELLED);
        when(taxiOrderMapper.selectOne(any())).thenReturn(order);

        TaxiOrder result = taxiService.cancel(1L, 5L, null);

        assertThat(result.getStatus()).isEqualTo(TaxiStatus.CANCELLED);
        verify(taxiOrderMapper, never()).update(any(), any());
    }

    @Test
    void cancelCompletedOrderConflicts() {
        when(taxiOrderMapper.selectOne(any())).thenReturn(order(5L, TaxiStatus.COMPLETED));

        assertCode(() -> taxiService.cancel(1L, 5L, null), ResultCode.ORDER_STATE_CONFLICT);
    }

    @Test
    void foreignOrderIsNotFound() {
        when(taxiOrderMapper.selectOne(any())).thenReturn(null);

        assertCode(() -> taxiService.get(2L, 5L), ResultCode.ORDER_NOT_FOUND);
        assertCode(() -> taxiService.cancel(2L, 5L, null), ResultCode.ORDER_NOT_FOUND);
    }

    @Test
    void completeCancelledOrderConflicts() {
        when(taxiOrderMapper.selectOne(any())).thenReturn(order(5L, TaxiStatus.CANCELLED));

        assertCode(() -> taxiService.complete(1L, 5L), ResultCode.ORDER_STATE_CONFLICT);
    }

    @Test
    void completeCreatedOrder() {
        when(taxiOrderMapper.selectOne(any())).thenReturn(order(5L, TaxiStatus.CREATED));
        when(taxiOrderMapper.update(isNull(), any())).thenReturn(1);
        when(taxiOrderMapper.selectById(5L)).thenReturn(order(5L, TaxiStatus.COMPLETED));

        TaxiOrder result = taxiService.complete(1L, 5L);

        assertThat(result.getStatus()).isEqualTo(TaxiStatus.COMPLETED);
    }

    @Test
    void listRejectsInvalidStatus() {
        assertCode(() -> taxiService.list(1L, "UNKNOWN", 1, 10), ResultCode.VALIDATION_ERROR);
    }

    private AgentTaxiOrderCreateRequest orderRequest(String provider) {
        AgentTaxiOrderCreateRequest request = new AgentTaxiOrderCreateRequest();
        request.setPickup(point("西湖"));
        request.setDestination(point("灵隐寺"));
        request.setVehicleType("快车");
        request.setProvider(provider);
        return request;
    }

    private TaxiOrderCreateRequest frontendRequest() {
        TaxiOrderCreateRequest request = new TaxiOrderCreateRequest();
        request.setPickup(point("西湖"));
        request.setDestination(point("灵隐寺"));
        request.setVehicleType("快车");
        request.setCity("杭州");
        return request;
    }

    private com.tripagent.taxi.dto.TaxiEstimateRequest estimateRequest() {
        com.tripagent.taxi.dto.TaxiEstimateRequest request =
                new com.tripagent.taxi.dto.TaxiEstimateRequest();
        request.setPickup(point("西湖"));
        request.setDestination(point("灵隐寺"));
        return request;
    }

    private TaxiPointRequest point(String name) {
        TaxiPointRequest point = new TaxiPointRequest();
        point.setName(name);
        point.setLongitude(new BigDecimal("120.100000"));
        point.setLatitude(new BigDecimal("30.200000"));
        return point;
    }

    private TaxiOrder order(Long id, String status) {
        TaxiOrder order = new TaxiOrder();
        order.setId(id);
        order.setUserId(1L);
        order.setStatus(status);
        order.setOrderNo("TX" + id);
        order.setVehicleType("快车");
        order.setEstimatedFare(new BigDecimal("25.00"));
        order.setProvider(TaxiProvider.MOCK);
        order.setVersion(0);
        return order;
    }

    private void assertCode(Runnable runnable, ResultCode expected) {
        BusinessException ex = catchThrowableOfType(runnable::run, BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getResultCode()).isEqualTo(expected);
    }
}
