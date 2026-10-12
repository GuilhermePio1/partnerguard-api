# ADR-0016: The security test matrix is the core test suite

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner
- **Related:** [specs/README.md](../../specs/README.md), `.claude/skills/security-tests`

## Context

"Every rule in sections 6 and 8 has at least one test proving it works and one
proving it can't be bypassed." Ad-hoc tests drift and leave gaps per endpoint.

## Decision

- Every operation in the contract has a parameterized **endpoint × caller**
  matrix test. Callers: anonymous, invalid token, expired token, wrong-audience
  token, `ADMIN`, `ANALYST`, `PARTNER` (Partner A), `PARTNER` without
  `partner_id`. The matrix also covers own resource, other partner's resource
  and missing resource.
- Expected statuses come from `x-required-roles` in the contract and the rules
  in `specs/authorization.md`. A test fails if an operation in the contract has
  no matrix entry (`AUTHZ-02`).
- Test layers:
  - **Slice/MockMvc** with `spring-security-test` JWT post-processors for the
    matrix (fast).
  - **Integration** with Testcontainers PostgreSQL for ownership queries,
    migrations and audit transactions.
  - **End-to-end auth** with a Keycloak Testcontainer (realm import) for issuer,
    audience, signature and expiry (`AUTHN-*`).
- Every test that proves a spec criterion starts its display name with the
  criterion ID (e.g. `@DisplayName("CLM-12 partner cannot change status")`).
- JaCoCo coverage is reported in CI. Gate: 80% line coverage on `common/`,
  `config/` and services.

## Consequences

- New endpoints can't be merged without matrix entries.
- Criterion IDs make the traceability from spec to test greppable.

## Alternatives considered

| Option | Why not |
|---|---|
| Hand-written tests per endpoint | Gaps go unnoticed |
| Only end-to-end tests with real Keycloak | Slow; harder to cover malformed tokens |
