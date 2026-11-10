package com.tripagent.taxi.model;

import com.tripagent.taxi.entity.TaxiOrder;

public record TaxiOrderSaveResult(TaxiOrder order, boolean replay) {
}
