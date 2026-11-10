package com.tripagent.trip.model;

import com.tripagent.trip.entity.Trip;
import com.tripagent.trip.entity.TripDay;
import com.tripagent.trip.entity.TripItem;

import java.util.List;

public record TripDetail(Trip trip, List<TripDay> days, List<TripItem> items) {
}
