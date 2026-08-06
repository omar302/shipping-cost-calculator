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

    private static ShippingRequest parcel(String weightKg) {
        return parcel(weightKg, "DOMESTIC");
    }

    private static ShippingRequest parcel(String weightKg, String zone) {
        return new ShippingRequest(weightKg == null ? null : new BigDecimal(weightKg), zone);
    }

    @ParameterizedTest(name = "The one where a parcel of {0}kg has a base rate of £{1}")
    @CsvSource({
            "25.00,  11.49",
            "22.50,  10.24",
            "50.00,  23.99",
            "20.01,   9.00",
            "20.00,   8.99",
    })
    void surchargeIsFiftyPencePerKilogramAboveTwenty(String weightKg, String expectedBaseRate) {
        var cost = service.calculate(parcel(weightKg));

        assertThat(cost.breakdown().baseRate()).isEqualByComparingTo(expectedBaseRate);
    }

    // The spec's full multiplier table; the acceptance tier keeps the first three rows
    // as its headline examples. The 25.00kg and 50.00kg rows are the ones where the
    // over-20kg surcharge is scaled along with the bracket rate, and every row but the
    // first lands on a half-up rounding decision in the third decimal place.
    @ParameterizedTest(name = "The one where a {0}kg parcel to {1} has a zone-adjusted rate of £{2}")
    @CsvSource({
            "0.50,  DOMESTIC,       2.99",
            "2.00,  EUROPEAN,       7.49",
            "12.00, INTERNATIONAL, 22.48",
            "25.00, EUROPEAN,      17.24",
            "50.00, INTERNATIONAL, 59.98",
    })
    void zoneMultiplierScalesTheWholeWeightBasedRate(String weightKg, String zone, String expectedRate) {
        var cost = service.calculate(parcel(weightKg, zone));

        assertThat(cost.breakdown().zoneAdjustedRate()).isEqualByComparingTo(expectedRate);
    }

    @Test
    @DisplayName("The one where a request carries no destination zone at all and is rejected as invalid")
    void requestWithoutADestinationZoneIsRejected() {
        var request = parcel("2.00", null);

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidDestinationZoneException.class)
                .hasMessage("Destination zone is required");
    }

    // One row per weight rule, every one of them paired with an unrecognised zone:
    // whichever weight rule is broken, it is the rejection reported and the zone is
    // never examined. The empty first column is a request carrying no weight at all.
    @ParameterizedTest(name = "The one where a parcel to \"LUNAR\" is rejected with {1}")
    @CsvSource(delimiter = '|', textBlock = """
            0.00  | Parcel weight must be above 0kg and at most 50kg, but was 0.00kg
            2.005 | Parcel weight must be given to at most 2 decimal places, but was 2.005kg
                  | Parcel weight is required
            """)
    void weightIsValidatedBeforeTheDestinationZone(String weightKg, String expectedExplanation) {
        var request = parcel(weightKg, "LUNAR");

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidParcelWeightException.class)
                .hasMessage(expectedExplanation);
    }

    @Test
    @DisplayName("The one where a parcel of 0.01kg is accepted at £2.99")
    void weightJustAboveZeroIsAccepted() {
        var cost = service.calculate(parcel("0.01"));

        assertThat(cost.breakdown().baseRate()).isEqualByComparingTo("2.99");
    }

    @Test
    @DisplayName("The one where a parcel submitted at 2.005kg is rejected as invalid")
    void weightFinerThanTwoDecimalPlacesIsRejected() {
        var request = parcel("2.005");

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidParcelWeightException.class);
    }

    @Test
    @DisplayName("The one where a parcel of 2.5000kg is accepted at £4.99, trailing zeros being no finer than 2.50kg")
    void trailingZerosAreNotFinerPrecision() {
        var cost = service.calculate(parcel("2.5000"));

        assertThat(cost.breakdown().baseRate()).isEqualByComparingTo("4.99");
    }

    @Test
    @DisplayName("The one where a request carries no parcel weight at all and is rejected as invalid")
    void requestWithoutAParcelWeightIsRejected() {
        var request = parcel(null);

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidParcelWeightException.class)
                .hasMessage("Parcel weight is required");
    }

    @ParameterizedTest(name = "The one where a parcel of {0}kg is rejected as invalid")
    @CsvSource({"0.00", "-1.50", "50.01"})
    void weightOutsideTheAcceptedRangeIsRejected(String weightKg) {
        var request = parcel(weightKg);

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidParcelWeightException.class);
    }
}
