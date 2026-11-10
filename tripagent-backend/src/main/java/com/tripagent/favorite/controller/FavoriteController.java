package com.tripagent.favorite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.result.PageResult;
import com.tripagent.common.result.Result;
import com.tripagent.context.UserContextHolder;
import com.tripagent.favorite.dto.FavoriteAddRequest;
import com.tripagent.favorite.entity.FavoritePlace;
import com.tripagent.favorite.model.FavoriteAddResult;
import com.tripagent.favorite.service.FavoriteService;
import com.tripagent.favorite.vo.FavoriteVO;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
@Tag(name = "景点收藏", description = "收藏、取消收藏、收藏列表")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    @Operation(summary = "添加收藏", description = "已收藏时返回已有记录，duplicate=true")
    public ResponseEntity<Result<FavoriteVO>> add(@Valid @RequestBody FavoriteAddRequest request) {
        FavoriteAddResult result = favoriteService.add(UserContextHolder.getRequiredUserId(), request);
        FavoriteVO vo = FavoriteVO.from(result.favorite(), result.duplicate());
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(Result.success(vo));
    }

    @DeleteMapping("/{favoriteId}")
    @Operation(summary = "取消收藏", description = "逻辑删除，重复删除保持幂等")
    public Result<Map<String, String>> delete(@PathVariable Long favoriteId) {
        favoriteService.delete(UserContextHolder.getRequiredUserId(), favoriteId);
        return Result.success(Map.of("favoriteId", String.valueOf(favoriteId)));
    }

    @GetMapping
    @Operation(summary = "收藏列表")
    public Result<PageResult<FavoriteVO>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String city) {
        Long userId = UserContextHolder.getRequiredUserId();
        long current = Math.max(pageNum, 1);
        long size = pageSize <= 0
                ? CommonConstants.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, CommonConstants.MAX_PAGE_SIZE);

        Page<FavoritePlace> page = favoriteService.list(userId, city, current, size);
        List<FavoriteVO> records = page.getRecords().stream()
                .map(place -> FavoriteVO.from(place, null))
                .toList();
        return Result.success(new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize()));
    }
}
