---
paths:
  - "src/main/java/**/service/**"
---

You are editing the service layer — all business logic and orchestration.

- This layer must be testable with plain JUnit, no Spring context. Keep HTTP and
  persistence concerns out: never import controller-layer or web types (no
  servlet types, no Spring MVC annotations, no web DTOs).
- Money and exact decimals follow the invariant in CLAUDE.md (`BigDecimal`, scale
  2, `RoundingMode.HALF_UP` — never `double`/`float`). Construct with
  `new BigDecimal("...")` or `BigDecimal.valueOf(...)`, never `new BigDecimal(double)`.
- When a feature applies a fixed sequence of steps, follow the Processing Order
  in CLAUDE.md exactly — code and tests must not reorder it.
- If a rule keeps accreting cases inside a private method, that is design
  pressure: extract it into its own value object or service with its own seam,
  and test it directly at the lower tier.

These boundaries are also audited by the `architecture-guardian` agent.
