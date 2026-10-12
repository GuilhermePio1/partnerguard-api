# ADR-0017: Add `GET /api/v1/claims/{id}`; defer admin CRUD

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner (decided on 2026-10-12)
- **Related:** [specs/claims.md](../../specs/claims.md), [ADR-0004](0004-ownership-checks-in-service-layer.md)

## Context

The ownership rule in the source document uses `GET /claims/{id}` as its
example, but that endpoint is missing from the endpoint table. The document
also says ADMIN "manages" contracts, policies and claims without listing any
write endpoint for them.

## Decision

- Add `GET /api/v1/claims/{id}` for `ADMIN`, `ANALYST` and `PARTNER`, with
  ownership enforced exactly like `GET /api/v1/contracts/{id}`.
- Do **not** add create/update/delete endpoints for contracts, policies or
  claims in this scope. Those records come from seed data. Admin CRUD is listed
  as future work in the README.

## Consequences

- The documented ownership example is directly testable (`CLM-03`).
- Scope stays as documented; the only write endpoints are
  `POST /api/v1/partners` and `PATCH /api/v1/claims/{id}`.

## Alternatives considered

| Option | Why not |
|---|---|
| Full admin CRUD now | Multiplies endpoints, tests and audit cases without demonstrating new security ideas |
| Leave the example untestable | The rule would rely on other endpoints only |
