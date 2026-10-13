# Specs

Behavioral specifications for PartnerGuard, one file per feature area. They
turn the README and the ADRs into **testable acceptance criteria**.

## How the pieces fit

```
partnerguard-api.md (original brief)
        │
        ▼
README.md (current design) ──► docs/adr/ (why)
        │
        ▼
specs/*.md (what must be true, criterion IDs) ──► contracts/openapi.yaml (HTTP shape)
        │                                                   │
        ▼                                                   ▼
src/test/** (one or more tests per criterion) ◄── generated interfaces / controllers
```

Workflow for any change: **spec → contract → tests → code → ADR (if a decision
was made) → README (if user-visible behavior changed)**.

## Index

| Spec | Prefix | Milestone |
|---|---|---|
| [api-conventions.md](api-conventions.md) | `API`, `DOC`, `ARCH` | 2, 7 |
| [fixtures.md](fixtures.md) | — | 1–3 |
| [authentication.md](authentication.md) | `AUTHN` | 3 |
| [authorization.md](authorization.md) | `AUTHZ` | 4 |
| [rate-limiting.md](rate-limiting.md) | `RL` | 5 |
| [partners.md](partners.md) | `PRT` | 2, 4, 6 |
| [contracts.md](contracts.md) | `CTR` | 2, 4 |
| [claims.md](claims.md) | `CLM` | 2, 4, 6 |
| [policies.md](policies.md) | `POL` | 2, 4 |
| [audit.md](audit.md) | `AUD` | 6 |
| [errors.md](errors.md) | `ERR` | 6 |
| [hardening.md](hardening.md) | `HRD` | 6 |

## Format

Each spec has:

1. **Scope**: what the spec covers and links to the ADRs.
2. **Rules**: the normative statements (MUST / MUST NOT).
3. **Acceptance criteria**: a table with a stable **ID**, a Given/When/Then
   statement and the test type. IDs are never reused or renumbered. A removed
   criterion is marked ~~struck through~~ with a reason.
4. **Open questions**: things the brief doesn't decide. They are **not**
   implemented until answered. An answer becomes a rule (and an ADR if it is a
   decision).

## Traceability

- Every criterion has at least one test whose display name starts with the ID:
  `@DisplayName("CLM-12 partner cannot change claim status")`.
- `grep -rn "CLM-12" src/test` must find it.
- A criterion that proves a rule *holds* should have a sibling that proves
  it *can't be bypassed* (source document, section 11).

Test types used in the tables:

| Type | Meaning |
|---|---|
| Unit | Plain JUnit 5 + Mockito, no Spring context |
| Slice | MockMvc through the full filter chain (`@SpringBootTest` + `@AutoConfigureMockMvc`), with `spring-security-test` JWT post-processors or a stub `JwtDecoder`; no real identity provider |
| Integration | `@SpringBootTest` + Testcontainers PostgreSQL |
| E2E-auth | Integration + Keycloak Testcontainer with the realm export |
| Arch | ArchUnit rule |
| Script | Covered by `scripts/smoke-test.*` or `scripts/rate-limit-check.*` against the local stack |
