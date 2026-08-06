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
@DisplayName("Destination Zones")
class DestinationZonesAcceptanceIT {

    @Autowired
    private MockMvcTester mvc;

    // Every zone example is a £0.00 order, which never earns free shipping, so the
    // rates below are unchanged by that feature.
    private MvcTestResult calculate(String weightKg, String zone) {
        return calculateBody("""
                { "weightKg": %s, "zone": "%s", "orderTotal": 0.00 }
                """.formatted(weightKg, zone));
    }

    private MvcTestResult calculateBody(String json) {
        return mvc.post().uri("/api/shipping/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    @Nested
    @DisplayName("The weight-based rate is multiplied by the multiplier for the destination zone")
    class ZoneMultiplier {

        // One headline example per zone. The full table — the rounding boundaries and
        // the surcharge being scaled along with the base rate — is enumerated in
        // ShippingCostServiceTest.
        @ParameterizedTest(name = "The one where a {0}kg parcel to {1} costs £{2}")
        @CsvSource({
                "0.50,  DOMESTIC,       2.99",
                "2.00,  EUROPEAN,       7.49",
                "12.00, INTERNATIONAL, 22.48",
        })
        void zoneMultiplierIsAppliedToTheWeightBasedRate(String weightKg, String zone, String expectedRate) {
            assertThat(calculate(weightKg, zone))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.breakdown.zoneAdjustedRate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedRate);
        }
    }

    @Nested
    @DisplayName("A destination zone that is not one of the three recognised names is rejected")
    class UnrecognisedZone {

        // Rates here pin which zone a spelling resolves to; the multiplier itself is
        // proven by ZoneMultiplier above. All three parcels weigh 2.00kg (£4.99).
        @ParameterizedTest(name = "The one where a parcel to \"{0}\" is accepted at £{1}")
        @CsvSource({
                "EUROPEAN,       7.49",
                "european,       7.49",
                "International, 12.48",
        })
        void anyCapitalisationOfARecognisedZoneIsAccepted(String zone, String expectedRate) {
            assertThat(calculate("2.00", zone))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.breakdown.zoneAdjustedRate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedRate);
        }

        // A literal rather than a parameter source: @CsvSource would trim the padding
        // away and @ValueSource's would be invisible, leaving the test proving nothing.
        @Test
        @DisplayName("The one where a parcel to \" EUROPEAN \" is accepted at £7.49, surrounding whitespace being ignored")
        void surroundingWhitespaceIsIgnored() {
            assertThat(calculate("2.00", " EUROPEAN "))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.breakdown.zoneAdjustedRate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("7.49");
        }

        // The explanation for each is pinned by RejectionExplanation.
        @ParameterizedTest(name = "The one where a parcel to \"{0}\" is rejected as invalid")
        @ValueSource(strings = {"LUNAR", "EU", ""})
        void aZoneThatIsNotARecognisedNameIsRejected(String zone) {
            assertThat(calculate("2.00", zone)).hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("A request that does not carry a destination zone is rejected")
    class MissingZone {

        // The counter-example — a zone that is present but empty, rejected with the
        // unrecognised-zone explanation instead — belongs to that rule, not this one.
        @ParameterizedTest(name = "The one where a request of {0} is rejected as requiring a zone")
        @ValueSource(strings = {
                "{ \"weightKg\": 2.00 }",
                "{ \"weightKg\": 2.00, \"zone\": null }",
        })
        void requestWithoutADestinationZoneIsRejected(String body) {
            assertThat(calculateBody(body))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Destination zone is required");
        }
    }

    @Nested
    @DisplayName("The multiplier applied and the resulting rate are reported alongside the base rate")
    class ReportedBreakdown {

        // The DOMESTIC row is the counter-example: the multiplier is reported even
        // when it changes nothing.
        @ParameterizedTest(name = "The one where a {0}kg parcel to {1} reports £{2} at x{3}, making £{4}")
        @CsvSource({
                "25.00, EUROPEAN, 11.49, 1.5, 17.24",
                "0.50,  DOMESTIC,  2.99, 1.0,  2.99",
        })
        void breakdownReportsBaseRateMultiplierAndZoneAdjustedRate(
                String weightKg, String zone,
                String expectedBaseRate, String expectedMultiplier, String expectedZoneAdjustedRate) {
            MvcTestResult result = calculate(weightKg, zone);

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.breakdown.baseRate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedBaseRate);
            assertThat(result).bodyJson().extractingPath("$.breakdown.zoneMultiplier")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedMultiplier);
            assertThat(result).bodyJson().extractingPath("$.breakdown.zoneAdjustedRate")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedZoneAdjustedRate);
        }
    }

    @Nested
    @DisplayName("The parcel weight is validated before the destination zone")
    class WeightValidatedBeforeZone {

        // The counter-example — a valid weight with an unrecognised zone, where the zone
        // rejection is the one reported — is pinned by RejectionExplanation below.
        @Test
        @DisplayName("The one where a 0kg parcel to \"LUNAR\" is rejected for its weight, the zone never being examined")
        void weightRejectionIsReportedBeforeTheZoneIsExamined() {
            assertThat(calculate("0.00", "LUNAR"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Parcel weight must be above 0kg and at most 50kg, but was 0.00kg");
        }
    }

    @Nested
    @DisplayName("A rejected destination zone explains why it was rejected")
    class RejectionExplanation {

        @Test
        @DisplayName("The one where a \"LUNAR\" zone is rejected with an explanation naming the recognised zones")
        void unrecognisedZoneRejectionNamesTheRecognisedZones() {
            assertThat(calculate("2.00", "LUNAR"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.detail").asString()
                    .isEqualTo("Destination zone must be one of DOMESTIC, EUROPEAN, INTERNATIONAL, but was \"LUNAR\"");
        }
    }
}
