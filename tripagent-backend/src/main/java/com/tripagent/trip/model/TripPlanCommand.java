package com.tripagent.trip.model;

import com.tripagent.trip.entity.PlanExtras;

import java.util.List;

public record TripPlanCommand(String destination, List<TripDayCommand> days, PlanExtras extras) {
}
