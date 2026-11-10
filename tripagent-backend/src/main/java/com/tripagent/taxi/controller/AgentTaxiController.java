package com.tripagent.taxi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.result.Result;
import com.tripagent.context.UserContextHolder;
import com.tripagent.taxi.dto.AgentTaxiEstimateRequest;
import com.tripagent.taxi.dto.AgentTaxiOrderCreateRequest;
import com.tripagent.taxi.dto.TaxiCancelRequest;
import com.tripagent.taxi.dto.TaxiEstimateRequest;
import com.tripagent.taxi.entity.TaxiOrder;
import com.tripagent.taxi.model.TaxiEstimateResult;
import com.tripagent.taxi.model.TaxiOrderSaveResult;
import com.tripagent.taxi.service.TaxiService;
import com.tripagent.taxi.vo.AgentTaxiEstimateVO;
import com.tripagent.taxi.vo.AgentTaxiOrderListVO;
import com.tripagent.taxi.vo.AgentTaxiOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Python Agent 使用的模拟打车接口（snake_case）。
 */
@RestController
@RequestMapping("/api/agent/v1/taxi")
@RequiredArgsConstructor
@Tag(name = "Agent 打车", description = "Python Agent 估价、下单、查询与取消")
public class AgentTaxiController {

    private final TaxiService taxiService;

    @PostMapping("/estimates")
    @Operation(summary = "模拟费用预估（Agent）")
    public Result<AgentTaxiEstimateVO> estimate(
            @Valid @RequestBody AgentTaxiEstimateRequest request) {
        TaxiEstimateRequest frontendRequest = new TaxiEstimateRequest();
        frontendRequest.setPickup(request.getPickup());
        frontendRequest.setDestination(request.getDestination());
        frontendRequest.setCity(request.getCity());
        frontendRequest.setVehicleType(request.getVehicleType());
        TaxiEstimateResult result = taxiService.estimate(frontendRequest);
        return Result.success(AgentTaxiEstimateVO.from(result));
    }

    @PostMapping("/orders")
    @Operation(summary = "创建订单（Agent）", description = "必须携带 Idempotency-Key；重复请求返回首次结果")
    public ResponseEntity<Result<AgentTaxiOrderVO>> create(
            @RequestHeader(CommonConstants.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody AgentTaxiOrderCreateRequest request) {
        TaxiOrderSaveResult result = taxiService.createForAgent(
                UserContextHolder.getRequiredUserId(), request, idempotencyKey);
        AgentTaxiOrderVO vo = AgentTaxiOrderVO.from(result.order(), result.replay());
        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(Result.success(vo));
    }

    @GetMapping("/orders")
    @Operation(summary = "查询最近订单（Agent）")
    public Result<AgentTaxiOrderListVO> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "5") long limit) {
        long size = limit <= 0 ? 5 : Math.min(limit, 20);
        Page<TaxiOrder> page = taxiService.list(
                UserContextHolder.getRequiredUserId(), status, 1, size);
        AgentTaxiOrderListVO list = new AgentTaxiOrderListVO();
        list.setOrders(page.getRecords().stream()
                .map(order -> AgentTaxiOrderVO.from(order, false))
                .toList());
        list.setTotal(page.getTotal());
        return Result.success(list);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "查询订单详情（Agent）")
    public Result<AgentTaxiOrderVO> detail(@PathVariable Long orderId) {
        TaxiOrder order = taxiService.get(
                UserContextHolder.getRequiredUserId(), orderId);
        return Result.success(AgentTaxiOrderVO.from(order, false));
    }

    @PostMapping("/orders/{orderId}/cancel")
    @Operation(summary = "取消订单（Agent）", description = "已取消幂等返回；已完成返回 40903")
    public Result<AgentTaxiOrderVO> cancel(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) TaxiCancelRequest request) {
        String reason = request == null ? null : request.getReason();
        TaxiOrder order = taxiService.cancel(
                UserContextHolder.getRequiredUserId(), orderId, reason);
        return Result.success(AgentTaxiOrderVO.from(order, false));
    }
}
