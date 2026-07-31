---
paths:
  - "src/main/java/**/model/**"
---

You are editing the model layer — domain objects, request/response DTOs, enums,
and value objects.

- Hold data, not logic. No business rules, calculation, or orchestration — those
  belong in the service layer.
- Immutable where practical; prefer records for value objects and DTOs.
- Monetary fields are `BigDecimal`. No framework coupling in domain value objects.
