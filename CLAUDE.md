# CLAUDE.md

PartnerGuard API: a secure Spring Boot 4 / Java 21 REST API for insurance
policies and partner repair shops. Keycloak (OIDC/JWT), PostgreSQL, Flyway,
Bucket4j. The point of the project is **security that is specified, decided and
tested**. Treat every security rule as a requirement with a test, not a
nice-to-have.

Status: milestone 6 done (audit log, security headers, correlation ids,
verified-TLS startup check, error catalog and security-event tests). Next:
milestone 7, CI scans (HRD-10/11), contract lint (DOC-03) and docs
(`README.md` §13).

Controllers get scope, role and partner id **only** from `CallerContext`
(`common.caller.JwtCallerContext`, built from the validated JWT).

## Where things live

| Path | What | Change it when |
|---|---|---|
| `specs/*.md` | Rules + acceptance criteria with IDs (`CLM-12`) | Behavior changes. **First** |
| `contracts/openapi.yaml` | HTTP contract, source of truth (`x-required-roles`) | Any endpoint, schema or status change. **Before code** |
| `docs/adr/` | Decisions and rejected alternatives | A design/security decision is made (skill `new-adr`) |
| `specs/fixtures.md` | Seed ids, users, reference CNPJs | Seed or Keycloak users change |
| `README.md` | Current design, including all security decisions | User-visible behavior or security design changes |
| `scripts/` | `.sh` + `.ps1` pairs: env-up/down, get-token, smoke-test, rate-limit-check | Keep both versions in sync |
| `partnerguard-api.md` | Original brief. Don't edit | Never |

Workflow: **spec → contract → tests (fail) → code → ADR → README**.
Skills: `new-feature`, `security-tests`, `flyway-migration`, `new-adr`,
`dependency-cve` (fix CVEs; remove stale overrides after a Boot upgrade).

## Non-negotiable rules

1. The partner identity comes **only** from the JWT `partner_id` claim. No
   request DTO has `partnerId`; unknown JSON properties → `400`.
2. `PARTNER` + missing or malformed `partner_id` → `403` everywhere (fail closed).
3. Ownership lives in the **service** via scoped queries
   (`findByIdAndPartnerId`). Another partner's resource → `404`, with a body
   identical to a missing one. Never `403` for that.
4. `@PreAuthorize` on **every** controller method (implementation, not the
   generated interface). Deny by default; only `/actuator/health` is public
   (plus Swagger in the `local` profile).
5. Claim PATCH: ADMIN all fields; ANALYST `status` only; PARTNER
   `description`/`amount` on own `SUBMITTED` claims. Order: `400` → `403` →
   `404` → `409`.
6. Queries are parameterized. No string concatenation or `String.format` in
   JPQL/SQL. Sort only from the contract allow-list.
7. Free text is plain text (`<`, `>` and control characters rejected).
8. `taxId` (CNPJ, numeric **or alphanumeric**) is masked as
   `**.***.***/****-NN` in every response and never logged.
9. Every error is RFC 7807 `application/problem+json`, including `401`/`403`
   from Spring Security and `429` from the rate-limit filters. No stack traces,
   SQL, class names.
10. Rate limits key on the **verified** `sub` only. The pre-auth filter keys on
    IP (ADR-0006).
11. Every write writes one audit row in the same transaction; `audit_log` is
    append-only.
12. No secrets in the repo. Local-only credentials stay in Compose/`.env.example`.
13. Don't invent business rules. If the brief or specs are silent, add an
    *Open question* to the spec and ask.

## Tests

- Every criterion ID has a test whose `@DisplayName` starts with the ID.
- Every operation in the contract has rows in the security matrix test.
- Integration tests use Testcontainers (PostgreSQL; Keycloak for `AUTHN-*`).
  Don't mock ownership queries.
- Rate-limit tests use small configured limits and a fake clock, never `sleep`.

## Commands

```bash
./mvnw verify                       # generate from contract, compile, all tests (needs Docker)
./mvnw spring-boot:run              # local profile (seed + .env), needs scripts/env-up first
scripts/env-up.sh | scripts/smoke-test.sh | scripts/rate-limit-check.sh
```

- Build with **JDK 21**. If your default JDK differs, set `JAVA_HOME` for every
  Maven call.
- Generated code: `target/generated-sources/openapi` (packages
  `com.partnerguard.api` and `com.partnerguard.api.model`). Never edit it.
- Integration tests extend `support.PostgresIntegrationTest` (a shared
  Testcontainers PostgreSQL with the same two roles as Compose).
  `support.ApiIntegrationTest` authenticates every request as ADMIN by
  default (`SecurityTestTokens`); `support.KeycloakIntegrationTest` adds a
  shared Keycloak container (same image tag as Compose; keep them in sync).
  `support.PostgresIntegrationTest` activates the `test` profile (without it
  the verified-TLS startup check stops every context), caps each context's
  pool at 3 connections, and sets the rate limits very high so tests don't
  trip them. Tests that need a reachable limit (a `429`) extend
  `support.SmallRateLimitsIntegrationTest`; its subclasses share one context,
  so don't add configuration to them.
  `support.SchemaOwner` changes rows the app role can't write (e.g. partner
  status); it commits outside the test transaction, so undo it in `@AfterEach`.
- If port 5432 is taken, set `POSTGRES_PORT` in `.env`; Compose and the `local`
  profile both read it. Machine-specific notes go in `CLAUDE.local.md` (git-ignored).

## Environment notes

- The developer's machine is Windows. Shells: PowerShell 5.1 and Git Bash.
  In `.ps1` files use `curl.exe` (bare `curl` is an alias for
  `Invoke-WebRequest`), avoid `&&`/`||`, pass JSON bodies through a temp file,
  and keep scripts compatible with both PowerShell 5.1 and 7.
- `.sh` files must keep LF line endings and must not depend on `jq`.
- Write all docs and code in English.
