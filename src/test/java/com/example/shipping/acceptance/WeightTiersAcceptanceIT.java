package com.example.shipping.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Weight Tiers")
class WeightTiersAcceptanceIT {

    @Autowired
    private MockMvcTester mvc;

    // Every weight-tier example is a domestic example on a £0.00 order: the DOMESTIC
    // multiplier is x1.0 and a zero order never earns free shipping, so the rates below
    // are unchanged by the destination-zones and free-shipping features.
    private MvcTestResult calculate(String weightKg) {
        return calculateBody("""
                { "weightKg": %s, "zone": "DOMESTIC", "orderTotal": 0.00 }
                """.formatted(weightKg));
    }

    private MvcTestResult calculateBody(String json) {
        return mvc.post().uri("/api/shipping/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    @Nested
    @DisplayName("The base rate is determined by which weight bracket the parcel falls into")
    class BaseRateByWeightBracket {

        // The full bracket table, boundaries included, is enumerated in WeightTierTest.
        @Test
        @DisplayName("The one where a 2kg parcel has a base rate of £4.99")
        void twoKilogramParcelHasBaseRateOfFourNinetyNine() {
            assertThat(calculate("2.00"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.breakdown.baseRate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("4.99");
        }
    }

    @Nested
    @DisplayName("£0.50 is added for every kilogram above 20kg, charged pro-rata")
    class SurchargeAboveTwentyKilograms {

        // The full surcharge table, pro-rata and rounding boundaries included, is
        // enumerated in ShippingCostServiceTest.
        @Test
        @DisplayName("The one where a 25kg parcel has a base rate of £11.49")
        void twentyFiveKilogramParcelHasBaseRateOfElevenFortyNine() {
            assertThat(calculate("25.00"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.breakdown.baseRate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("11.49");
        }
    }

    @Nested
    @DisplayName("A parcel weight outside the range above 0kg up to and including 50kg is rejected")
    class WeightOutsideAcceptedRange {

        // Every out-of-range weight is enumerated in ShippingCostServiceTest; this
        // example exists to prove the rejection reaches the caller as a 400.
        @Test
        @DisplayName("The one where a parcel of 0kg is rejected as invalid")
        void zeroWeightParcelIsRejected() {
            assertThat(calculate("0.00")).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @ParameterizedTest(name = "The one where a parcel of {0}kg is accepted at £{1}")
        @CsvSource({
                "0.01,   2.99",
                "50.00, 23.99",
        })
        void weightAtTheEdgeOfTheAcceptedRangeIsPriced(String weightKg, String expectedBaseRate) {
            assertThat(calculate(weightKg))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.breakdown.baseRate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedBaseRate);
        }
    }

    @Nested
    @DisplayName("A parcel weight given to more than two decimal places is rejected")
    class WeightPrecision {

        @Test
        @DisplayName("The one where a parcel submitted at 2.005kg is rejected as invalid")
        void weightFinerThanTwoDecimalPlacesIsRejected() {
            assertThat(calculate("2.005")).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("The one where a parcel weighs exactly 22.53kg and is priced at £10.26")
        void weightOfExactlyTwoDecimalPlacesIsPriced() {
            assertThat(calculate("22.53"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.breakdown.baseRate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("10.26");
        }
    }

    @Nested
    @DisplayName("A request that does not carry a parcel weight is rejected")
    class MissingWeight {

        // The counter-example — a weight that is present but zero, rejected with the
        // range explanation instead — is pinned by RejectionExplanation below.
        @ParameterizedTest(name = "The one where a request of {0} is rejected as requiring a weight")
        @ValueSource(strings = {"{}", "{ \"weightKg\": null }"})
        void requestWithoutAParcelWeightIsRejected(String body) {
            assertThat(calculateBody(body))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Parcel weight is required");
        }
    }

    @Nested
    @DisplayName("A parcel weight that is not a number is rejected")
    class NonNumericWeight {

        @Test
        @DisplayName("The one where a weight submitted as \"heavy\" is rejected, naming the value that was not a number")
        void nonNumericWeightIsRejected() {
            assertThat(calculateBody("""
                    { "weightKg": "heavy" }
                    """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Parcel weight must be a number, but was \"heavy\"");
        }

        @Test
        @DisplayName("The one where the body is malformed JSON and is rejected without blaming a field")
        void malformedJsonKeepsTheGenericExplanation() {
            assertThat(calculateBody("{invalid"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Request body could not be read");
        }

        @Test
        @DisplayName("The one where a weight submitted as the string \"2.50\" is accepted at £4.99")
        void quotedNumberIsStillANumber() {
            assertThat(calculateBody("""
                    { "weightKg": "2.50", "zone": "DOMESTIC", "orderTotal": 0.00 }
                    """))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.breakdown.baseRate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("4.99");
        }
    }

    @Nested
    @DisplayName("A rejected parcel explains why it was rejected")
    class RejectionExplanation {

        @Test
        @DisplayName("The one where a 0kg parcel is rejected with an explanation naming the accepted range")
        void outOfRangeRejectionExplainsTheRange() {
            assertThat(calculate("0.00"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Parcel weight must be above 0kg and at most 50kg, but was 0.00kg");
        }

        @Test
        @DisplayName("The one where a 2.005kg parcel is rejected with an explanation naming the precision limit")
        void tooPreciseRejectionExplainsThePrecisionLimit() {
            assertThat(calculate("2.005"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Parcel weight must be given to at most 2 decimal places, but was 2.005kg");
        }
    }
}
