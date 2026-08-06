package com.example.shipping.model;

import java.math.BigDecimal;

// The zone arrives as the name the caller wrote, not a DestinationZone: resolving it
// during deserialization would reject an unrecognised zone before the weight is ever
// validated, and the weight is validated first.
public record ShippingRequest(BigDecimal weightKg, String zone, BigDecimal orderTotal) {
}
