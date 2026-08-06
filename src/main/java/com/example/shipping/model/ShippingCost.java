package com.example.shipping.model;

import java.math.BigDecimal;

public record ShippingCost(BigDecimal totalCost, Breakdown breakdown) {

    public record Breakdown(
            BigDecimal baseRate,
            BigDecimal zoneMultiplier,
            BigDecimal zoneAdjustedRate,
            boolean freeShippingApplied) {
    }
}
