# ADR-0003: Take roles and partner identity only from the validated token

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner
- **Related:** [specs/authorization.md](../../specs/authorization.md), [ADR-0004](0004-ownership-checks-in-service-layer.md)

## Context

A partner must only reach its own data. If the partner identity can be supplied
by the client (body, path, query, header), any partner can impersonate another.

## Decision

- Roles come from the Keycloak realm roles in the `realm_access.roles` claim.
  Only `ADMIN`, `ANALYST` and `PARTNER` are mapped (to `ROLE_ADMIN`,
  `ROLE_ANALYST`, `ROLE_PARTNER`); any other role is ignored.
- The partner identity comes **only** from the `partner_id` claim (a UUID),
  filled by a Keycloak user-attribute mapper.
- The API exposes the caller as a `CurrentUser` value (`sub`, roles,
  optional `partnerId`) built from the JWT. Services receive `CurrentUser`;
  they never read a partner id from a DTO.
- **Fail closed:** a token with role `PARTNER` and a missing or malformed
  `partner_id` gets `403` on every endpoint (it is a misconfigured account, not a
  missing resource).
- No request DTO contains `partnerId`. Unknown JSON fields are rejected
  (`400`), so `{"partnerId": "..."}` in a body cannot be silently ignored or
  bound.

## Consequences

- Impersonation via request data is impossible by construction.
- Keycloak configuration becomes security-critical: the `partner_id` attribute
  must not be user-editable (it is set by admins, and the realm's user profile
  marks it admin-only).
- Tests build tokens with explicit `partner_id` values, including missing and
  malformed ones (`AUTHZ-05`, `AUTHZ-06`).

## Alternatives considered

| Option | Why not |
|---|---|
| Partner id in the URL (`/partners/{id}/claims`) | Must still be checked against the token; invites IDOR bugs |
| Look up partner by `sub` in a local table | Extra mapping to keep in sync; claim is simpler and auditable |
| Client roles instead of realm roles | Works too; realm roles keep the realm export simpler for one client |
