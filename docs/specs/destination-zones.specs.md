# Destination Zones

**As an online retailer, I want shipping costs adjusted by destination zone, so that international deliveries reflect higher logistics costs.**

## Rules and Examples

### Rule: Must multiply the weight-based rate by the multiplier for the destination zone

The weight-based rate is the whole figure produced by the weight-tiers spec — bracket base rate *plus* any over-20kg surcharge. Domestic is ×1.0, European ×1.5, International ×2.5. Only the resulting money is rounded, to 2 decimal places, half up.

| Parcel weight | Zone | Weight-based rate | Multiplier | Zone-adjusted rate |
|---|---|---|---|---|
| 0.50 kg | DOMESTIC | £2.99 | ×1.0 | £2.99 |
| 2.00 kg | EUROPEAN | £4.99 | ×1.5 | £7.49 |
| 12.00 kg | INTERNATIONAL | £8.99 | ×2.5 | £22.48 |
| 25.00 kg | EUROPEAN | £11.49 | ×1.5 | £17.24 |
| 50.00 kg | INTERNATIONAL | £23.99 | ×2.5 | £59.98 |

The 25.00kg row shows the surcharge being multiplied too: £2.50 of surcharge is scaled along with the £8.99 base.

---

### Rule: Must reject a destination zone that is not one of the three recognised names

Zone names are matched ignoring case and surrounding whitespace, so any capitalisation of a recognised name is accepted however it is padded. There are no aliases — the value must otherwise be the zone name. A rejected zone returns a validation error and no shipping cost.

| Destination zone | Outcome |
|---|---|
| `"EUROPEAN"` | Accepted — ×1.5 |
| `"european"` | Accepted — ×1.5 |
| `"International"` | Accepted — ×2.5 |
| `" EUROPEAN "` | Accepted — ×1.5 |
| `"LUNAR"` | Rejected |
| `"EU"` | Rejected |
| `""` | Rejected |

The `"european"` and `"International"` rows are the point of the rule: lower case and mixed case are the same zone, and the padded row shows the same for stray whitespace. `"EU"` is the counter-example — a recognisable abbreviation is not an alias — and the empty value names no zone at all.

---

### Rule: Must reject a request that does not carry a destination zone

An omitted field and an explicit `null` both mean "no zone given" and are treated identically. The explanation names the missing field rather than describing a zone that was never supplied.

- **Example:** The one where a request carries `weightKg` but no `zone` and is rejected with the explanation "Destination zone is required".
- **Example:** The one where `zone` is explicitly `null` and is rejected with the same explanation.
- **Counter-example:** The one where the zone is present but empty (`""`) — also rejected, but by the recognised-names rule, with its own explanation naming the value.

---

### Rule: Must explain why a rejected destination zone was rejected

A rejection carries a `detail` field naming the rule that was broken and the zone that broke it, matching the existing rejection contract.

- **Example:** The one where a `"LUNAR"` zone is rejected with the explanation "Destination zone must be one of DOMESTIC, EUROPEAN, INTERNATIONAL, but was \"LUNAR\"".

---

### Rule: Must validate the parcel weight before the destination zone

When a request breaks both a weight rule and a zone rule, only the weight rejection is reported. One rejection carries one explanation.

- **Example:** The one where a request has a weight of 0kg and a zone of `"LUNAR"` and is rejected with "Parcel weight must be above 0kg and at most 50kg, but was 0.00kg" — the zone is never examined.
- **Counter-example:** The one where the weight is valid at 2.00kg and only the zone is wrong — the zone rejection is reported, since no weight rule was broken.

---

### Rule: Must report the multiplier applied and the resulting rate alongside the base rate

The caller sees what was charged and why. The response still carries no total — that awaits the free-shipping spec.

- **Example:** The one where a 25.00kg EUROPEAN parcel returns a base rate of £11.49, a multiplier of 1.5, and a zone-adjusted rate of £17.24.
- **Counter-example:** The one where a 0.50kg DOMESTIC parcel returns a base rate of £2.99, a multiplier of 1.0, and a zone-adjusted rate of £2.99 — the multiplier is reported even when it changes nothing.

---

## Resolved decisions (for implementation)

- **Multiplier scope:** applied to the base rate *plus* the over-20kg surcharge — the whole weight-based figure, not the bracket rate alone.
- **Multipliers:** DOMESTIC ×1.0, EUROPEAN ×1.5, INTERNATIONAL ×2.5.
- **Rounding:** the zone-adjusted rate is rounded to 2 decimal places, half up, after multiplication. The weight-based rate is rounded first, as the weight-tiers spec requires, so rounding happens at both stages.
- **Missing zone:** required. An omitted field and an explicit `null` are equivalent; the explanation is "Destination zone is required".
- **Zone matching:** case-insensitive, and surrounding whitespace is ignored. `EUROPEAN`, `european` and `" EUROPEAN "` are the same zone. No aliases.
- **Processing order:** the weight rules (present → precision → range) run first, then zone (present → recognised), then pricing (base rate → surcharge → zone multiplier).
- **Response shape:** `breakdown` gains `zoneMultiplier` and `zoneAdjustedRate` next to the existing `baseRate`.
- **Breaking change:** `zone` is now required, so existing requests carrying only `weightKg` are rejected. The weight-tiers acceptance tests must send `"zone": "DOMESTIC"`.
- **Out of scope:** free-shipping thresholds and any `totalCost` field.
