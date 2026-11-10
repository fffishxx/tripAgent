package com.tripagent.trip.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.result.PageResult;
import com.tripagent.common.result.Result;
import com.tripagent.context.UserContextHolder;
import com.tripagent.trip.converter.TripConverter;
import com.tripagent.trip.dto.TripCreateRequest;
import com.tripagent.trip.dto.TripUpdateRequest;
import com.tripagent.trip.entity.Trip;
import com.tripagent.trip.model.TripDetail;
import com.tripagent.trip.model.TripSaveCommand;
import com.tripagent.trip.model.TripSaveResult;
import com.tripagent.trip.service.TripService;
import com.tripagent.trip.vo.FrontendTripDetailVO;
import com.tripagent.trip.vo.FrontendTripSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@Tag(name = "行程管理", description = "创建、查询、修改、删除行程")
public class TripController {

    private final TripService tripService;

    @PostMapping
    @Operation(summary = "创建行程", description = "整单创建；日期必填，天数必须与日期区间一致；可选 Idempotency-Key")
    public ResponseEntity<Result<FrontendTripDetailVO>> create(
            @RequestHeader(value = CommonConstants.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            @Valid @RequestBody TripCreateRequest request) {
        Long userId = UserContextHolder.getRequiredUserId();
        TripSaveCommand command = TripConverter.toCommand(request, null);

        if (StringUtils.hasText(idempotencyKey)) {
            TripSaveResult result = tripService.createWithIdempotency(
                    userId, command, "MANUAL", idempotencyKey);
            FrontendTripDetailVO vo = TripConverter.toDetailVO(result.detail());
            vo.setIdempotentReplay(result.replay());
            HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
            return ResponseEntity.status(status).body(Result.success(vo));
        }

        TripDetail detail = tripService.create(userId, command, "MANUAL");
        FrontendTripDetailVO vo = TripConverter.toDetailVO(detail);
        vo.setIdempotentReplay(false);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(vo));
    }

    @GetMapping
    @Operation(summary = "行程列表", description = "按更新时间倒序分页")
    public Result<PageResult<FrontendTripSummaryVO>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        Long userId = UserContextHolder.getRequiredUserId();
        long current = Math.max(pageNum, 1);
        long size = pageSize <= 0
                ? CommonConstants.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, CommonConstants.MAX_PAGE_SIZE);

        Page<Trip> page = tripService.list(userId, keyword, current, size);
        List<FrontendTripSummaryVO> records = page.getRecords().stream()
                .map(TripConverter::toSummaryVO)
                .toList();
        return Result.success(new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/{tripId}")
    @Operation(summary = "行程详情")
    public Result<FrontendTripDetailVO> detail(@PathVariable Long tripId) {
        Long userId = UserContextHolder.getRequiredUserId();
        return Result.success(TripConverter.toDetailVO(tripService.get(userId, tripId)));
    }

    @PutMapping("/{tripId}")
    @Operation(summary = "修改行程", description = "整单替换，需携带当前 version；版本冲突返回 40902")
    public Result<FrontendTripDetailVO> update(@PathVariable Long tripId,
                                               @Valid @RequestBody TripUpdateRequest request) {
        Long userId = UserContextHolder.getRequiredUserId();
        TripSaveCommand command = TripConverter.toCommand(request, request.getVersion());
        return Result.success(TripConverter.toDetailVO(tripService.update(userId, tripId, command)));
    }

    @DeleteMapping("/{tripId}")
    @Operation(summary = "删除行程", description = "逻辑删除，重复删除保持幂等")
    public Result<Map<String, String>> delete(@PathVariable Long tripId) {
        Long userId = UserContextHolder.getRequiredUserId();
        tripService.delete(userId, tripId);
        return Result.success(Map.of("tripId", String.valueOf(tripId)));
    }
}
