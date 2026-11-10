package com.tripagent.trip.converter;

import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.trip.dto.AgentDayRequest;
import com.tripagent.trip.dto.AgentItemRequest;
import com.tripagent.trip.dto.AgentPlanRequest;
import com.tripagent.trip.dto.AgentTripSaveRequest;
import com.tripagent.trip.dto.FrontendPlanRequest;
import com.tripagent.trip.dto.PlanExtrasDTO;
import com.tripagent.trip.dto.TripBaseRequest;
import com.tripagent.trip.entity.PlanExtras;
import com.tripagent.trip.entity.Trip;
import com.tripagent.trip.entity.TripDay;
import com.tripagent.trip.entity.TripItem;
import com.tripagent.trip.model.TripDayCommand;
import com.tripagent.trip.model.TripDetail;
import com.tripagent.trip.model.TripItemCommand;
import com.tripagent.trip.model.TripPlanCommand;
import com.tripagent.trip.model.TripSaveCommand;
import com.tripagent.trip.vo.AgentDayVO;
import com.tripagent.trip.vo.AgentItemVO;
import com.tripagent.trip.vo.AgentPlanVO;
import com.tripagent.trip.vo.AgentTripDetailVO;
import com.tripagent.trip.vo.AgentTripSummaryVO;
import com.tripagent.trip.vo.FrontendPlanVO;
import com.tripagent.trip.vo.FrontendTripDayVO;
import com.tripagent.trip.vo.FrontendTripDetailVO;
import com.tripagent.trip.vo.FrontendTripItemVO;
import com.tripagent.trip.vo.FrontendTripSummaryVO;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 前端 DTO/VO 与内部命令/实体之间的转换。
 */
public final class TripConverter {

    private TripConverter() {
    }

    public static TripSaveCommand toCommand(TripBaseRequest request, Integer version) {
        return new TripSaveCommand(
                request.getTitle(),
                request.getStartDate(),
                request.getEndDate(),
                request.getDescription(),
                toPlanCommand(request.getPlan()),
                version
        );
    }

    public static FrontendTripDetailVO toDetailVO(TripDetail detail) {
        Trip trip = detail.trip();
        FrontendTripDetailVO vo = new FrontendTripDetailVO();
        vo.setId(trip.getId());
        vo.setTitle(trip.getTitle());
        vo.setDestination(trip.getDestination());
        vo.setStartDate(trip.getStartDate());
        vo.setEndDate(trip.getEndDate());
        vo.setDaysCount(trip.getDaysCount());
        vo.setDescription(trip.getDescription());
        vo.setSource(trip.getSource());
        vo.setVersion(trip.getVersion());
        vo.setCreateTime(trip.getCreateTime());
        vo.setUpdateTime(trip.getUpdateTime());

        List<TripItem> items = detail.items() == null ? List.of() : detail.items();
        Map<Long, List<TripItem>> itemsByDay = items.stream()
                .collect(Collectors.groupingBy(TripItem::getTripDayId));
        List<TripDay> days = detail.days() == null ? List.of() : detail.days();

        List<FrontendTripDayVO> dayVOs = days.stream()
                .sorted(Comparator.comparing(TripDay::getDayIndex))
                .map(day -> toDayVO(day, itemsByDay.getOrDefault(day.getId(), List.of())))
                .toList();

        FrontendPlanVO plan = new FrontendPlanVO();
        plan.setDestination(trip.getDestination());
        plan.setDays(dayVOs);
        fillPlanExtras(plan, trip.getPlanExtras());
        vo.setPlan(plan);
        return vo;
    }

    public static FrontendTripSummaryVO toSummaryVO(Trip trip) {
        FrontendTripSummaryVO vo = new FrontendTripSummaryVO();
        vo.setId(trip.getId());
        vo.setTitle(trip.getTitle());
        vo.setDestination(trip.getDestination());
        vo.setStartDate(trip.getStartDate());
        vo.setEndDate(trip.getEndDate());
        vo.setDaysCount(trip.getDaysCount());
        vo.setSource(trip.getSource());
        vo.setVersion(trip.getVersion());
        vo.setUpdateTime(trip.getUpdateTime());
        return vo;
    }

    public static TripSaveCommand toCommand(AgentTripSaveRequest request) {
        return new TripSaveCommand(
                request.getTitle(),
                request.getStartDate(),
                request.getEndDate(),
                request.getDescription(),
                toAgentPlanCommand(request.getPlan()),
                request.getVersion()
        );
    }

