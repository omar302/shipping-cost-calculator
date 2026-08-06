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
    // Deliberately a separate constant from the weight limit above, despite the equal
    // value: the weight limit is a business choice about scales, this one follows the
    // 2dp money invariant. Merging them would let a change to one silently move the other.
    private static final int MAX_ORDER_TOTAL_DECIMAL_PLACES = 2;
    private static final BigDecimal MIN_ORDER_TOTAL = new BigDecimal("0.00");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("75.00");
    // The cap is the surcharge boundary, so free shipping never applies to a parcel
    // charged a surcharge. Taken from the tier rather than restated, so the two cannot
    // drift apart. Note a 20.00kg parcel is already in OVER_20KG with a zero surcharge,
    // so the cap is a weight comparison, not a check on which tier the parcel is in.
    private static final BigDecimal FREE_SHIPPING_WEIGHT_CAP_KG = WeightTier.OVER_20KG.lowerBoundKg();
    private static final BigDecimal FREE = new BigDecimal("0.00");

    public ShippingCost calculate(ShippingRequest request) {
        BigDecimal weightKg = request.weightKg();
        requirePresentWeight(weightKg);
        requireSupportedWeightPrecision(weightKg);
        requireAcceptedWeight(weightKg);

        DestinationZone zone = resolveZone(request.zone());

        BigDecimal orderTotal = request.orderTotal();
        requirePresentOrderTotal(orderTotal);
        requireSupportedOrderTotalPrecision(orderTotal);
        requireAcceptedOrderTotal(orderTotal);

        BigDecimal weightBasedRate = weightBasedRate(weightKg);
        BigDecimal zoneMultiplier = zone.multiplier();
        BigDecimal zoneAdjustedRate = weightBasedRate.multiply(zoneMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        boolean freeShippingApplied = qualifiesForFreeShipping(weightKg, zone, orderTotal);
        BigDecimal totalCost = freeShippingApplied ? FREE : zoneAdjustedRate;

        return new ShippingCost(totalCost, new ShippingCost.Breakdown(
                weightBasedRate, zoneMultiplier, zoneAdjustedRate, freeShippingApplied));
    }

    private void requirePresentOrderTotal(BigDecimal orderTotal) {
        if (orderTotal == null) {
            throw new InvalidOrderTotalException("Order total is required");
        }
    }

    private void requireSupportedOrderTotalPrecision(BigDecimal orderTotal) {
        // As with parcel weight, trailing zeros are not finer precision: £75.0000 is
        // £75.00, and still reaches the threshold.
        if (orderTotal.stripTrailingZeros().scale() > MAX_ORDER_TOTAL_DECIMAL_PLACES) {
            throw new InvalidOrderTotalException(
                    "Order total must be given to at most %d decimal places, but was £%s"
                            .formatted(MAX_ORDER_TOTAL_DECIMAL_PLACES, orderTotal));
        }
    }

    // Unlike parcel weight, which must be above zero, a £0.00 order is a valid amount
    // that simply never reaches the threshold. There is no upper bound.
    private void requireAcceptedOrderTotal(BigDecimal orderTotal) {
        if (orderTotal.compareTo(MIN_ORDER_TOTAL) < 0) {
            throw new InvalidOrderTotalException(
                    "Order total must be £%s or above, but was £%s"
                            .formatted(MIN_ORDER_TOTAL, orderTotal));
        }
    }

    private boolean qualifiesForFreeShipping(
            BigDecimal weightKg, DestinationZone zone, BigDecimal orderTotal) {
        return zone == DestinationZone.DOMESTIC
                && weightKg.compareTo(FREE_SHIPPING_WEIGHT_CAP_KG) <= 0
                && orderTotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0;
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

    private void requireSupportedWeightPrecision(BigDecimal weightKg) {
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
