# ADR-0005: Deny by default

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner
- **Related:** [specs/authorization.md](../../specs/authorization.md)

## Context

New endpoints are the most common source of accidental exposure: a developer
adds a controller and forgets the rule.

## Decision

- The `SecurityFilterChain` lists the few public paths explicitly and ends with
  `.anyRequest().denyAll()` for anything outside `/api/**`. Under `/api/**`
  requests must be authenticated, then method security decides.
- Public paths: `GET /actuator/health` and, in the `local` profile only,
  `/swagger-ui.html`, `/swagger-ui/**` and `/v3/api-docs/**`.
- Every controller method must carry `@PreAuthorize` with explicit roles.
  An architecture test (ArchUnit) fails the build if a public method of a
  `@RestController` has no `@PreAuthorize` (`AUTHZ-01`).
- Actuator exposes only `health`, with `show-details: never`. All other
  actuator endpoints are not exposed over HTTP.
- Unknown paths: under `/api/**`, `401` for anonymous callers and `404` for
  authenticated ones (MVC finds no handler); outside `/api/**` and the public
  paths, `403` (`denyAll`).

## Consequences

- Forgetting a rule breaks the build instead of exposing data.
- Swagger UI is not available outside the `local` profile.

## Alternatives considered

| Option | Why not |
|---|---|
| URL rules only in `SecurityFilterChain` | Drifts from controllers; easy to miss a path |
| `@PreAuthorize` only, `anyRequest().permitAll()` | One missing annotation exposes an endpoint |
