package com.example.shipping.model;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum DestinationZone {

    DOMESTIC(new BigDecimal("1.0")),
    EUROPEAN(new BigDecimal("1.5")),
    INTERNATIONAL(new BigDecimal("2.5"));

    private final BigDecimal multiplier;

    DestinationZone(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public BigDecimal multiplier() {
        return multiplier;
    }

    public static DestinationZone forName(String name) {
        try {
            return valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unrecognised) {
            throw new IllegalArgumentException(
                    "Destination zone must be one of %s, but was \"%s\"".formatted(recognisedNames(), name));
        }
    }

    private static String recognisedNames() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