    public static AgentTripDetailVO toAgentDetailVO(TripDetail detail, boolean replay) {
        Trip trip = detail.trip();
        AgentTripDetailVO vo = new AgentTripDetailVO();
        vo.setId(trip.getId());
        vo.setTitle(trip.getTitle());
        vo.setDestination(trip.getDestination());
        vo.setStartDate(trip.getStartDate());
        vo.setEndDate(trip.getEndDate());
        vo.setDaysCount(trip.getDaysCount());
        vo.setDescription(trip.getDescription());
        vo.setSource(trip.getSource());
        vo.setVersion(trip.getVersion());
        vo.setCreateTime(trip.getCreateTime());
        vo.setUpdateTime(trip.getUpdateTime());
        vo.setIdempotentReplay(replay);

        List<TripItem> items = detail.items() == null ? List.of() : detail.items();
        Map<Long, List<TripItem>> itemsByDay = items.stream()
                .collect(Collectors.groupingBy(TripItem::getTripDayId));
        List<TripDay> days = detail.days() == null ? List.of() : detail.days();

        List<AgentDayVO> dayVOs = days.stream()
                .sorted(Comparator.comparing(TripDay::getDayIndex))
                .map(day -> {
                    AgentDayVO dayVO = new AgentDayVO();
                    dayVO.setDayIndex(day.getDayIndex());
                    dayVO.setTheme(day.getTheme());
                    dayVO.setItems(itemsByDay.getOrDefault(day.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(TripItem::getSequenceNo))
                            .map(TripConverter::toAgentItemVO)
                            .toList());
                    return dayVO;
                })
                .toList();

        AgentPlanVO plan = new AgentPlanVO();
        plan.setDestination(trip.getDestination());
        plan.setDays(dayVOs);
        PlanExtras extras = trip.getPlanExtras();
        if (extras != null) {
            plan.setLodgingArea(extras.getLodgingArea());
            plan.setDiningSuggestions(extras.getDiningSuggestions());
            plan.setBudgetEstimate(extras.getBudgetEstimate());
            plan.setAssumptions(extras.getAssumptions());
            plan.setValidationStatus(extras.getValidationStatus());
            plan.setWarnings(extras.getWarnings());
        }
        vo.setPlan(plan);
        return vo;
    }

    public static AgentTripSummaryVO toAgentSummaryVO(Trip trip) {
        AgentTripSummaryVO vo = new AgentTripSummaryVO();
        vo.setId(trip.getId());
        vo.setTitle(trip.getTitle());
        vo.setDestination(trip.getDestination());
        vo.setStartDate(trip.getStartDate());
        vo.setEndDate(trip.getEndDate());
        vo.setDaysCount(trip.getDaysCount());
        vo.setSource(trip.getSource());
        vo.setVersion(trip.getVersion());
        vo.setUpdateTime(trip.getUpdateTime());
        return vo;
    }

