# ADR-0004: Enforce ownership in the service layer and answer 404 for other partners' resources

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner
- **Related:** [specs/authorization.md](../../specs/authorization.md), [ADR-0003](0003-roles-and-partner-identity-from-token.md)

## Context

Role checks alone allow a `PARTNER` to read any claim by guessing its id
(broken object-level authorization, OWASP A01 / API1). Returning `403` for a
foreign resource confirms that it exists.

## Decision

- Ownership is enforced in the **service layer**, so it applies to every caller
  (controllers, future jobs, other services).
- For `PARTNER` callers, lookups are scoped in the query itself:
  `findByIdAndPartnerId(id, currentUser.partnerId())`. List endpoints use
  `findAllByPartnerId(partnerId, pageable)`. Internal roles use the unscoped
  query. A shared `OwnershipScope` helper in `common/` picks the query; services
  never load-then-compare in the controller.
- A resource that belongs to another partner produces the **same `404` Problem
  Details body** as a resource that does not exist (same `type`, `title`,
  `detail`; no id echoed in `detail`).
- Response time should not differ noticeably between the two cases; both run
  one indexed query.
- Every ownership-denied lookup is logged as a security event (WARN,
  `event=ownership_denied`, `sub`, resource type and id) without revealing it to
  the caller.

## Consequences

- No existence oracle for partners.
- Internal users still get `404` for truly missing resources.
- Role failures still return `403` (the caller can't do this at all), which
  is not an existence leak because it doesn't depend on the resource.
- Every scoped endpoint needs three tests: own resource → `200`, other
  partner's → `404`, missing → `404` with identical body (`AUTHZ-08..10`).

## Alternatives considered

| Option | Why not |
|---|---|
| `@PostAuthorize` on the controller | Loads the entity first; easy to forget on new callers; returns `403` |
| Filter in the controller | Bypassed by any other caller of the service |
| `403` for foreign resources | Confirms existence (rejected by the source document) |
| Hibernate filters / row-level security in PostgreSQL | Stronger, but heavier to set up and test for this scope; possible future ADR |
