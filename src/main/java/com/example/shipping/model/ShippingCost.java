package com.example.shipping.model;

import java.math.BigDecimal;

public record ShippingCost(Breakdown breakdown) {

    public record Breakdown(BigDecimal baseRate, BigDecimal zoneMultiplier, BigDecimal zoneAdjustedRate) {
    }
}
