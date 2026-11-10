package com.tripagent.trip.model;

import java.math.BigDecimal;

public record TripItemCommand(
        String itemType,
        String name,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        String timeSlot,
        Integer durationMin,
        String reason,
        String externalPoiId,
        String notes
) {
}
