# ADR-0013: Free-text fields are plain text; SQL-like input is data

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner (approved on 2026-10-12)
- **Related:** [specs/hardening.md](../../specs/hardening.md)

## Context

The API returns JSON only, but values like `Claim.description` may later be
rendered by some HTML frontend (stored XSS). Malicious payloads must be
"rejected or neutralized", and the design must say which, per field.

## Decision

- **Script/markup payloads are rejected.** Free-text fields (`legalName`,
  `description`, and `terms` if it ever becomes writable) use the `@PlainText`
  constraint: no `<` or `>`, no control characters except tab, LF and CR.
  The contract's `PlainText` pattern mirrors it. Violations → `400`.
- **SQL-like payloads are accepted as data** when they are otherwise valid
  plain text (e.g. `'; DROP TABLE claim; --`). All queries are parameterized
  (Spring Data derived queries, JPQL with named parameters, or `Criteria`), so
  such strings are stored and returned verbatim, with no effect. Tests prove both
  (`HRD-04`, `HRD-06`, `HRD-07`, `CLM-24`).
- No string concatenation or `String.format` when building JPQL/SQL. Native
  queries are only allowed with bind parameters. An ArchUnit/grep check in CI
  flags `createQuery(` / `createNativeQuery(` calls that receive a
  concatenated string (`HRD-08`).
- Sort parameters are matched against an allow-list (enumerated in the
  contract), so property names from the request never reach a query.
- Responses are `application/json` only, with `X-Content-Type-Options: nosniff`
  and a restrictive CSP (see the hardening spec).

## Consequences

- Rejecting `<`/`>` prevents some legitimate text (e.g. "amount > 100").
  That's acceptable for repair-shop descriptions; revisit if users complain.
- Validation does not replace output encoding in any future frontend.

## Alternatives considered

| Option | Why not |
|---|---|
| HTML-sanitize on input (OWASP Java HTML Sanitizer) | Silently changes data; harder to test and explain |
| Accept anything, rely on frontend encoding | No frontend exists to rely on; defense in depth |
| Reject SQL keywords | Unnecessary with parameterized queries; causes false positives |
