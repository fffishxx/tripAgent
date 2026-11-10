package com.tripagent.trip.model;

import java.time.LocalDate;

public record TripSaveCommand(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        TripPlanCommand plan,
        Integer version
) {
}
