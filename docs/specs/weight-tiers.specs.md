# Weight Tiers

**As an online retailer, I want shipping costs calculated on parcel weight using tiered pricing, so that heavier parcels are charged appropriately.**

## Rules and Examples

### Rule: Must charge a base rate determined by which weight bracket the parcel falls into

This is the base rate only — the first stage of the calculation. Zone multipliers and free-shipping are covered by their own specs.

| Parcel weight | Base rate |
|---|---|
| 0.50 kg | £2.99 |
| 2.00 kg | £4.99 |
| 12.00 kg | £8.99 |
| 0.99 kg | £2.99 |
| 1.00 kg | £4.99 |
| 5.00 kg | £8.99 |
| 20.00 kg | £8.99 |

Brackets are lower-bound inclusive: the boundary rows show 1.00kg and 5.00kg falling into the *higher* bracket, while 0.99kg stays in the lowest. Exactly 20.00kg attracts no surcharge.

---

### Rule: Must add £0.50 for every kilogram above 20kg, charged pro-rata

The excess is the weight above 20kg, charged at exactly £0.50 per kilogram with no rounding of the weight itself. Only the resulting money is rounded, to 2 decimal places, half up.

| Parcel weight | Excess over 20kg | Surcharge | Base rate |
|---|---|---|---|
| 25.00 kg | 5.00 kg | £2.50 | £11.49 |
| 22.50 kg | 2.50 kg | £1.25 | £10.24 |
| 50.00 kg | 30.00 kg | £15.00 | £23.99 |
| 20.01 kg | 0.01 kg | £0.01 | £9.00 |
| 20.00 kg | none | £0.00 | £8.99 |

The 20.01kg row is the rounding boundary: £0.005 rounds half up to £0.01. The 20.00kg row is the counter-example — nothing is above 20, so no surcharge applies.

---

### Rule: Must reject a parcel weight outside the range above 0kg up to and including 50kg

A rejected parcel returns a validation error and no shipping cost.

| Parcel weight | Outcome |
|---|---|
| 0.00 kg | Rejected |
| −1.50 kg | Rejected |
| 50.01 kg | Rejected |
| 0.01 kg | Accepted — £2.99 |
| 50.00 kg | Accepted — £23.99 |

The 0.01kg and 50.00kg rows are the counter-examples: a tiny but real parcel and the heaviest permitted parcel are both priced normally.

---

### Rule: Must reject a parcel weight given to more than two decimal places

- **Example:** The one where a parcel submitted at 2.005kg is rejected as invalid, because weights are accepted only to the nearest 10 grams.
- **Counter-example:** The one where a parcel weighs exactly 22.53kg — two decimal places — and is priced at £10.26.
- **Counter-example:** The one where a parcel is submitted as 2.5000kg and is accepted at £4.99 — trailing zeros are not finer precision, since 2.5000kg *is* 2.50kg.

---

### Rule: Must reject a request that does not carry a parcel weight

An omitted field and an explicit `null` both mean "no weight given" and are treated identically. The explanation names the missing field rather than describing a weight that was never supplied.

- **Example:** The one where a request arrives as `{}` and is rejected with the explanation "Parcel weight is required".
- **Example:** The one where `weightKg` is explicitly `null` and is rejected with the same explanation.
- **Counter-example:** The one where the weight is present but zero — also rejected, but by the accepted-range rule, with its own explanation naming the range.

---

### Rule: Must reject a parcel weight that is not a number

A non-numeric weight is rejected the same way as every other invalid weight, so the endpoint has one error contract.

- **Example:** The one where `weightKg` is submitted as `"heavy"` and is rejected with the explanation "Parcel weight must be a number, but was \"heavy\"".
- **Counter-example:** The one where `weightKg` is submitted as the JSON string `"2.50"` rather than the number `2.50`, and is accepted and priced at £4.99 — a quoted number is still a number.
- **Counter-example:** The one where the body is malformed JSON rather than a bad value — rejected too, but with the generic explanation "Request body could not be read", because no single field can be blamed.

---

### Rule: Must explain why a rejected parcel was rejected

A rejection carries a `detail` field naming the rule that was broken and the weight that broke it, so the caller can correct the request without guessing.

- **Example:** The one where a 0kg parcel is rejected with the explanation "Parcel weight must be above 0kg and at most 50kg, but was 0.00kg".
- **Example:** The one where a 2.005kg parcel is rejected with the explanation "Parcel weight must be given to at most 2 decimal places, but was 2.005kg".

---

## Resolved decisions (for implementation)

- **Fractional excess over 20kg:** charged pro-rata, not rounded up to a whole kilogram.
- **Maximum weight:** 50kg, inclusive — exactly 50.00kg is accepted and priced at £23.99, above it is rejected.
- **Weight precision:** two decimal places; finer precision is a validation error. Measured after trailing zeros are stripped, so 2.5000kg is treated as 2.50kg and accepted.
- **Rejection response:** RFC 9457 problem detail (`application/problem+json`), whose `detail` field carries the explanation.
- **Missing weight:** an omitted field and an explicit `null` are equivalent; the explanation is "Parcel weight is required".
- **Non-numeric weight:** rejected through the same problem-detail contract as every other invalid weight. This one fails during JSON deserialization, before the service runs, so it cannot travel on the `InvalidParcelWeightException` path.
- **Quoted numbers:** accepted — `"2.50"` is coerced to 2.50 and priced normally.
- **Unreadable request body:** a value of the wrong type names the field and the offending value ("Parcel weight must be a number, but was \"heavy\""). Any other unbindable body — malformed JSON, a truncated request — falls back to the generic "Request body could not be read", because there is no single field to blame.
- **Out of scope:** zone multipliers and free-shipping thresholds.
