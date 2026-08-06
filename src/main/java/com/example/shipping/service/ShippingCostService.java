package com.example.shipping.service;

import com.example.shipping.model.DestinationZone;
import com.example.shipping.model.ShippingCost;
import com.example.shipping.model.ShippingRequest;
import com.example.shipping.model.WeightTier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class ShippingCostService {

    private static final BigDecimal SURCHARGE_PER_KG = new BigDecimal("0.50");
    private static final BigDecimal MIN_WEIGHT_KG = new BigDecimal("0");
    private static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("50");
    private static final int MAX_WEIGHT_DECIMAL_PLACES = 2;

    public ShippingCost calculate(ShippingRequest request) {
        BigDecimal weightKg = request.weightKg();
        requirePresentWeight(weightKg);
        requireSupportedPrecision(weightKg);
        requireAcceptedWeight(weightKg);

        DestinationZone zone = resolveZone(request.zone());

        BigDecimal weightBasedRate = weightBasedRate(weightKg);
        BigDecimal zoneMultiplier = zone.multiplier();
        BigDecimal zoneAdjustedRate = weightBasedRate.multiply(zoneMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        return new ShippingCost(
                new ShippingCost.Breakdown(weightBasedRate, zoneMultiplier, zoneAdjustedRate));
    }

    private BigDecimal weightBasedRate(BigDecimal weightKg) {
        WeightTier tier = WeightTier.forWeight(weightKg);
        BigDecimal rate = tier.baseRate();

        if (tier == WeightTier.OVER_20KG) {
            BigDecimal excessKg = weightKg.subtract(tier.lowerBoundKg());
            rate = rate.add(excessKg.multiply(SURCHARGE_PER_KG));
        }

        return rate.setScale(2, RoundingMode.HALF_UP);
    }

    private void requirePresentWeight(BigDecimal weightKg) {
        if (weightKg == null) {
            throw new InvalidParcelWeightException("Parcel weight is required");
        }
    }

    private DestinationZone resolveZone(String zone) {
        if (zone == null) {
            throw new InvalidDestinationZoneException("Destination zone is required");
        }
        try {
            return DestinationZone.forName(zone);
        } catch (IllegalArgumentException unrecognised) {
            throw new InvalidDestinationZoneException(unrecognised.getMessage());
        }
    }

    private void requireSupportedPrecision(BigDecimal weightKg) {
        // Trailing zeros are not finer precision: 2.5000kg is 2.50kg, and accepted.
        if (weightKg.stripTrailingZeros().scale() > MAX_WEIGHT_DECIMAL_PLACES) {
            throw new InvalidParcelWeightException(
                    "Parcel weight must be given to at most %d decimal places, but was %skg"
                            .formatted(MAX_WEIGHT_DECIMAL_PLACES, weightKg));
        }
    }

    private void requireAcceptedWeight(BigDecimal weightKg) {
        if (weightKg.compareTo(MIN_WEIGHT_KG) <= 0 || weightKg.compareTo(MAX_WEIGHT_KG) > 0) {
            throw new InvalidParcelWeightException(
                    "Parcel weight must be above %skg and at most %skg, but was %skg"
                            .formatted(MIN_WEIGHT_KG, MAX_WEIGHT_KG, weightKg));
        }
    }
}
