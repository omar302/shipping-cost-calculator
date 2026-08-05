package com.example.shipping.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeightTierTest {

    @ParameterizedTest(name = "The one where a {0}kg parcel has a base rate of £{1}")
    @CsvSource({
            "0.50,   2.99",
            "2.00,   4.99",
            "12.00,  8.99",
            "0.99,   2.99",
            "1.00,   4.99",
            "5.00,   8.99",
            "20.00,  8.99",
    })
    void baseRateIsDeterminedByWeightBracket(String weightKg, String expectedBaseRate) {
        assertThat(WeightTier.forWeight(new BigDecimal(weightKg)).baseRate())
                .isEqualByComparingTo(expectedBaseRate);
    }
}