    private static TripPlanCommand toAgentPlanCommand(AgentPlanRequest plan) {
        if (plan == null || plan.getDays() == null) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "plan.days 不能为空");
        }
        List<TripDayCommand> days = plan.getDays().stream()
                .map(day -> new TripDayCommand(
                        day.getDayIndex(),
                        day.getTheme(),
                        day.getItems() == null
                                ? List.of()
                                : day.getItems().stream()
                                .map(TripConverter::toAgentItemCommand)
                                .toList()))
                .toList();
        return new TripPlanCommand(plan.getDestination(), days, toAgentPlanExtras(plan));
    }

    private static TripItemCommand toAgentItemCommand(AgentItemRequest item) {
        BigDecimal longitude = null;
        BigDecimal latitude = null;
        List<BigDecimal> coords = item.getCoords();
        if (coords != null && !coords.isEmpty()) {
            if (coords.size() != 2) {
                throw new BusinessException(
                        ResultCode.VALIDATION_ERROR, "coords 必须是 [经度, 纬度]");
            }
            longitude = coords.get(0);
            latitude = coords.get(1);
        }
        return new TripItemCommand(
                item.getItemType(),
                item.getPoi(),
                item.getAddress(),
                longitude,
                latitude,
                item.getTimeSlot(),
                item.getDurationMin(),
                item.getReason(),
                item.getExternalPoiId(),
                item.getNotes()
        );
    }

    private static PlanExtras toAgentPlanExtras(AgentPlanRequest plan) {
        boolean empty = plan.getLodgingArea() == null
                && (plan.getDiningSuggestions() == null || plan.getDiningSuggestions().isEmpty())
                && plan.getBudgetEstimate() == null
                && plan.getAssumptions() == null
                && !StringUtils.hasText(plan.getValidationStatus())
                && (plan.getWarnings() == null || plan.getWarnings().isEmpty());
        if (empty) {
            return null;
        }
        PlanExtras extras = new PlanExtras();
        extras.setLodgingArea(plan.getLodgingArea());
        extras.setDiningSuggestions(plan.getDiningSuggestions());
        extras.setBudgetEstimate(plan.getBudgetEstimate());
        extras.setAssumptions(plan.getAssumptions());
        extras.setValidationStatus(trimToNull(plan.getValidationStatus()));
        extras.setWarnings(plan.getWarnings());
        return extras;
    }

    private static AgentItemVO toAgentItemVO(TripItem item) {
        AgentItemVO vo = new AgentItemVO();
        vo.setPoi(item.getName());
        if (item.getLongitude() != null && item.getLatitude() != null) {
            vo.setCoords(List.of(item.getLongitude(), item.getLatitude()));
        }
        vo.setItemType(item.getItemType());
        vo.setAddress(item.getAddress());
        vo.setTimeSlot(item.getTimeSlot());
        vo.setReason(item.getReason());
        vo.setDurationMin(item.getDurationMin());
        vo.setExternalPoiId(item.getExternalPoiId());
        vo.setNotes(item.getNotes());
        return vo;
    }

    private static TripPlanCommand toPlanCommand(FrontendPlanRequest plan) {
        List<TripDayCommand> days = plan.getDays().stream()
                .map(day -> new TripDayCommand(
                        day.getDayIndex(),
                        day.getTheme(),
                        day.getItems() == null
                                ? List.of()
                                : day.getItems().stream()
                                .map(item -> new TripItemCommand(
                                        item.getItemType(),
                                        item.getName(),
                                        item.getAddress(),
                                        item.getLongitude(),
                                        item.getLatitude(),
                                        item.getTimeSlot(),
                                        item.getDurationMin(),
                                        item.getReason(),
                                        item.getExternalPoiId(),
                                        item.getNotes()))
                                .toList()))
                .toList();
        return new TripPlanCommand(plan.getDestination(), days, toPlanExtras(plan));
    }

    private static PlanExtras toPlanExtras(FrontendPlanRequest plan) {
        boolean empty = plan.getLodgingArea() == null
                && (plan.getDiningSuggestions() == null || plan.getDiningSuggestions().isEmpty())
                && plan.getBudgetEstimate() == null
                && plan.getAssumptions() == null
                && !StringUtils.hasText(plan.getValidationStatus())
                && (plan.getWarnings() == null || plan.getWarnings().isEmpty());
        if (empty) {
            return null;
        }

        PlanExtras extras = new PlanExtras();
        if (plan.getLodgingArea() != null) {
            PlanExtras.LodgingArea lodging = new PlanExtras.LodgingArea();
            lodging.setName(plan.getLodgingArea().getName());
            lodging.setReason(plan.getLodgingArea().getReason());
            extras.setLodgingArea(lodging);
        }
        if (plan.getDiningSuggestions() != null && !plan.getDiningSuggestions().isEmpty()) {
            extras.setDiningSuggestions(plan.getDiningSuggestions().stream().map(dto -> {
                PlanExtras.DiningSuggestion suggestion = new PlanExtras.DiningSuggestion();
                suggestion.setArea(dto.getArea());
                suggestion.setCuisine(dto.getCuisine());
                suggestion.setNote(dto.getNote());
                return suggestion;
            }).toList());
        }
        if (plan.getBudgetEstimate() != null) {
            PlanExtras.BudgetEstimate budget = new PlanExtras.BudgetEstimate();
            budget.setTotal(plan.getBudgetEstimate().getTotal());
            budget.setBreakdown(plan.getBudgetEstimate().getBreakdown());
            extras.setBudgetEstimate(budget);
        }
        if (plan.getAssumptions() != null) {
            PlanExtras.Assumptions assumptions = new PlanExtras.Assumptions();
            assumptions.setDays(plan.getAssumptions().getDays());
            assumptions.setCrowd(plan.getAssumptions().getCrowd());
            assumptions.setBudgetLevel(plan.getAssumptions().getBudgetLevel());
            assumptions.setNotes(plan.getAssumptions().getNotes() == null
                    ? null : new ArrayList<>(plan.getAssumptions().getNotes()));
            extras.setAssumptions(assumptions);
        }
        extras.setValidationStatus(trimToNull(plan.getValidationStatus()));
        if (plan.getWarnings() != null && !plan.getWarnings().isEmpty()) {
            extras.setWarnings(plan.getWarnings().stream().map(dto -> {
                PlanExtras.Warning warning = new PlanExtras.Warning();
                warning.setDayIndex(dto.getDayIndex());
                warning.setFrom(dto.getFrom());
                warning.setTo(dto.getTo());
                warning.setWalkMin(dto.getWalkMin());
                warning.setDistanceKm(dto.getDistanceKm());
                warning.setMessage(dto.getMessage());
                return warning;
            }).toList());
        }
        return extras;
    }

    private static void fillPlanExtras(FrontendPlanVO plan, PlanExtras extras) {
        if (extras == null) {
            return;
        }
        if (extras.getLodgingArea() != null) {
            PlanExtrasDTO.LodgingAreaDTO dto = new PlanExtrasDTO.LodgingAreaDTO();
            dto.setName(extras.getLodgingArea().getName());
            dto.setReason(extras.getLodgingArea().getReason());
            plan.setLodgingArea(dto);
        }
        if (extras.getDiningSuggestions() != null) {
            plan.setDiningSuggestions(extras.getDiningSuggestions().stream().map(item -> {
                PlanExtrasDTO.DiningSuggestionDTO dto = new PlanExtrasDTO.DiningSuggestionDTO();
                dto.setArea(item.getArea());
                dto.setCuisine(item.getCuisine());
                dto.setNote(item.getNote());
                return dto;
            }).toList());
        }
        if (extras.getBudgetEstimate() != null) {
            PlanExtrasDTO.BudgetEstimateDTO dto = new PlanExtrasDTO.BudgetEstimateDTO();
            dto.setTotal(extras.getBudgetEstimate().getTotal());
            dto.setBreakdown(extras.getBudgetEstimate().getBreakdown());
            plan.setBudgetEstimate(dto);
        }
        if (extras.getAssumptions() != null) {
            PlanExtrasDTO.AssumptionsDTO dto = new PlanExtrasDTO.AssumptionsDTO();
            dto.setDays(extras.getAssumptions().getDays());
            dto.setCrowd(extras.getAssumptions().getCrowd());
            dto.setBudgetLevel(extras.getAssumptions().getBudgetLevel());
            dto.setNotes(extras.getAssumptions().getNotes() == null
                    ? null : new ArrayList<>(extras.getAssumptions().getNotes()));
            plan.setAssumptions(dto);
        }
        plan.setValidationStatus(extras.getValidationStatus());
        if (extras.getWarnings() != null) {
            plan.setWarnings(extras.getWarnings().stream().map(item -> {
                PlanExtrasDTO.WarningDTO dto = new PlanExtrasDTO.WarningDTO();
                dto.setDayIndex(item.getDayIndex());
                dto.setFrom(item.getFrom());
                dto.setTo(item.getTo());
                dto.setWalkMin(item.getWalkMin());
                dto.setDistanceKm(item.getDistanceKm());
                dto.setMessage(item.getMessage());
                return dto;
            }).toList());
        }
    }

    private static FrontendTripDayVO toDayVO(TripDay day, List<TripItem> items) {
        FrontendTripDayVO vo = new FrontendTripDayVO();
        vo.setId(day.getId());
        vo.setDayIndex(day.getDayIndex());
        vo.setDayDate(day.getDayDate());
        vo.setTheme(day.getTheme());
        vo.setItems(items.stream()
                .sorted(Comparator.comparing(TripItem::getSequenceNo))
                .map(TripConverter::toItemVO)
                .toList());
        return vo;
    }

    private static FrontendTripItemVO toItemVO(TripItem item) {
        FrontendTripItemVO vo = new FrontendTripItemVO();
        vo.setId(item.getId());
        vo.setSequenceNo(item.getSequenceNo());
        vo.setItemType(item.getItemType());
        vo.setName(item.getName());
        vo.setAddress(item.getAddress());
        vo.setLongitude(item.getLongitude());
        vo.setLatitude(item.getLatitude());
        vo.setTimeSlot(item.getTimeSlot());
        vo.setDurationMin(item.getDurationMin());
        vo.setReason(item.getReason());
        vo.setExternalPoiId(item.getExternalPoiId());
        vo.setNotes(item.getNotes());
        return vo;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
