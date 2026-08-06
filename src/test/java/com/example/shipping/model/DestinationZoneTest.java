package com.example.shipping.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DestinationZoneTest {

    @ParameterizedTest(name = "The one where a parcel to {0} is charged x{1}")
    @CsvSource({
            "DOMESTIC,      1.0",
            "EUROPEAN,      1.5",
            "INTERNATIONAL, 2.5",
    })
    void multiplierIsDeterminedByDestinationZone(DestinationZone zone, String expectedMultiplier) {
        assertThat(zone.multiplier()).isEqualByComparingTo(expectedMultiplier);
    }

    @ParameterizedTest(name = "The one where a zone written \"{0}\" is recognised as {1}")
    @CsvSource({
            "EUROPEAN,      EUROPEAN",
            "european,      EUROPEAN",
            "International, INTERNATIONAL",
    })
    void zoneNamesAreMatchedIgnoringCase(String name, DestinationZone expected) {
        assertThat(DestinationZone.forName(name)).isEqualTo(expected);
    }

    // A literal rather than a @CsvSource row: the source would trim the padding away
    // before forName ever saw it, leaving the test proving nothing.
    @Test
    @DisplayName("The one where a zone written \" EUROPEAN \" is recognised, surrounding whitespace being ignored")
    void zoneNamesAreMatchedIgnoringSurroundingWhitespace() {
        assertThat(DestinationZone.forName(" EUROPEAN ")).isEqualTo(DestinationZone.EUROPEAN);
    }

    @Test
    @DisplayName("The one where a zone written \"LUNAR\" is not recognised, and the refusal names the zones that are")
    void anUnrecognisedZoneNameIsRefusedNamingTheRecognisedZones() {
        assertThatThrownBy(() -> DestinationZone.forName("LUNAR"))
                .hasMessage("Destination zone must be one of DOMESTIC, EUROPEAN, INTERNATIONAL, but was \"LUNAR\"");
    }
}
