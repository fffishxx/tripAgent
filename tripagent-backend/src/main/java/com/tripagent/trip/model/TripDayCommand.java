package com.tripagent.trip.model;

import java.util.List;

public record TripDayCommand(Integer dayIndex, String theme, List<TripItemCommand> items) {
}
