package com.tripagent.trip.model;

import lombok.Data;

@Data
public class TripIdempotencyRow {

    private Long id;
    private Integer deleted;
}
