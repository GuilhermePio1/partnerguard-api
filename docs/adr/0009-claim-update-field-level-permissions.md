# ADR-0009: Field-level permissions and a state machine for claim updates

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner (decided on 2026-10-12)
- **Related:** [specs/claims.md](../../specs/claims.md), [ADR-0004](0004-ownership-checks-in-service-layer.md)

## Context

The source document says ANALYST "updates claim status", PARTNER "updates its
own claims" and ADMIN "manages claims", but lists only ANALYST and PARTNER on
`PATCH /api/v1/claims/{id}` and doesn't say which fields each role may change.
Letting every role write every field would let a partner approve its own claim
(mass assignment / broken function-level authorization).

## Decision

`PATCH /api/v1/claims/{id}` is allowed for **ADMIN, ANALYST and PARTNER**, with
per-field permissions:

| Role | `status` | `description` | `amount` | Condition |
|---|---|---|---|---|
| ADMIN | ✔ | ✔ | ✔ | Status changes follow the state machine |
| ANALYST | ✔ | ✘ | ✘ | Status changes follow the state machine |
| PARTNER | ✘ | ✔ | ✔ | Own claims only, and only while `status = SUBMITTED` |

State machine: `SUBMITTED → UNDER_REVIEW → APPROVED | REJECTED`. `APPROVED`
and `REJECTED` are final. A same-value "transition" is rejected as invalid.

Evaluation order (each step stops the request):

1. Body validation (schema, unknown fields, at least one field) → `400`
2. Field permission for the caller's role → `403`
3. Ownership-scoped lookup → `404`
4. State rules (invalid transition, partner editing a non-`SUBMITTED` claim) → `409`
5. Apply, write the audit log in the same transaction → `200`

Step 2 runs before the lookup, so the `403` depends only on role and request
body, never on whether the claim exists.

## Consequences

- A partner can't change status; an analyst can't change amounts.
- Field permissions live in one place (`ClaimUpdatePolicy` in `claim/`) and
  are covered by a parameterized test over role × field (`CLM-10..18`).
- The table in the source document is extended with ADMIN. The README is
  updated.

## Alternatives considered

| Option | Why not |
|---|---|
| Follow the table literally (no ADMIN) | Contradicts "ADMIN manages claims" |
| No field restriction | A partner could approve its own claim |
| Separate endpoints per action (`/claims/{id}/approve`) | Cleaner REST-wise, but changes the documented contract |
