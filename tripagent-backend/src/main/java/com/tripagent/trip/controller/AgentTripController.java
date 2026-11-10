package com.tripagent.trip.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.Result;
import com.tripagent.common.result.ResultCode;
import com.tripagent.context.UserContextHolder;
import com.tripagent.trip.converter.TripConverter;
import com.tripagent.trip.dto.AgentTripSaveRequest;
import com.tripagent.trip.entity.Trip;
import com.tripagent.trip.model.TripSaveCommand;
import com.tripagent.trip.model.TripSaveResult;
import com.tripagent.trip.service.TripService;
import com.tripagent.trip.vo.AgentTripDetailVO;
import com.tripagent.trip.vo.AgentTripListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Python Agent 使用的行程接口（snake_case），需要用户 JWT + X-Service-Token。
 */
@RestController
@RequestMapping("/api/agent/v1/trips")
@RequiredArgsConstructor
@Tag(name = "Agent 行程", description = "Python Agent 保存/查询/修改/删除行程")
public class AgentTripController {

    private final TripService tripService;

    @PostMapping
    @Operation(summary = "保存行程（Agent）", description = "必须携带 Idempotency-Key；重复请求返回首次结果")
    public ResponseEntity<Result<AgentTripDetailVO>> create(
            @RequestHeader(CommonConstants.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody AgentTripSaveRequest request) {
        Long userId = UserContextHolder.getRequiredUserId();
        TripSaveCommand command = TripConverter.toCommand(request);
        TripSaveResult result = tripService.createWithIdempotency(
                userId, command, "AGENT", idempotencyKey);
        AgentTripDetailVO vo = TripConverter.toAgentDetailVO(result.detail(), result.replay());
        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(Result.success(vo));
    }

    @GetMapping
    @Operation(summary = "查询最近行程（Agent）")
    public Result<AgentTripListVO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") long limit) {
        Long userId = UserContextHolder.getRequiredUserId();
        long size = limit <= 0 ? 10 : Math.min(limit, 20);
        Page<Trip> page = tripService.list(userId, keyword, 1, size);
        AgentTripListVO list = new AgentTripListVO();
        list.setTrips(page.getRecords().stream()
                .map(TripConverter::toAgentSummaryVO)
                .toList());
        list.setTotal(page.getTotal());
        return Result.success(list);
    }

    @GetMapping("/{tripId}")
    @Operation(summary = "查询行程详情（Agent）")
    public Result<AgentTripDetailVO> detail(@PathVariable Long tripId) {
        Long userId = UserContextHolder.getRequiredUserId();
        return Result.success(TripConverter.toAgentDetailVO(
                tripService.get(userId, tripId), false));
    }

    @PutMapping("/{tripId}")
    @Operation(summary = "修改行程（Agent）", description = "整单替换，必须携带 version")
    public Result<AgentTripDetailVO> update(@PathVariable Long tripId,
                                            @Valid @RequestBody AgentTripSaveRequest request) {
        if (request.getVersion() == null) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "version 不能为空");
        }
        Long userId = UserContextHolder.getRequiredUserId();
        TripSaveCommand command = TripConverter.toCommand(request);
        return Result.success(TripConverter.toAgentDetailVO(
                tripService.update(userId, tripId, command), false));
    }

    @DeleteMapping("/{tripId}")
    @Operation(summary = "删除行程（Agent）", description = "逻辑删除，重复删除保持幂等")
    public Result<Map<String, String>> delete(@PathVariable Long tripId) {
        Long userId = UserContextHolder.getRequiredUserId();
        tripService.delete(userId, tripId);
        return Result.success(Map.of("trip_id", String.valueOf(tripId)));
    }
}
