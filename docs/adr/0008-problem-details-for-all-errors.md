# ADR-0008: RFC 7807 Problem Details for every error, including security errors

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner
- **Related:** [specs/errors.md](../../specs/errors.md)

## Context

Default error responses leak information (stack traces, SQL messages, class
names) and differ by source. Spring Security's default `401`/`403` responses
are not Problem Details, and neither is a `429` written by a servlet filter.

## Decision

- Every error body is `application/problem+json` with `type`, `title`,
  `status`, optional `detail` and `instance`. Validation errors add `errors[]`
  (`field`, `message`).
- `type` values are stable URIs from the catalog in `specs/errors.md`
  (`https://problems.partnerguard.example/<slug>`).
- Sources and handlers:
  - Controllers and services → `@RestControllerAdvice` extending
    `ResponseEntityExceptionHandler` (`spring.mvc.problemdetails.enabled=true`).
  - Authentication failures → custom `AuthenticationEntryPoint` (`401` +
    `WWW-Authenticate: Bearer ...`).
  - Authorization failures → custom `AccessDeniedHandler` (`403`).
  - Rate limiting → written directly by the rate-limit filters (`429`).
  - Anything unhandled → generic `500` with a fixed `detail`; full exception
    logged server-side with a correlation id.
- Responses never contain stack traces, exception messages from libraries, SQL,
  constraint names or class names. `spring.web.error.include-stacktrace=never`,
  `include-message=never`, `include-binding-errors=never`. (Editorial fix,
  2026-10-12: the original text used the pre-Boot-4 `server.error.*` names,
  which Boot 4 no longer binds. The decision is unchanged.)
- Constraint violations from the database (e.g. duplicate tax ID) are mapped
  to `409` with a domain-level `detail`, never the constraint name.

## Consequences

- One error format for clients and tests. A shared test helper asserts the
  shape and the absence of leaked internals (`ERR-*`).
- The three non-MVC sources need their own code paths; each has a test.

## Alternatives considered

| Option | Why not |
|---|---|
| Spring's default `/error` JSON | Different shape; can leak messages |
| Custom error envelope | Reinvents a standard |
