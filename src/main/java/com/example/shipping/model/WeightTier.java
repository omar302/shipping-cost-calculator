package com.example.shipping.model;

import java.math.BigDecimal;

public enum WeightTier {

    UNDER_1KG(new BigDecimal("0"), new BigDecimal("2.99")),
    ONE_TO_5KG(new BigDecimal("1"), new BigDecimal("4.99")),
    FIVE_TO_20KG(new BigDecimal("5"), new BigDecimal("8.99")),
    OVER_20KG(new BigDecimal("20"), new BigDecimal("8.99"));

    private final BigDecimal lowerBoundKg;
    private final BigDecimal baseRate;

    WeightTier(BigDecimal lowerBoundKg, BigDecimal baseRate) {
        this.lowerBoundKg = lowerBoundKg;
        this.baseRate = baseRate;
    }

    public BigDecimal lowerBoundKg() {
        return lowerBoundKg;
    }

    public BigDecimal baseRate() {
        return baseRate;
    }

    // Tiers are declared in ascending order of lower bound, so the last match wins.
    public static WeightTier forWeight(BigDecimal weightKg) {
        WeightTier tier = UNDER_1KG;
        for (WeightTier candidate : values()) {
            if (weightKg.compareTo(candidate.lowerBoundKg) >= 0) {
                tier = candidate;
            }
        }
        return tier;
    }
}
