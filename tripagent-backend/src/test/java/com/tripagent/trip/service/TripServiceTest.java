package com.tripagent.trip.service;

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
import com.tripagent.trip.model.TripPlanCommand;
import com.tripagent.trip.model.TripSaveCommand;
import com.tripagent.trip.model.TripSaveResult;
import com.tripagent.support.MybatisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripServiceTest {

    private TripMapper tripMapper;
    private TripDayMapper tripDayMapper;
    private TripItemMapper tripItemMapper;
    private TripService tripService;

    private final LocalDate start = LocalDate.of(2026, 11, 10);
    private final LocalDate end = LocalDate.of(2026, 11, 11);

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initTableInfo(Trip.class, TripDay.class, TripItem.class);
        tripMapper = org.mockito.Mockito.mock(TripMapper.class);
        tripDayMapper = org.mockito.Mockito.mock(TripDayMapper.class);
        tripItemMapper = org.mockito.Mockito.mock(TripItemMapper.class);
        tripService = new TripService(tripMapper, tripDayMapper, tripItemMapper);

        AtomicLong dayId = new AtomicLong(200);
        AtomicLong itemId = new AtomicLong(300);
        when(tripMapper.insert(any(Trip.class))).thenAnswer(invocation -> {
            Trip trip = invocation.getArgument(0);
            if (trip.getId() == null) {
                trip.setId(100L);
            }
            return 1;
        });
        when(tripDayMapper.insert(any(TripDay.class))).thenAnswer(invocation -> {
            TripDay day = invocation.getArgument(0);
            day.setId(dayId.incrementAndGet());
            return 1;
        });
        when(tripItemMapper.insert(any(TripItem.class))).thenAnswer(invocation -> {
            TripItem item = invocation.getArgument(0);
            item.setId(itemId.incrementAndGet());
            return 1;
        });
        when(tripDayMapper.delete(any())).thenReturn(1);
        when(tripItemMapper.delete(any())).thenReturn(1);
    }

    @Test
    void createPersistsTripAndChildren() {
        TripSaveCommand command = command(2, 1, "上午", "ATTRACTION",
                new BigDecimal("120.1"), new BigDecimal("30.2"));

        TripDetail detail = tripService.create(1L, command, "AGENT");

        assertThat(detail.trip().getId()).isEqualTo(100L);
        assertThat(detail.trip().getTitle()).isEqualTo("杭州2日游");
        assertThat(detail.trip().getDaysCount()).isEqualTo(2);
        assertThat(detail.days()).hasSize(2);
        assertThat(detail.items()).hasSize(2);
        verify(tripMapper).insert(any(Trip.class));
        verify(tripDayMapper, org.mockito.Mockito.times(2)).insert(any(TripDay.class));
        verify(tripItemMapper, org.mockito.Mockito.times(2)).insert(any(TripItem.class));
    }

    @Test
    void createRejectsStartAfterEnd() {
        TripSaveCommand command = new TripSaveCommand(
                "", LocalDate.of(2026, 11, 12), start, null,
                new TripPlanCommand("杭州", List.of(day(1, "上午", "ATTRACTION",
                        new BigDecimal("120.1"), new BigDecimal("30.2"))), null),
                null);

        assertCode(() -> tripService.create(1L, command, "AGENT"), ResultCode.VALIDATION_ERROR);
    }

    @Test
    void createRejectsDayCountMismatch() {
        TripSaveCommand command = new TripSaveCommand(
                "", start, end, null,
                new TripPlanCommand("杭州", List.of(
                        day(1, "上午", "ATTRACTION",
                                new BigDecimal("120.1"), new BigDecimal("30.2"))), null),
                null);

        assertCode(() -> tripService.create(1L, command, "AGENT"), ResultCode.VALIDATION_ERROR);
    }

    @Test
    void createRejectsEmptyItems() {
        TripSaveCommand command = new TripSaveCommand(
                "", start, end, null,
                new TripPlanCommand("杭州", List.of(
                        new TripDayCommand(1, "无安排", List.of()),
                        new TripDayCommand(2, "无安排", List.of())), null),
                null);

        assertCode(() -> tripService.create(1L, command, "AGENT"), ResultCode.VALIDATION_ERROR);
    }

    @Test
    void createRejectsInvalidTimeSlot() {
        TripSaveCommand command = command(2, 1, "深夜", "ATTRACTION",
                new BigDecimal("120.1"), new BigDecimal("30.2"));

        assertCode(() -> tripService.create(1L, command, "AGENT"), ResultCode.VALIDATION_ERROR);
    }

    @Test
    void createRejectsInvalidItemType() {
        TripSaveCommand command = command(2, 1, "上午", "UNKNOWN",
                new BigDecimal("120.1"), new BigDecimal("30.2"));

        assertCode(() -> tripService.create(1L, command, "AGENT"), ResultCode.VALIDATION_ERROR);
    }

    @Test
    void createRejectsPartialCoordinates() {
        TripSaveCommand command = command(2, 1, "上午", "ATTRACTION",
                new BigDecimal("120.1"), null);

        assertCode(() -> tripService.create(1L, command, "AGENT"), ResultCode.VALIDATION_ERROR);
    }

    @Test
    void idempotentCreateReplaysExistingTrip() {
        TripIdempotencyRow row = new TripIdempotencyRow();
        row.setId(9L);
        row.setDeleted(0);
        when(tripMapper.selectIdempotencyRow(1L, "key-1")).thenReturn(row);
        when(tripMapper.selectOne(any())).thenReturn(existingTrip(9L));
        when(tripDayMapper.selectList(any())).thenReturn(List.of());
        when(tripItemMapper.selectList(any())).thenReturn(List.of());

        TripSaveResult result = tripService.createWithIdempotency(1L, validCommand(), "AGENT", "key-1");

        assertThat(result.replay()).isTrue();
        assertThat(result.detail().trip().getId()).isEqualTo(9L);
        verify(tripMapper, never()).insert(any(Trip.class));
    }

    @Test
    void idempotentCreateRejectsDeletedResource() {
        TripIdempotencyRow row = new TripIdempotencyRow();
        row.setId(9L);
        row.setDeleted(1);
        when(tripMapper.selectIdempotencyRow(1L, "key-2")).thenReturn(row);

        assertCode(() -> tripService.createWithIdempotency(1L, validCommand(), "AGENT", "key-2"),
                ResultCode.IDEMPOTENT_RESOURCE_DELETED);
    }

    @Test
    void idempotentCreateRejectsBlankKey() {
        assertCode(() -> tripService.createWithIdempotency(1L, validCommand(), "AGENT", " "),
                ResultCode.VALIDATION_ERROR);
    }

    @Test
    void updateRejectsVersionConflict() {
        Trip trip = existingTrip(9L);
        trip.setVersion(1);
        when(tripMapper.selectOne(any())).thenReturn(trip);

        TripSaveCommand command = new TripSaveCommand(
                "", start, end, null,
                new TripPlanCommand("杭州", List.of(
                        day(1, "上午", "ATTRACTION", new BigDecimal("120.1"), new BigDecimal("30.2")),
                        day(2, "下午", "ATTRACTION", new BigDecimal("120.2"), new BigDecimal("30.3"))), null),
                0);

        assertCode(() -> tripService.update(1L, 9L, command), ResultCode.VERSION_CONFLICT);
        verify(tripMapper, never()).updateById(any(Trip.class));
    }

    @Test
    void updateReplacesChildren() {
        Trip trip = existingTrip(9L);
        trip.setVersion(1);
        when(tripMapper.selectOne(any())).thenReturn(trip);
        when(tripMapper.updateById(any(Trip.class))).thenReturn(1);
        Trip refreshed = existingTrip(9L);
        refreshed.setTitle("新标题");
        refreshed.setVersion(2);
        when(tripMapper.selectById(9L)).thenReturn(refreshed);

        TripSaveCommand command = new TripSaveCommand(
                "新标题", start, end, "描述",
                new TripPlanCommand("杭州", List.of(
                        day(1, "上午", "ATTRACTION", new BigDecimal("120.1"), new BigDecimal("30.2")),
                        day(2, "下午", "ATTRACTION", new BigDecimal("120.2"), new BigDecimal("30.3"))), null),
                1);

        TripDetail detail = tripService.update(1L, 9L, command);

        assertThat(detail.trip().getTitle()).isEqualTo("新标题");
        assertThat(detail.days()).hasSize(2);
        assertThat(detail.items()).hasSize(2);
        verify(tripItemMapper).delete(any());
        verify(tripDayMapper).delete(any());
    }

    @Test
    void getForeignTripIsNotFound() {
        when(tripMapper.selectOne(any())).thenReturn(null);

        assertCode(() -> tripService.get(2L, 9L), ResultCode.TRIP_NOT_FOUND);
    }

    @Test
    void deleteForeignTripIsNotFound() {
        when(tripMapper.selectOne(any())).thenReturn(null);
        when(tripMapper.selectDeletedFlag(9L, 1L)).thenReturn(null);

        assertCode(() -> tripService.delete(1L, 9L), ResultCode.TRIP_NOT_FOUND);
    }

    @Test
    void deleteAlreadyDeletedIsIdempotent() {
        when(tripMapper.selectOne(any())).thenReturn(null);
        when(tripMapper.selectDeletedFlag(9L, 1L)).thenReturn(1);

        tripService.delete(1L, 9L);

        verify(tripMapper, never()).deleteById(9L);
    }

    @Test
    void deleteOwnedTrip() {
        when(tripMapper.selectOne(any())).thenReturn(existingTrip(9L));

        tripService.delete(1L, 9L);

        verify(tripMapper).deleteById(9L);
    }

    private TripSaveCommand validCommand() {
        return command(2, 1, "上午", "ATTRACTION",
                new BigDecimal("120.1"), new BigDecimal("30.2"));
    }

    private TripSaveCommand command(int rangeDays,
                                    int itemsPerDay,
                                    String timeSlot,
                                    String itemType,
                                    BigDecimal longitude,
                                    BigDecimal latitude) {
        LocalDate commandEnd = start.plusDays(rangeDays - 1L);
        List<TripDayCommand> days = new ArrayList<>();
        for (int index = 1; index <= rangeDays; index++) {
            List<TripItemCommand> items = new ArrayList<>();
            for (int item = 1; item <= itemsPerDay; item++) {
                items.add(new TripItemCommand(itemType, "景点" + index + "-" + item, "地址",
                        longitude, latitude, timeSlot, 120, "理由", null, null));
            }
            days.add(new TripDayCommand(index, "主题" + index, items));
        }
        return new TripSaveCommand("", start, commandEnd, null,
                new TripPlanCommand("杭州", days, null), null);
    }

    private TripDayCommand day(int index, String timeSlot, String itemType,
                               BigDecimal longitude, BigDecimal latitude) {
        return new TripDayCommand(index, "主题" + index, List.of(
                new TripItemCommand(itemType, "景点" + index, "地址",
                        longitude, latitude, timeSlot, 120, "理由", null, null)));
    }

    private Trip existingTrip(Long id) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setUserId(1L);
        trip.setTitle("已存在行程");
        trip.setDestination("杭州");
        trip.setStartDate(start);
        trip.setEndDate(end);
        trip.setDaysCount(2);
        trip.setVersion(0);
        trip.setDeleted(0);
        return trip;
    }

    private void assertCode(Runnable runnable, ResultCode expected) {
        BusinessException ex = catchThrowableOfType(runnable::run, BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getResultCode()).isEqualTo(expected);
    }
}
