package com.tripagent.trip.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.trip.entity.Trip;
import com.tripagent.trip.entity.TripDay;
import com.tripagent.trip.entity.TripItem;
import com.tripagent.trip.mapper.TripDayMapper;
import com.tripagent.trip.mapper.TripItemMapper;
import com.tripagent.trip.mapper.TripMapper;
import com.tripagent.trip.model.TripDayCommand;
import com.tripagent.trip.model.TripDetail;
import com.tripagent.trip.model.TripIdempotencyRow;
import com.tripagent.trip.model.TripItemCommand;
import com.tripagent.trip.model.TripSaveCommand;
import com.tripagent.trip.model.TripSaveResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TripService {

    private static final Set<String> ITEM_TYPES = Set.of(
            "ATTRACTION", "ACTIVITY", "MEAL", "HOTEL", "TRANSPORT", "OTHER");
    private static final Set<String> TIME_SLOTS = Set.of("上午", "中午", "下午", "晚上");
    private static final String DEFAULT_ITEM_TYPE = "ATTRACTION";

    private final TripMapper tripMapper;
    private final TripDayMapper tripDayMapper;
    private final TripItemMapper tripItemMapper;

    @Transactional(rollbackFor = Exception.class)
    public TripDetail create(Long userId, TripSaveCommand command, String source) {
        return doCreate(userId, command, source, null);
    }

    /**
     * Agent 创建行程：带 Idempotency-Key，重复请求返回首次结果。
     */
    /**
     * READ_COMMITTED 保证并发重复键冲突后，能读到另一个事务刚提交的幂等记录；
     * 默认 REPEATABLE READ 下普通 SELECT 仍使用旧快照，会误报“幂等请求冲突”。
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TripSaveResult createWithIdempotency(Long userId,
                                                TripSaveCommand command,
                                                String source,
                                                String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 64) {
            throw validation("Idempotency-Key 不合法");
        }

        TripIdempotencyRow existing = tripMapper.selectIdempotencyRow(userId, idempotencyKey);
        if (existing != null) {
            if (Integer.valueOf(1).equals(existing.getDeleted())) {
                throw new BusinessException(ResultCode.IDEMPOTENT_RESOURCE_DELETED);
            }
            return new TripSaveResult(get(userId, existing.getId()), true);
        }

        try {
            TripDetail detail = doCreate(userId, command, source, idempotencyKey);
            return new TripSaveResult(detail, false);
        } catch (DuplicateKeyException e) {
            TripIdempotencyRow duplicate = tripMapper.selectIdempotencyRow(userId, idempotencyKey);
            if (duplicate != null && Integer.valueOf(0).equals(duplicate.getDeleted())) {
                return new TripSaveResult(get(userId, duplicate.getId()), true);
            }
            if (duplicate != null) {
                throw new BusinessException(ResultCode.IDEMPOTENT_RESOURCE_DELETED);
            }
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "幂等请求冲突，请重试");
        }
    }

    private TripDetail doCreate(Long userId,
                                TripSaveCommand command,
                                String source,
                                String idempotencyKey) {
        List<TripDayCommand> days = validateAndSort(command);
        String destination = command.plan().destination().trim();

        Trip trip = new Trip();
        trip.setUserId(userId);
        trip.setTitle(resolveTitle(command.title(), destination, days.size()));
        trip.setDestination(destination);
        trip.setStartDate(command.startDate());
        trip.setEndDate(command.endDate());
        trip.setDaysCount(days.size());
        trip.setDescription(trimToNull(command.description()));
        trip.setSource(source);
        trip.setPlanExtras(command.plan().extras());
        trip.setIdempotencyKey(idempotencyKey);
        trip.setVersion(0);
        trip.setDeleted(0);
        tripMapper.insert(trip);

        return insertChildren(trip, days);
    }

    @Transactional(rollbackFor = Exception.class)
    public TripDetail update(Long userId, Long tripId, TripSaveCommand command) {
        Trip trip = getOwnedActive(userId, tripId);
        if (command.version() == null || !command.version().equals(trip.getVersion())) {
            throw new BusinessException(ResultCode.VERSION_CONFLICT);
        }

        List<TripDayCommand> days = validateAndSort(command);
        String destination = command.plan().destination().trim();
        trip.setTitle(resolveTitle(command.title(), destination, days.size()));
        trip.setDestination(destination);
        trip.setStartDate(command.startDate());
        trip.setEndDate(command.endDate());
        trip.setDaysCount(days.size());
        trip.setDescription(trimToNull(command.description()));
        trip.setPlanExtras(command.plan().extras());

        int updated = tripMapper.updateById(trip);
        if (updated != 1) {
            throw new BusinessException(ResultCode.VERSION_CONFLICT);
        }

        tripItemMapper.delete(Wrappers.<TripItem>lambdaQuery().eq(TripItem::getTripId, tripId));
        tripDayMapper.delete(Wrappers.<TripDay>lambdaQuery().eq(TripDay::getTripId, tripId));

        Trip refreshed = tripMapper.selectById(tripId);
        return insertChildren(refreshed, days);
    }

    public TripDetail get(Long userId, Long tripId) {
        Trip trip = getOwnedActive(userId, tripId);
        List<TripDay> days = tripDayMapper.selectList(Wrappers.<TripDay>lambdaQuery()
                .eq(TripDay::getTripId, tripId)
                .orderByAsc(TripDay::getDayIndex));
        List<TripItem> items = tripItemMapper.selectList(Wrappers.<TripItem>lambdaQuery()
                .eq(TripItem::getTripId, tripId)
                .orderByAsc(TripItem::getTripDayId)
                .orderByAsc(TripItem::getSequenceNo));
        return new TripDetail(trip, days, items);
    }

    public Page<Trip> list(Long userId, String keyword, long pageNum, long pageSize) {
        LambdaQueryWrapper<Trip> wrapper = Wrappers.<Trip>lambdaQuery()
                .eq(Trip::getUserId, userId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Trip::getTitle, kw).or().like(Trip::getDestination, kw));
        }
        wrapper.orderByDesc(Trip::getUpdateTime);
        return tripMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long tripId) {
        Trip trip = tripMapper.selectOne(Wrappers.<Trip>lambdaQuery()
                .eq(Trip::getId, tripId)
                .eq(Trip::getUserId, userId));
        if (trip != null) {
            tripMapper.deleteById(tripId);
            return;
        }

        Integer deletedFlag = tripMapper.selectDeletedFlag(tripId, userId);
        if (deletedFlag != null) {
            // 已删除，重复删除保持幂等
            return;
        }
        throw new BusinessException(ResultCode.TRIP_NOT_FOUND);
    }

    private TripDetail insertChildren(Trip trip, List<TripDayCommand> days) {
        List<TripDay> dayEntities = new ArrayList<>();
        List<TripItem> itemEntities = new ArrayList<>();

        for (int i = 0; i < days.size(); i++) {
            TripDayCommand dayCommand = days.get(i);
            TripDay day = new TripDay();
            day.setTripId(trip.getId());
            day.setDayIndex(i + 1);
            day.setDayDate(trip.getStartDate().plusDays(i));
            day.setTheme(trimToNull(dayCommand.theme()));
            tripDayMapper.insert(day);
            dayEntities.add(day);

            List<TripItemCommand> items = dayCommand.items() == null
                    ? List.of() : dayCommand.items();
            for (int j = 0; j < items.size(); j++) {
                TripItemCommand itemCommand = items.get(j);
                TripItem item = new TripItem();
                item.setTripId(trip.getId());
                item.setTripDayId(day.getId());
                item.setSequenceNo(j + 1);
                item.setItemType(defaultIfBlank(itemCommand.itemType(), DEFAULT_ITEM_TYPE));
                item.setName(itemCommand.name().trim());
                item.setAddress(trimToNull(itemCommand.address()));
                item.setLongitude(scale(itemCommand.longitude()));
                item.setLatitude(scale(itemCommand.latitude()));
                item.setTimeSlot(trimToNull(itemCommand.timeSlot()));
                item.setDurationMin(itemCommand.durationMin());
                item.setReason(trimToNull(itemCommand.reason()));
                item.setExternalPoiId(trimToNull(itemCommand.externalPoiId()));
                item.setNotes(trimToNull(itemCommand.notes()));
                tripItemMapper.insert(item);
                itemEntities.add(item);
            }
        }
        return new TripDetail(trip, dayEntities, itemEntities);
    }

    private List<TripDayCommand> validateAndSort(TripSaveCommand command) {
        if (command.startDate() == null || command.endDate() == null) {
            throw new BusinessException(ResultCode.TRIP_DATES_REQUIRED);
        }
        if (command.startDate().isAfter(command.endDate())) {
            throw validation("开始日期不能晚于结束日期");
        }
        if (command.plan() == null || !StringUtils.hasText(command.plan().destination())) {
            throw validation("目的地不能为空");
        }

        String destination = command.plan().destination().trim();
        checkMaxLength(destination, 64, "目的地");
        checkMaxLength(command.title(), 100, "行程名称");
        checkMaxLength(command.description(), 1000, "行程描述");

        if (command.plan().days() == null || command.plan().days().isEmpty()) {
            throw validation("行程天数不能为空");
        }

        long expectedDays = ChronoUnit.DAYS.between(command.startDate(), command.endDate()) + 1;
        if (expectedDays < 1) {
            throw validation("日期区间不合法");
        }
        if (command.plan().days().size() != expectedDays) {
            throw validation("行程天数与日期区间不一致");
        }

        List<TripDayCommand> days = new ArrayList<>(command.plan().days());
        days.sort(Comparator.comparing(TripDayCommand::dayIndex,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        int itemCount = 0;
        for (int i = 0; i < days.size(); i++) {
            TripDayCommand day = days.get(i);
            if (day.dayIndex() == null || day.dayIndex() != i + 1) {
                throw validation("dayIndex 必须从1开始连续");
            }
            checkMaxLength(day.theme(), 100, "当天主题");
            if (day.items() == null) {
                continue;
            }
            for (TripItemCommand item : day.items()) {
                validateItem(item);
                itemCount++;
            }
        }
        if (itemCount == 0) {
            throw validation("行程至少需要一条景点/活动安排");
        }
        return days;
    }

    private void validateItem(TripItemCommand item) {
        if (item == null || !StringUtils.hasText(item.name())) {
            throw validation("景点名称不能为空");
        }
        checkMaxLength(item.name().trim(), 128, "景点名称");
        checkMaxLength(item.address(), 255, "地址");
        checkMaxLength(item.reason(), 500, "安排理由");
        checkMaxLength(item.externalPoiId(), 64, "高德 POI ID");
        checkMaxLength(item.notes(), 500, "备注");

        if (item.durationMin() != null && (item.durationMin() < 1 || item.durationMin() > 1440)) {
            throw validation("durationMin 必须在 1~1440 之间");
        }
        if (StringUtils.hasText(item.timeSlot()) && !TIME_SLOTS.contains(item.timeSlot().trim())) {
            throw validation("timeSlot 取值不合法");
        }
        if (StringUtils.hasText(item.itemType()) && !ITEM_TYPES.contains(item.itemType().trim())) {
            throw validation("itemType 取值不合法");
        }

        boolean hasLongitude = item.longitude() != null;
        boolean hasLatitude = item.latitude() != null;
        if (hasLongitude != hasLatitude) {
            throw validation("经纬度必须同时提供或同时为空");
        }
        if (hasLongitude) {
            double longitude = item.longitude().doubleValue();
            double latitude = item.latitude().doubleValue();
            if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
                throw validation("经纬度超出范围");
            }
        }
    }

    private Trip getOwnedActive(Long userId, Long tripId) {
        Trip trip = tripMapper.selectOne(Wrappers.<Trip>lambdaQuery()
                .eq(Trip::getId, tripId)
                .eq(Trip::getUserId, userId));
        if (trip == null) {
            throw new BusinessException(ResultCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    private String resolveTitle(String title, String destination, int daysCount) {
        String resolved = StringUtils.hasText(title)
                ? title.trim()
                : destination + daysCount + "日游";
        checkMaxLength(resolved, 100, "行程名称");
        return resolved;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private void checkMaxLength(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw validation(field + "不能超过" + maxLength + "个字符");
        }
    }

    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
