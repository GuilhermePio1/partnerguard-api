# OWASP hardening

## Scope

Input validation, injection, sensitive data, security headers and
configuration. Related:
[ADR-0010](../docs/adr/0010-cnpj-validation-and-masking.md),
[ADR-0013](../docs/adr/0013-plain-text-input-and-xss.md),
[ADR-0005](../docs/adr/0005-deny-by-default.md),
[ADR-0018](../docs/adr/0018-verified-tls-for-database-connections.md).

## Rules

### Input and injection

1. Every request DTO is validated (`@Valid`). Constraints come from the contract
   plus custom ones: `@Cnpj`, `@PlainText`.
2. Free text rejects `<`, `>` and control characters (except tab, LF, CR).
3. Queries are Spring Data derived queries, JPQL with named parameters or
   Criteria. No string concatenation or `String.format` to build a query.
4. Sort fields come from the contract's allow-list only.

### Sensitive data

5. `taxId` is masked in every response and never logged (ADR-0010).
6. Tokens, `Authorization` headers and request bodies are never logged.
7. TLS is expected in front of the API outside local development. The API
   itself serves HTTP on `localhost` only.

### Headers (every response)

| Header | Value |
|---|---|
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` (Swagger UI paths in `local`: a relaxed policy that allows `'self'` scripts/styles) |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `no-referrer` |
| `Cache-Control` | `no-store` on `/api/**` |
| `Strict-Transport-Security` | Only when served over HTTPS (Spring default) |

No `Server` or `X-Powered-By` version banners.

### Configuration

8. `spring.web.error.include-stacktrace=never`, `include-message=never`,
   `include-binding-errors=never`, `include-exception=false`. (Spring Boot 4
   name. The old `server.error.*` keys are no longer bound and are silently
   ignored.)
9. Actuator: `management.endpoints.web.exposure.include=health`,
   `management.endpoint.health.show-details=never`.
10. No secrets in the repository: DB password, Keycloak admin password and
    similar come from environment variables. Local defaults live only in
    `docker-compose.yml`, `.env.example` and `application-local.yml`, clearly
    marked local-only. `application.yml` has no password defaults.
11. CORS is disabled (no browser frontend in scope).
12. CI runs OWASP Dependency-Check (`./mvnw dependency-check:check`) and fails
    on CVSS ≥ 7. The NVD API key comes from the repository secret
    `NVD_API_KEY`, never from a file; without it the job fails instead of
    running unchecked. A false positive is suppressed only in
    `dependency-check-suppressions.xml`, with the reason next to it.

### Database connections ([ADR-0018](../docs/adr/0018-verified-tls-for-database-connections.md))

13. Outside the `local` and `test` profiles, every PostgreSQL connection, both
    the application datasource and Flyway, MUST use TLS with full server
    verification: `sslmode=verify-full` plus `sslrootcert` pointing to the
    trusted CA certificate. `disable`, `allow`, `prefer`, `require` and
    `verify-ca` are rejected, because only `verify-full` checks both the
    certificate chain and the server hostname.
14. **Fail closed:** in any profile other than `local` and `test`, the
    application refuses to start when `spring.datasource.url` or
    `spring.flyway.url` lacks `sslmode=verify-full` or `sslrootcert`. The error
    names the property and never echoes the URL's credentials.
15. The `local` profile and the tests connect to `localhost` or a container
    without TLS. Tests must run with the `test` profile active, so a missing
    profile can't silently disable the check.

Example (non-local):

```
jdbc:postgresql://db.internal:5432/partnerguard?sslmode=verify-full&sslrootcert=/etc/partnerguard/pg-ca.crt
```

### Secret scanning

16. CI scans the whole git history for secrets with gitleaks; a finding fails
    the build. A value that only looks like a secret (a fake token in a test)
    is listed by fingerprint in `.gitleaksignore`, with the reason next to it.

## Acceptance criteria

| ID | Given / When / Then | Type |
|---|---|---|
| HRD-01 | Given any `/api/**` response, then all headers in the table are present with the listed values | Slice |
| HRD-02 | Given `/actuator/health`, then the same headers except `Cache-Control` | Slice |
| HRD-03 | Given any response, then no `Server` or `X-Powered-By` header reveals a version | Integration |
| HRD-04 | Given `<img src=x onerror=alert(1)>` in `legalName` or `description`, then `400` | Slice |
| HRD-05 | Given a description with a tab and a newline, then accepted | Slice |
| HRD-06 | Given a description containing a NUL or `\u001b`, then `400` | Slice |
| HRD-07 | Given SQL payloads in free text and in query parameters (`' OR '1'='1`, `1; DROP TABLE claim`), then they are either `400` (pattern/format) or stored verbatim with no side effect; the schema is unchanged afterwards | Integration |
| HRD-08 | Static check: no `createQuery`/`createNativeQuery`/`@Query` value built by concatenation or `String.format` | Arch |
| HRD-09 | Given a CORS preflight from `https://evil.example`, then no `Access-Control-Allow-Origin` header | Slice |
| HRD-10 | Given the repository, then secret scanning (e.g. gitleaks in CI) finds nothing | CI |
| HRD-11 | Given CI, then OWASP Dependency-Check reports no CVSS ≥ 7 vulnerability, or the build fails | CI |
| HRD-12 | Given the local stack, `scripts/smoke-test.*` sees `X-Content-Type-Options: nosniff` and a masked `taxId` | Script |
| HRD-13 | Given no `local`/`test` profile and a datasource URL with `sslmode=require` (or no `sslmode`), then the application fails to start with an error naming `spring.datasource.url` | Unit |
| HRD-14 | Given no `local`/`test` profile and a Flyway URL without `sslrootcert`, then the application fails to start with an error naming `spring.flyway.url` | Unit |
| HRD-15 | Given no `local`/`test` profile and both URLs with `sslmode=verify-full&sslrootcert=...`, then the check passes | Unit |
| HRD-16 | Given a failing check on a URL containing `password=secret`, then neither the error nor the log contains `secret` | Unit |
| HRD-17 | Given the `local` or `test` profile and a URL without `sslmode`, then the check passes | Unit |

## Open questions

- Encrypting `taxId` at rest (column-level or pgcrypto)? Not required by the
  brief.
