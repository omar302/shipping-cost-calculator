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

    // A £0.00 order is valid and never reaches the free-shipping threshold, so it keeps
    // the weight and zone examples priced as they were before free shipping existed.
    private static ShippingRequest parcel(String weightKg, String zone) {
        return parcel(weightKg, zone, "0.00");
    }

    private static ShippingRequest parcel(String weightKg, String zone, String orderTotal) {
        return new ShippingRequest(
                weightKg == null ? null : new BigDecimal(weightKg),
                zone,
                orderTotal == null ? null : new BigDecimal(orderTotal));
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

    // The spec's full qualification table; the acceptance tier keeps one headline row
    // per outcome. 20.00kg and £50.00 are the inclusive boundaries, 20.01kg and £49.99
    // the rows just past each.
    @ParameterizedTest(name = "The one where a {0}kg parcel to {1} on a £{2} order costs £{3}")
    @CsvSource({
            "2.00,  DOMESTIC,       60.00,  0.00",
            "2.00,  EUROPEAN,       60.00,  0.00",
            "20.00, DOMESTIC,       60.00,  0.00",
            "20.01, DOMESTIC,       60.00,  9.00",
            "2.00,  DOMESTIC,       50.00,  0.00",
            "2.00,  DOMESTIC,       49.99,  4.99",
            "2.00,  EUROPEAN,       49.99,  7.49",
            "2.00,  INTERNATIONAL, 1000.00, 12.48",
    })
    void freeShippingIsWaivedOnlyForAQualifyingParcel(
            String weightKg, String zone, String orderTotal, String expectedTotalCost) {
        var cost = service.calculate(parcel(weightKg, zone, orderTotal));

        assertThat(cost.totalCost()).isEqualByComparingTo(expectedTotalCost);
    }

    // The INTERNATIONAL row is the counter-example: the flag is reported even when free
    // shipping was not earned, as the zone multiplier already is.
    @ParameterizedTest(name = "The one where a 2.00kg {0} order of £{1} reports free shipping applied {2}")
    @CsvSource({
            "DOMESTIC,       60.00, true",
            "INTERNATIONAL, 100.00, false",
    })
    void freeShippingAppliedIsReportedWhetherOrNotItWasEarned(
            String zone, String orderTotal, boolean expectedFreeShippingApplied) {
        var cost = service.calculate(parcel("2.00", zone, orderTotal));

        assertThat(cost.breakdown().freeShippingApplied()).isEqualTo(expectedFreeShippingApplied);
    }

    @Test
    @DisplayName("The one where a request carries no order total at all and is rejected as invalid")
    void requestWithoutAnOrderTotalIsRejected() {
        var request = parcel("2.00", "DOMESTIC", null);

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidOrderTotalException.class)
                .hasMessage("Order total is required");
    }

    @Test
    @DisplayName("The one where an order total submitted at £75.005 is rejected as invalid")
    void orderTotalFinerThanTwoDecimalPlacesIsRejected() {
        var request = parcel("2.00", "DOMESTIC", "75.005");

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidOrderTotalException.class);
    }

    @Test
    @DisplayName("The one where an order total of -£1.00 is rejected as invalid")
    void orderTotalBelowZeroIsRejected() {
        var request = parcel("2.00", "DOMESTIC", "-1.00");

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOf(InvalidOrderTotalException.class);
    }

    @Test
    @DisplayName("The one where an order of £50.0000 is shipped free, trailing zeros being no finer than £50.00")
    void orderTotalTrailingZerosAreNotFinerPrecision() {
        var cost = service.calculate(parcel("2.00", "DOMESTIC", "50.0000"));

        assertThat(cost.totalCost()).isEqualByComparingTo("0.00");
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
