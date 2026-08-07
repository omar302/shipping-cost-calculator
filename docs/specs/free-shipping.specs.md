# Free Shipping

**As an online retailer, I want to waive shipping on larger domestic and European orders of manageable size, so that customers are encouraged to consolidate purchases into a single delivery.**

## Rules and Examples

### Rule: Must waive the shipping cost only for a domestic or European parcel of at most 20.00 kg whose order total reaches £50.00

All three conditions must hold. Both thresholds are inclusive. The weight cap is exactly the point where the over-20kg surcharge begins, so a parcel carrying a surcharge never qualifies.

| Zone | Parcel weight | Order total | Total cost |
|---|---|---|---|
| DOMESTIC | 2.00 kg | £60.00 | £0.00 |
| EUROPEAN | 2.00 kg | £60.00 | £0.00 |
| DOMESTIC | 20.00 kg | £60.00 | £0.00 |
| DOMESTIC | 20.01 kg | £60.00 | £9.00 |
| DOMESTIC | 2.00 kg | £50.00 | £0.00 |
| DOMESTIC | 2.00 kg | £49.99 | £4.99 |
| EUROPEAN | 2.00 kg | £49.99 | £7.49 |
| INTERNATIONAL | 2.00 kg | £1,000.00 | £12.48 |

The 20.00 kg and £50.00 rows are the boundaries — reaching either threshold still qualifies. The 20.01 kg row is a counter-example that pays in full despite a £60.00 order, and the £49.99 rows a penny short of qualifying. The two EUROPEAN rows together are the point of the rule: Europe qualifies on the same terms as home, not unconditionally. The INTERNATIONAL row is the exclusion — no order total, however large, and no weight, however light, earns free shipping outside those two zones.

---

### Rule: Must report the total cost the customer pays and whether free shipping was applied

The response gains a `totalCost` alongside the existing breakdown, and the breakdown gains `freeShippingApplied`. A waived rate is still reported, so the caller can see what free shipping was worth.

- **Example:** The one where a 2.00 kg DOMESTIC order of £60.00 reports a total cost of £0.00 and free shipping applied, while the breakdown still shows the £4.99 zone-adjusted rate that was waived.
- **Counter-example:** The one where a 2.00 kg INTERNATIONAL order of £100.00 reports a total cost of £12.48 and free shipping *not* applied — the flag is reported even when it changes nothing, as the zone multiplier already is.

---

### Rule: Must reject a request that does not carry an order total

An omitted field and an explicit `null` both mean "no order total given" and are treated identically.

- **Example:** The one where a request carries `weightKg` and `zone` but no `orderTotal` and is rejected with the explanation "Order total is required".
- **Counter-example:** The one where the order total is present but £0.00 — accepted, and simply never reaches the threshold.

---

### Rule: Must reject an order total that is not a valid amount

Precision is measured *after* stripping trailing zeros, as parcel weight already is.

| Order total | Outcome |
|---|---|
| £60.00 | Accepted |
| £0.00 | Accepted — never qualifies |
| £50.0000 | Accepted — the same as £50.00 |
| £75.005 | Rejected — finer than 2 decimal places |
| -£1.00 | Rejected — below £0.00 |

There is no upper bound; an order can be any size. The £0.00 row is the boundary that separates "valid but never qualifying" from "rejected".

---

### Rule: Must reject an order total that is not a number

A non-numeric order total is rejected the same way as every other invalid order total, and the explanation names the order total rather than any other field.

- **Example:** The one where `orderTotal` is submitted as `"abc"` and is rejected with the explanation "Order total must be a number, but was \"abc\"".
- **Counter-example:** The one where `orderTotal` is submitted as the JSON string `"80.00"` rather than the number `80.00`, and is accepted and ships a 2.00 kg domestic parcel free — a quoted number is still a number.

---

### Rule: Must explain why a rejected order total was rejected

A rejection names the rule that was broken and the value that broke it, matching the existing rejection contract.

- **Example:** The one where £75.005 is rejected with the explanation "Order total must be given to at most 2 decimal places, but was £75.005".
- **Example:** The one where -£1.00 is rejected with the explanation "Order total must be £0.00 or above, but was £-1.00".

---

### Rule: Must validate the parcel weight and destination zone before the order total

The existing order holds and the new rules join the end of the chain. One rejection carries one explanation.

- **Example:** The one where a 0 kg parcel to `"LUNAR"` with no order total is rejected with "Parcel weight must be above 0kg and at most 50kg, but was 0.00kg" — neither the zone nor the order total is examined.
- **Example:** The one where a 2.00 kg parcel to `"LUNAR"` with an order total of -£1.00 is rejected for its zone — the weight is valid, so the zone is reached, and the order total is never examined.
- **Counter-example:** The one where the weight and zone are both valid and only the order total is wrong — that rejection is the one reported, since no earlier rule was broken.

---

## Resolved decisions (for implementation)

- **Qualifying conditions:** all three must hold — DOMESTIC or EUROPEAN zone, parcel weight at most 20.00 kg, order total at least £50.00. Both thresholds inclusive.
- **Weight cap:** 20.00 kg, which is exactly the no-surcharge range. A parcel carrying an over-20kg surcharge never qualifies.
- **Threshold:** £50.00. Fixed, not configurable or per-customer.
- **Zone restriction:** DOMESTIC and EUROPEAN qualify on identical terms; INTERNATIONAL never qualifies at any order total or weight. The qualifying set is a property of the zone, so adding a zone to it is a change here and in `DestinationZone`, not a new threshold.
- **Missing order total:** required. An omitted field and an explicit `null` are equivalent; the explanation is "Order total is required".
- **Order total validation:** at most 2 decimal places (measured after stripping trailing zeros), and £0.00 or above. No upper bound.
- **Non-numeric order total:** rejected with an explanation naming the order total. A value the body reader cannot parse must name the field it came from, not whichever numeric field happens to be checked first.
- **Response shape:** `totalCost` joins `breakdown` at the top level; `breakdown` gains `freeShippingApplied`. The waived `zoneAdjustedRate` is preserved, not zeroed.
- **Processing order:** weight (present → precision → range) → zone (present → recognised) → order total (present → precision → range) → pricing (base rate → surcharge → zone multiplier → free-shipping check).
- **Breaking change:** `orderTotal` is now required, so every existing acceptance test must add one — the second such change, after `zone`.
- **Out of scope:** partial or tiered discounts, per-customer thresholds, and any currency other than pounds.
