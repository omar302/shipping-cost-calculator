package com.example.shipping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shipping.model.ShippingRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShippingCostServiceTest {

    private final ShippingCostService service = new ShippingCostService();

    @ParameterizedTest(name = "The one where a parcel of {0}kg has a base rate of £{1}")
    @CsvSource({
            "25.00,  11.49",
            "22.50,  10.24",
            "50.00,  23.99",
            "20.01,   9.00",
            "20.00,   8.99",
    })
    void surchargeIsFiftyPencePerKilogramAboveTwenty(String weightKg, String expectedBaseRate) {
        var cost = service.calculate(new ShippingRequest(new BigDecimal(weightKg)));

        assertThat(cost.breakdown().baseRate()).isEqualByComparingTo(expectedBaseRate);
    }

    @Test
    @DisplayName("The one where a parcel of 0.01kg is accepted at £2.99")
    void weightJustAboveZeroIsAccepted() {
        var cost = service.calculate(new ShippingRequest(new BigDecimal("0.01")));

        assertThat(cost.breakdown().baseRate()).isEqualByComparingTo("2.99");
    }

    @Test
    @DisplayName("The one where a parcel submitted at 2.005kg is rejected as invalid")
    void weightFinerThanTwoDecimalPlacesIsRejected() {
        var request = new ShippingRequest(new BigDecimal("2.005"));

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidParcelWeightException.class);
    }

    @Test
    @DisplayName("The one where a parcel of 2.5000kg is accepted at £4.99, trailing zeros being no finer than 2.50kg")
    void trailingZerosAreNotFinerPrecision() {
        var cost = service.calculate(new ShippingRequest(new BigDecimal("2.5000")));

        assertThat(cost.breakdown().baseRate()).isEqualByComparingTo("4.99");
    }

    @Test
    @DisplayName("The one where a request carries no parcel weight at all and is rejected as invalid")
    void requestWithoutAParcelWeightIsRejected() {
        var request = new ShippingRequest(null);

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidParcelWeightException.class)
                .hasMessage("Parcel weight is required");
    }

    @ParameterizedTest(name = "The one where a parcel of {0}kg is rejected as invalid")
    @CsvSource({"0.00", "-1.50", "50.01"})
    void weightOutsideTheAcceptedRangeIsRejected(String weightKg) {
        var request = new ShippingRequest(new BigDecimal(weightKg));

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidParcelWeightException.class);
    }
}
