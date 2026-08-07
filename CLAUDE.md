# Shipping Cost Calculator

A Spring Boot REST service that calculates shipping cost from parcel weight, destination zone and order total, waiving it entirely for qualifying domestic orders. Built with a Spec-Driven Development (SDD) workflow: business rules live in `docs/specs/` and every rule is driven into existence by a failing test before any production code is written.

## Project Overview

- **Build tool:** Gradle (`./gradlew`)
- **Framework:** Spring Boot 4.1.0 (Spring Framework 7), Java 25 — see `build.gradle`
- **Testing:** JUnit 6.1.0 (Jupiter) + AssertJ
- **Persistence:** none. The calculation is pure; there is no database.

## Architecture

Standard Spring Boot layered architecture — controller calls service, service uses models:

- **controller/** — `ShippingController` (the single endpoint, delegation only) and `ShippingExceptionHandler` (`@RestControllerAdvice` mapping domain exceptions to HTTP status codes). No business logic.
- **service/** — `ShippingCostService` holds all validation and arithmetic, testable with plain JUnit and no Spring context. `InvalidParcelWeightException`, `InvalidDestinationZoneException` and `InvalidOrderTotalException` signal a rejected weight, zone and order total.
- **model/** — `ShippingRequest`, `ShippingCost` (with nested `Breakdown`), and the `WeightTier` and `DestinationZone` enums. Plain Java: no Spring, no framework imports.
- **config/** — the security filter chain and its collaborators: `SecurityConfig`, `ApiKeyProperties`, the `Role` enum, `ApiKeyAuthenticationFilter`, and the two refusal writers. Infrastructure, not domain — no business rules live here.

Layer rules: controllers never contain business logic; services never import controller-layer or web types; models hold data, not logic. Per-layer detail lives in `.claude/rules/`, and the `architecture-guardian` agent enforces the boundaries.

## Processing Order

Authentication and authorisation run first, in the security filter chain, before any of the steps below. A refused request is never parsed or priced, so an unauthenticated caller learns nothing about their payload. That ordering is structural — the filter chain runs before `DispatcherServlet` — not something the steps below enforce.

The calculation then applies a fixed sequence. Code and tests must follow this exact order:

1. **Weight present** — a missing or null `weightKg` is rejected.
2. **Weight precision** — more than two decimal places is rejected, measured *after* stripping trailing zeros (`2.5000kg` is `2.50kg` and is accepted).
3. **Weight range** — the weight must be above 0kg and at most 50kg. Zero is **not** valid, unlike an order total.
4. **Zone present** — a missing or null `zone` is rejected.
5. **Zone recognised** — the value must name one of the three zones, matched ignoring case and surrounding whitespace. No aliases.
6. **Order total present** — a missing or null `orderTotal` is rejected.
7. **Order total precision** — more than two decimal places is rejected, again measured *after* stripping trailing zeros (`£75.0000` is `£75.00` and is accepted).
8. **Order total range** — £0.00 or above. Unlike weight, **zero is valid** — it simply never qualifies — and there is no upper bound.
9. **Base rate** — the weight bracket determines the rate (brackets are lower-bound inclusive).
10. **Surcharge** — above 20kg, add £0.50 per excess kilogram, pro-rata, then round to 2dp `HALF_UP`.
11. **Zone multiplier** — multiply the whole weight-based rate (base *plus* surcharge) by the zone multiplier — DOMESTIC ×1.0, EUROPEAN ×1.5, INTERNATIONAL ×2.5 — then round to 2dp `HALF_UP` again. Rounding therefore happens at two stages.
12. **Free shipping** — a DOMESTIC parcel of at most 20.00kg on an order of £75.00 or more costs £0.00. Both thresholds are inclusive; all three conditions must hold.

Validation precedes calculation, so an invalid request is never priced. The field groups run in order — all weight rules, then all zone rules, then all order-total rules — so a request breaking several is rejected for the earliest, and one rejection carries one explanation.

That ordering is why `ShippingRequest.zone` is a raw `String` resolved inside the service, not a `DestinationZone` bound by Jackson. Resolving it during deserialization rejects an unrecognised zone before the weight is ever seen, which inverts steps 1–5 and cannot be tested below the acceptance tier. Do not reintroduce a `DestinationZone` deserializer or `@JsonCreator`.

The free-shipping weight cap is taken from `WeightTier.OVER_20KG.lowerBoundKg()`, not restated as its own `20`, because the cap *is* the surcharge boundary — a parcel charged a surcharge never ships free. Note that a 20.00kg parcel already sits in `OVER_20KG` with a zero surcharge, so the cap must be a weight comparison, not a check on which tier the parcel is in.

## Monetary / Numeric Precision

For money and other exact decimal values:

- Use `BigDecimal` — never `double` or `float`. Construct from a `String` literal.
- Scale: 2 decimal places. Rounding: `RoundingMode.HALF_UP`.
- Compare with `compareTo()`, not `equals()` (`BigDecimal` is scale-sensitive). In tests, AssertJ's `isEqualByComparingTo`.
- Assert exact values in tests — no floating-point tolerance.

## API Design

One endpoint. JSON in, JSON out.

```
POST /api/shipping/calculate
Content-Type: application/json
X-API-Key: <a configured key>

{ "weightKg": 25.00, "zone": "EUROPEAN", "orderTotal": 100.00 }

Response 200:
{ "totalCost": 17.24,
  "breakdown": { "baseRate": 11.49, "zoneMultiplier": 1.5, "zoneAdjustedRate": 17.24,
                 "freeShippingApplied": false } }
```

All three of `weightKg`, `zone` and `orderTotal` are **required**. `baseRate` is the whole weight-based figure — bracket rate plus any over-20kg surcharge — `zoneAdjustedRate` is that figure times the multiplier, and `totalCost` is what the customer pays: the zone-adjusted rate, or `0.00` when free shipping applies.

When free shipping applies, `zoneAdjustedRate` still reports the rate that was **waived** rather than being zeroed, so the caller can see what it was worth. `freeShippingApplied` is always present, `false` included.

A rejected request returns `400` as an RFC 9457 problem detail (`application/problem+json`), whose `detail` names the rule that was broken and the value that broke it:

```
Response 400:
{ "title":  "Invalid parcel weight",
  "status": 400,
  "detail": "Parcel weight must be above 0kg and at most 50kg, but was 0.00kg" }
```

(`type` is also present, defaulting to `about:blank`.) A rejected zone and a rejected order total use the same shape, titled `"Invalid destination zone"` and `"Invalid order total"`.

A value the body reader cannot parse must name the field it came from — the request has two numeric fields, so `ShippingExceptionHandler` resolves the field from the Jackson path rather than blaming a fixed one. Anything it cannot attribute keeps the generic `"Request body could not be read"`.

Status codes are explicit: `200` success, `400` validation error, `401` no or unrecognised key, `403` key not permitted here. The endpoint has no not-found case.

`GET /api/admin/rates` exists as an **authorisation boundary only** — ADMIN reaches it, USER gets 403 — and returns no body. Its content needs its own spec; do not invent a rates response shape.

**One deliberate exception to the contract above.** A refused *key* is never named in the `detail`, unlike every other rejection, which echoes the value that broke the rule. A rejected key is usually a real credential, and echoing it writes it into responses, proxy logs and error trackers. The three refusal explanations are fixed: "API key is required", "API key is not recognised", "API key is not permitted to use this endpoint".

## Testing Conventions

Three tiers. Prove each rule at the lowest tier that can:

- **Acceptance** (`src/test/java/.../acceptance/`, `*IT`): `@SpringBootTest` + `@AutoConfigureMockMvc` with `MockMvcTester`. One class per feature, one `@Nested` class per spec rule.
- **Service** (`src/test/java/.../service/`, `*Test`): plain JUnit, constructs the service directly. The workhorse tier — validation and arithmetic live here.
- **Value object** (`src/test/java/.../model/`, `*Test`): plain JUnit for classification and boundaries.

Conventions:

- Test method names describe the business rule (`weightJustAboveZeroIsAccepted`), not a number (`testCalculate3`).
- `@DisplayName` uses plain-language, Example-Mapping descriptions: `"The one where a 25kg parcel has a base rate of £11.49"`.
- **Exhaustive enumeration goes at the lowest tier; the acceptance tier keeps one headline example per rule.** A comment at the acceptance test names the class holding the full table. The overlap on the headline example is intentional — it is the executable specification.
- A rule whose examples form a table becomes one `@ParameterizedTest`, not one test per row.
- Run the suite with `./gradlew test`. The `/accept` and `/tdd` workflows run it for you, so each red and green step stays visible.

## Spec Files

Business rules live in `docs/specs/` as markdown, one file per feature (`<feature>.specs.md`). Every rule has at least one test. Currently:

- **`weight-tiers.specs.md`** — seven rules (brackets, surcharge, range, precision, missing weight, non-numeric weight, and rejection explanations). Implemented.
- **`destination-zones.specs.md`** — six rules (multiplier, unrecognised zone, missing zone, rejection explanation, weight-before-zone ordering, and reporting the multiplier). Implemented.
- **`free-shipping.specs.md`** — seven rules (qualifying conditions, reporting the total and the flag, missing order total, invalid amount, non-numeric order total, rejection explanation, and weight-and-zone-before-order-total ordering). Implemented.
- **`api-security.specs.md`** — six rules (unrecognised key, role-based access, unrecognised role at startup, public documentation, security before any shipping rule, and refusal explanations). Implemented.

When behaviour is decided during implementation, the spec is updated in the same cycle. A test that traces to no rule is drift.

Use `/discover` to turn a feature idea into a spec, then `/accept` and `/tdd` to implement it, and `/review` before committing.

## API Documentation (Swagger/OpenAPI)

springdoc-openapi is included in `build.gradle`. When the app runs, Swagger UI is served at `/swagger-ui.html`, generated from the controllers, and is reachable **without a key** along with the `/v3/api-docs` document behind it. Add `@Operation` / `@ApiResponse` annotations for richer descriptions.

`OpenApiConfig` declares the `apiKey` security scheme and requires it globally, so the document tells an integrator a key is needed and which header carries it. That is the point of keeping the docs public — reading them before being issued a key only helps if they state the requirement.

## Security

API key authentication, per `api-security.specs.md`. Every endpoint except the documentation needs an `X-API-Key` header naming a configured key.

Keys are a key → role map, so several keys can share a role and one can be rotated or revoked without affecting the others:

```properties
shipping.api-keys.7f3a91c4=USER
shipping.api-keys.e91c7a2b=ADMIN
```

**No keys are committed**, so a freshly cloned app refuses every request with 401 until some are configured. That is the intended default, not a fault to debug.

- **Roles:** `USER` and `ADMIN` only, bound to the `Role` enum. An unrecognised role fails at **startup** rather than at the first request that needed it — a key carrying a junk role would otherwise still authenticate, silently keeping catch-all access while losing the access it was meant to have. Do not widen this back to a `String`.
- **Public paths:** Swagger UI and the OpenAPI document it fetches. The UI cannot render without the document, so opening one without the other achieves nothing.
- **401 vs 403** is decided by which path a request takes, not by branching: an unauthenticated caller reaches `ApiKeyAuthenticationEntryPoint`, an authenticated-but-forbidden one reaches `ApiKeyAccessDeniedHandler`.
- **Refusals never echo the key.** This is the one deliberate exception to the rejection contract below — see API Design.

Tests supply their own keys via `@SpringBootTest(properties = "shipping.api-keys.test-user-key=USER")` rather than relying on `application.properties`, so the suite never depends on what a deployment configures.

## The `.claude/` Toolchain

- **`.claude/settings.json`** — one hook: a `PreToolUse` file guard blocking edits to sensitive files. Tests are run by the workflows, not by hooks, so red/green stays visible.
- **`.claude/hooks/protect-files.sh`** — blocks edits to sensitive files via `PROTECTED_PATTERNS`.
- **`.claude/skills/`** — the workflow: `/discover` (rule → example → counter-example → questions), `/accept` (one failing acceptance test for one rule), `/tdd` (one RED → GREEN → REFACTOR cycle, then stop), `/review` (read-only architecture and traceability review). Also `/claudius` and `/commit-summary`.
- **`.claude/rules/`** — per-layer rules auto-loaded when editing that layer: `controller-rules.md`, `service-rules.md`, `model-rules.md`, `test-rules.md`.
- **`.claude/agents/`** — `architecture-guardian` (layer boundaries), `spec-compliance` (rules have tests, precision, API contract), `mutation-analyst` (test-suite strength via PIT), `config-auditor`.
- **`.claude/commands/`** — `quality-check.md`.
