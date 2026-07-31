---
paths:
  - "src/main/java/**/controller/**"
---

You are editing a REST controller — the web layer.

- Keep controllers thin: receive the request, delegate to a service, return the
  response. No business logic, no calculation, no branching on domain state.
- Accept and return JSON DTOs (request/response models). Map domain exceptions to
  HTTP status codes here — this is the only layer that knows about HTTP. Be
  explicit: 200 success, 400 validation, 401/403 auth, 404 not found.
- Use constructor injection, not field `@Autowired`. Never reach into persistence
  or framework internals from here.

These boundaries are also audited by the `architecture-guardian` agent.
