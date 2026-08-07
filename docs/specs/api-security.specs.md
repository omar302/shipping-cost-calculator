# API Security

**As a platform administrator, I want the shipping API secured with API key authentication, so that only authorised clients can access the service.**

## Rules and Examples

### Rule: Must refuse a request that does not carry a recognised API key

The key travels in an `X-API-Key` header. A key is recognised when it is one of those configured; anything else is refused, whether or not it looks like a key.

| `X-API-Key` header | Outcome |
|---|---|
| a key configured as USER | Accepted |
| absent | Refused — 401 |
| present but not configured | Refused — 401 |
| present but empty | Refused — 401 |

The first row is the counter-example: holding a configured key is what "authenticated" means. The empty row names no key at all, and is refused by the same rule as an unknown one.

---

### Rule: Must grant access according to the role the key carries

ADMIN is a superset of USER, not a separate lane — an administrator can do everything a user can.

| Key role | Endpoint | Outcome |
|---|---|---|
| USER | `POST /api/shipping/calculate` | Allowed |
| ADMIN | `POST /api/shipping/calculate` | Allowed |
| ADMIN | `GET /api/admin/rates` | Allowed |
| USER | `GET /api/admin/rates` | Refused — 403 |

The last row is the counter-example, and the point of having roles: a valid key is not a licence for every endpoint. It is refused with 403 rather than 401 — the caller is known, but not permitted.

---

### Rule: Must refuse to start when a key is configured with an unrecognised role

There are exactly two roles. A configuration naming anything else is a mistake, and it must be a loud one: a key carrying an unrecognised role would still authenticate, quietly granting whatever the catch-all allows while silently losing the access it was meant to have.

- **Example:** The one where a key is configured as `ADMNI` and the application refuses to start.
- **Counter-example:** The one where keys are configured as `USER` and `ADMIN` and the application starts normally.

---

### Rule: Must leave the API documentation reachable without a key

Documentation is public so an integrator can read it before they have been issued anything.

- **Example:** The one where Swagger UI is fetched with no `X-API-Key` header and is served.
- **Example:** The one where the documentation declares that the endpoints need an `X-API-Key` header — reading it before being issued a key is the point, and documentation that omits the requirement does not serve that.
- **Counter-example:** The one where the calculate endpoint is fetched with no key and is refused — being unauthenticated opens the documentation and nothing else.

---

### Rule: Must authenticate and authorise before applying any shipping rule

An unauthenticated caller learns nothing about their payload, and an unauthorised one learns nothing about the endpoint.

- **Example:** The one where a request carries no key *and* a weight of 0kg, and is refused as unauthenticated — the weight is never examined.
- **Example:** The one where a USER key reaches an administrative path that does not exist, and is refused as not permitted rather than told it is not there — authorisation is settled before the request is routed, so which administrative endpoints exist is not disclosed to a caller who may not use them.
- **Counter-example:** The one where a valid USER key carries a weight of 0kg and *is* rejected for the weight, with the existing explanation — once authorised, every shipping rule applies exactly as before.

---

### Rule: Must explain a refusal without disclosing the key

A refusal uses the same RFC 9457 problem detail as every other rejection, but names only the rule. It deliberately does **not** name the value that broke it: a rejected key is often a real credential, and echoing it writes it into responses, proxy logs and error trackers.

| Situation | Status | `detail` |
|---|---|---|
| No key given | 401 | "API key is required" |
| Key not recognised | 401 | "API key is not recognised" |
| Key given but empty | 401 | "API key is not recognised" |
| Key not permitted here | 403 | "API key is not permitted to use this endpoint" |

The rejected key appears nowhere in the response. The middle row is the departure from the existing contract, which everywhere else names the offending value.

---

## Resolved decisions (for implementation)

- **Transport:** an `X-API-Key` header. No query-parameter or bearer-token alternative.
- **Key configuration:** a key → role map in `application.properties`, e.g. `shipping.api-keys.7f3a91c4=USER`. Several keys may share a role, so a key can be issued per client, rotated, or revoked without affecting the others.
- **Roles:** `USER` and `ADMIN` only. ADMIN includes every USER permission. The two names are a closed set the configuration is bound to, so an unrecognised role is refused when the application starts rather than at the first request that needed it.
- **`GET /api/admin/rates`:** **out of scope except as an authorisation boundary.** This spec pins who may reach it — 200 for ADMIN, 403 for USER — and asserts nothing about its body. Its content needs its own spec; do not invent a rates response shape.
- **Refusal body:** RFC 9457 problem detail, titled `"Unauthorised"` (401) and `"Forbidden"` (403). The key is never echoed, masked or truncated into the response.
- **Absent vs empty:** an absent header is "required"; a present-but-empty one is "not recognised", matching how `destination-zones.specs.md` treats an empty zone.
- **Processing order:** authentication then authorisation run *before* step 1 of the existing chain, so the 12 shipping steps are unchanged and simply never reached by a refused request.
- **Public paths:** Swagger UI, plus the OpenAPI document it fetches to render — the UI is useless without it.
- **Breaking change:** every existing acceptance test must send a valid key or receive 401. This is the third such change, after `zone` and `orderTotal`.
- **Out of scope:** key issuance, rotation mechanics, expiry, rate limiting, audit logging, and any role beyond the two named.
