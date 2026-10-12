---
name: security-tests
description: Write or extend PartnerGuard security tests, namely the endpoint × caller authorization matrix, ownership (404 for other partners), field-level permission, JWT validation, rate-limit and injection tests. Use when adding or changing an endpoint, when a spec criterion with prefix AUTHN/AUTHZ/RL/HRD/CLM/PRT/CTR/POL lacks a test, or when asked to prove a security rule holds or can't be bypassed.
---

# Security tests

The security matrix is the core test suite (ADR-0016). Every rule needs a test
proving it **holds** and one proving it **can't be bypassed**.

## Inputs to read first

- `contracts/openapi.yaml`: the operation's `x-required-roles` and status codes.
- `specs/authorization.md`: the access matrix and fail-closed rules.
- The feature spec (`specs/<feature>.md`): criterion IDs.
- `specs/fixtures.md`: ids, users and reference values. **Use only these.**

## 1. Matrix entry (always)

Add the operation to the parameterized matrix test
(`src/test/java/com/partnerguard/security/SecurityMatrixTest.java`). One row
per caller:

| Caller | How to build it (MockMvc) |
|---|---|
| anonymous | no `Authorization` header |
| `ADMIN` | `jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))` plus claim `sub` |
| `ANALYST` | same with `ROLE_ANALYST` |
| `PARTNER` (Partner A) | `ROLE_PARTNER` + claim `partner_id = 10000000-0000-4000-8000-00000000000a` |
| `PARTNER` without `partner_id` | `ROLE_PARTNER`, no claim → always `403` |
| `PARTNER` malformed `partner_id` | claim `"abc"` → always `403` |
| unmapped role | only `ROLE_SUPERUSER` → `403` |

Expected status: `401` for anonymous, `403` for roles not in
`x-required-roles`, otherwise the success status. The matrix test also asserts
that every `operationId` in the contract has rows. Don't skip that assertion
(`AUTHZ-02`).

Use a test helper (`SecurityTestTokens`) instead of repeating JWT builders.
Mapping from `realm_access.roles` itself is tested once, in the JWT converter
unit test, not in every matrix row.

## 2. Ownership (resources with an owner)

Integration test (Testcontainers PostgreSQL + seed):

1. Partner A reads its own resource → `200`.
2. Partner A reads Partner B's resource → `404`.
3. Partner A reads `40000000-0000-4000-8000-000000000000` (never exists) → `404`.
4. Assert the bodies of 2 and 3 are **identical except `instance`**.
5. Lists: no item has `partnerId ≠ A`; a `?partnerId=<B>` query parameter
   has no effect.
6. A service-level test calls the service directly with
   `OwnershipScope.partner(PARTNER_A)` and B's id and expects
   `ResourceNotFoundException`. This proves the rule doesn't depend on the
   controller. See `ContractServiceTests` / `ClaimServiceTests`; ids come from
   `support.Fixtures`.
7. An `ownership_denied` log event is emitted (use `OutputCaptureExtension`).

## 3. Field-level permissions (writes)

Parameterized over role × field from the spec table. Assert the evaluation
order: a forbidden field returns `403` **even on a resource the caller doesn't
own** (proves the check runs before the lookup); an owned resource in the
wrong state returns `409`; an unknown property such as `partnerId` returns `400`.

## 4. Bypass attempts to include where relevant

- Owner id smuggled in body, query, path or a custom header.
- Token with `alg: none`, `HS256`, wrong `iss`, missing `aud`, expired by
  more than the 30 s skew (E2E-auth with a Keycloak Testcontainer, or a local
  JWKS stub that signs test tokens).
- Token in `?access_token=` instead of the header.
- Forged tokens with random or victim `sub` to dodge or drain rate limits
  (`RL-04`, `RL-05`).
- Script payloads (`<script>`, `<img onerror>`), control characters, SQL
  payloads (`'; DROP TABLE claim; --`): script → `400`; SQL as plain text →
  stored verbatim and the schema intact afterwards.
- Problem Details bodies never contain stack traces, `SQL`, class names or
  unmasked CNPJs. Use the shared `ProblemAssertions` helper.

## 5. Conventions

- `@DisplayName("<CRITERION-ID> <what it proves>")` on every test. One ID per
  test method, or per parameterized row via `name = "{0}"`.
- Slice tests (`@WebMvcTest`) for the matrix; integration tests for anything
  touching queries, transactions, the audit log or Flyway.
- Rate-limit tests set small limits via `@TestPropertySource` and use a fake
  clock. They never call `Thread.sleep`.
- Don't mock the ownership query in integration tests. The query *is* the
  rule.
- After writing: `./mvnw verify` and `grep -rn "<ID>" src/test` for every
  criterion you were covering.
