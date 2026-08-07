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

// Secured by api-security.specs.md: every request needs a configured key. The key
// is supplied here rather than taken from application.properties, so the suite
// never depends on whatever a real deployment configures.
@SpringBootTest(properties = "shipping.api-keys.test-user-key=USER")
@AutoConfigureMockMvc
@DisplayName("Free Shipping")
class FreeShippingAcceptanceIT {

    @Autowired
    private MockMvcTester mvc;

    private MvcTestResult calculate(String weightKg, String zone, String orderTotal) {
        return calculateBody("""
                { "weightKg": %s, "zone": "%s", "orderTotal": %s }
                """.formatted(weightKg, zone, orderTotal));
    }

    private MvcTestResult calculateBody(String json) {
        return mvc.post().uri("/api/shipping/calculate")
                .header("X-API-Key", "test-user-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    @Nested
    @DisplayName("The shipping cost is waived only for a domestic parcel of at most 20.00kg whose order total reaches £75.00")
    class QualifyingForFreeShipping {

        // One headline row per condition the rule turns on — earned, refused on weight,
        // refused on zone. The full table, including both inclusive boundaries and the
        // rows just past them, is enumerated in ShippingCostServiceTest.
        @ParameterizedTest(name = "The one where a {0}kg parcel to {1} on a £{2} order costs £{3}")
        @CsvSource({
                "2.00,  DOMESTIC, 80.00,  0.00",
                "20.01, DOMESTIC, 80.00,  9.00",
                "2.00,  EUROPEAN, 100.00, 7.49",
        })
        void freeShippingIsWaivedOnlyForAQualifyingDomesticParcel(
                String weightKg, String zone, String orderTotal, String expectedTotalCost) {
            assertThat(calculate(weightKg, zone, orderTotal))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalCost")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedTotalCost);
        }
    }

    @Nested
    @DisplayName("The total cost the customer pays and whether free shipping was applied are both reported")
    class ReportedTotalAndFlag {

        // Both rows are a 2.00kg parcel. The EUROPEAN row is the counter-example: the
        // flag is reported even when it changes nothing. In both, the zone-adjusted
        // rate is what the breakdown still shows — waived, it is not zeroed.
        @ParameterizedTest(name = "The one where a 2.00kg {0} order of £{1} pays £{2}, free shipping applied {3}")
        @CsvSource({
                "DOMESTIC,  80.00, 0.00, true,  4.99",
                "EUROPEAN, 100.00, 7.49, false, 7.49",
        })
        void totalCostAndFreeShippingFlagAreReportedAlongsideTheWaivedRate(
                String zone, String orderTotal, String expectedTotalCost,
                boolean expectedFreeShippingApplied, String expectedZoneAdjustedRate) {
            MvcTestResult result = calculate("2.00", zone, orderTotal);

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.totalCost")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedTotalCost);
            assertThat(result).bodyJson().extractingPath("$.breakdown.freeShippingApplied")
                    .asBoolean().isEqualTo(expectedFreeShippingApplied);
            assertThat(result).bodyJson().extractingPath("$.breakdown.zoneAdjustedRate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedZoneAdjustedRate);
        }
    }

    @Nested
    @DisplayName("A request that does not carry an order total is rejected")
    class MissingOrderTotal {

        @ParameterizedTest(name = "The one where a request of {0} is rejected as requiring an order total")
        @ValueSource(strings = {
                "{ \"weightKg\": 2.00, \"zone\": \"DOMESTIC\" }",
                "{ \"weightKg\": 2.00, \"zone\": \"DOMESTIC\", \"orderTotal\": null }",
        })
        void requestWithoutAnOrderTotalIsRejected(String body) {
            assertThat(calculateBody(body))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Order total is required");
        }

        // The spec's counter-example — an order total present but £0.00, accepted and
        // simply never qualifying — is the same request and assertion as the £0.00 row
        // of InvalidOrderTotal below, where it is the boundary of the accepted range.
        // It is executed there rather than twice.
    }

    @Nested
    @DisplayName("An order total that is not a valid amount is rejected")
    class InvalidOrderTotal {

        // All rows are a 2.00kg domestic parcel costing £4.99, so the total shows
        // whether the order qualified. The £75.0000 row is the point of the rule:
        // trailing zeros are not finer precision, so it is the same as £75.00 and
        // still earns free shipping. £0.00 is the boundary of the accepted range —
        // valid, and never qualifying.
        @ParameterizedTest(name = "The one where an order total of £{0} is accepted and pays £{1}")
        @CsvSource({
                "80.00,    0.00",
                "0.00,     4.99",
                "75.0000,  0.00",
        })
        void orderTotalWithinTheAcceptedRangeIsPriced(String orderTotal, String expectedTotalCost) {
            assertThat(calculate("2.00", "DOMESTIC", orderTotal))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalCost")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedTotalCost);
        }

        // The explanation for each is pinned by the rejection-explanation rule.
        @ParameterizedTest(name = "The one where an order total of £{0} is rejected as invalid")
        @ValueSource(strings = {"75.005", "-1.00"})
        void orderTotalThatIsNotAValidAmountIsRejected(String orderTotal) {
            assertThat(calculate("2.00", "DOMESTIC", orderTotal)).hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("An order total that is not a number is rejected")
    class NonNumericOrderTotal {

        @Test
        @DisplayName("The one where an order total submitted as \"abc\" is rejected, naming the order total rather than another field")
        void nonNumericOrderTotalIsRejected() {
            assertThat(calculateBody("""
                    { "weightKg": 2.00, "zone": "DOMESTIC", "orderTotal": "abc" }
                    """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Order total must be a number, but was \"abc\"");
        }

        @Test
        @DisplayName("The one where an order total submitted as the string \"80.00\" ships a 2.00kg domestic parcel free")
        void quotedNumberIsStillANumber() {
            assertThat(calculateBody("""
                    { "weightKg": 2.00, "zone": "DOMESTIC", "orderTotal": "80.00" }
                    """))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalCost")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("A rejected order total explains why it was rejected")
    class RejectionExplanation {

        // A '|' delimiter rather than a comma: the explanations contain commas.
        @ParameterizedTest(name = "The one where an order total of £{0} is rejected with {1}")
        @CsvSource(delimiter = '|', textBlock = """
                75.005 | Order total must be given to at most 2 decimal places, but was £75.005
                -1.00  | Order total must be £0.00 or above, but was £-1.00
                """)
        void rejectedOrderTotalExplainsTheRuleThatWasBroken(
                String orderTotal, String expectedExplanation) {
            assertThat(calculate("2.00", "DOMESTIC", orderTotal))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo(expectedExplanation);
        }
    }

    @Nested
    @DisplayName("The parcel weight and destination zone are validated before the order total")
    class WeightAndZoneValidatedBeforeOrderTotal {

        @Test
        @DisplayName("The one where a 0kg parcel to \"LUNAR\" with no order total is rejected for its weight, neither the zone nor the order total being examined")
        void weightRejectionIsReportedBeforeTheZoneOrTheOrderTotal() {
            assertThat(calculateBody("""
                    { "weightKg": 0.00, "zone": "LUNAR" }
                    """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Parcel weight must be above 0kg and at most 50kg, but was 0.00kg");
        }

        @Test
        @DisplayName("The one where a 2.00kg parcel to \"LUNAR\" with an order total of -£1.00 is rejected for its zone, the order total never being examined")
        void zoneRejectionIsReportedBeforeTheOrderTotal() {
            assertThat(calculate("2.00", "LUNAR", "-1.00"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Destination zone must be one of DOMESTIC, EUROPEAN, INTERNATIONAL, but was \"LUNAR\"");
        }

        @Test
        @DisplayName("The one where the weight and zone are both valid and only the order total is wrong, so that rejection is the one reported")
        void orderTotalRejectionIsReportedWhenNoEarlierRuleWasBroken() {
            assertThat(calculate("2.00", "DOMESTIC", "-1.00"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Order total must be £0.00 or above, but was £-1.00");
        }
    }
}
