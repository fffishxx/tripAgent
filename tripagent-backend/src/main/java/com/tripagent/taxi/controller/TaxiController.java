package com.tripagent.taxi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.result.PageResult;
import com.tripagent.common.result.Result;
import com.tripagent.context.UserContextHolder;
import com.tripagent.taxi.dto.TaxiCancelRequest;
import com.tripagent.taxi.dto.TaxiEstimateRequest;
import com.tripagent.taxi.dto.TaxiOrderCreateRequest;
import com.tripagent.taxi.entity.TaxiOrder;
import com.tripagent.taxi.model.TaxiEstimateResult;
import com.tripagent.taxi.model.TaxiOrderSaveResult;
import com.tripagent.taxi.service.TaxiService;
import com.tripagent.taxi.vo.TaxiEstimateVO;
import com.tripagent.taxi.vo.TaxiOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/taxi")
@RequiredArgsConstructor
@Tag(name = "模拟打车", description = "模拟估价、订单创建、查询、取消与完成")
public class TaxiController {

    private final TaxiService taxiService;

    @PostMapping("/estimates")
    @Operation(summary = "模拟费用预估", description = "Java 模拟计价，返回车型与预估价格")
    public Result<TaxiEstimateVO> estimate(@Valid @RequestBody TaxiEstimateRequest request) {
        TaxiEstimateResult result = taxiService.estimate(request);
        return Result.success(TaxiEstimateVO.from(result));
    }

    @PostMapping("/orders")
    @Operation(summary = "创建模拟订单", description = "服务端重新计算距离与价格，provider=MOCK；可选 Idempotency-Key")
    public ResponseEntity<Result<TaxiOrderVO>> create(
            @RequestHeader(value = CommonConstants.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            @Valid @RequestBody TaxiOrderCreateRequest request) {
        Long userId = UserContextHolder.getRequiredUserId();

        if (StringUtils.hasText(idempotencyKey)) {
            TaxiOrderSaveResult result = taxiService.createWithIdempotency(
                    userId, request, idempotencyKey);
            TaxiOrderVO vo = TaxiOrderVO.from(result.order());
            vo.setIdempotentReplay(result.replay());
            HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
            return ResponseEntity.status(status).body(Result.success(vo));
        }

        TaxiOrder order = taxiService.create(userId, request);
        TaxiOrderVO vo = TaxiOrderVO.from(order);
        vo.setIdempotentReplay(false);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(vo));
    }

    @GetMapping("/orders")
    @Operation(summary = "订单列表")
    public Result<PageResult<TaxiOrderVO>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status) {
        Long userId = UserContextHolder.getRequiredUserId();
        long current = Math.max(pageNum, 1);
        long size = pageSize <= 0
                ? CommonConstants.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, CommonConstants.MAX_PAGE_SIZE);

        Page<TaxiOrder> page = taxiService.list(userId, status, current, size);
        List<TaxiOrderVO> records = page.getRecords().stream()
                .map(TaxiOrderVO::from)
                .toList();
        return Result.success(new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "订单详情")
    public Result<TaxiOrderVO> detail(@PathVariable Long orderId) {
        TaxiOrder order = taxiService.get(UserContextHolder.getRequiredUserId(), orderId);
        return Result.success(TaxiOrderVO.from(order));
    }

    @PostMapping("/orders/{orderId}/cancel")
    @Operation(summary = "取消订单", description = "已取消幂等返回；已完成返回 40903")
    public Result<TaxiOrderVO> cancel(@PathVariable Long orderId,
                                      @Valid @RequestBody(required = false) TaxiCancelRequest request) {
        String reason = request == null ? null : request.getReason();
        TaxiOrder order = taxiService.cancel(
                UserContextHolder.getRequiredUserId(), orderId, reason);
        return Result.success(TaxiOrderVO.from(order));
    }

    @PostMapping("/orders/{orderId}/complete")
    @Operation(summary = "模拟完成订单", description = "仅演示使用；已完成幂等返回，已取消返回 40903")
    public Result<TaxiOrderVO> complete(@PathVariable Long orderId) {
        TaxiOrder order = taxiService.complete(
                UserContextHolder.getRequiredUserId(), orderId);
        return Result.success(TaxiOrderVO.from(order));
    }
}
