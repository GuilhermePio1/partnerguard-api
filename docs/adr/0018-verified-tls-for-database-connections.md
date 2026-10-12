# ADR-0018: Require verified TLS (`sslmode=verify-full`) for database connections outside local development

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner (approved on 2026-10-12)
- **Related:** [specs/hardening.md](../../specs/hardening.md#database-connections-adr-0018), [ADR-0015](0015-flyway-and-local-seed-data.md)

## Context

The application sends database credentials and every query over the JDBC
connection. pgjdbc's default `sslmode` is `prefer`, which silently falls back to
plaintext and verifies nothing. `require` encrypts but accepts any certificate,
so an attacker between the application and the database can present their own
server. The pgjdbc 42.7.14 security release (CVE-2026-107314) shows that attack
in practice: a man in the middle can request cleartext password authentication.
Its release notes recommend `sslmode=verify-full` with a trusted CA regardless
of that fix. The brief says "TLS expected in production" for the API but is
silent about the database link.

## Decision

- Outside the `local` and `test` profiles, the datasource and Flyway URLs must
  contain `sslmode=verify-full` and `sslrootcert=<trusted CA file>`. Every other
  `sslmode` is rejected.
- A startup check enforces it and **fails closed**: the application refuses to
  start, naming the offending property and never echoing credentials.
- `local` and `test` are the only exemptions. They connect to `localhost` or a
  Testcontainers instance. Tests run with the `test` profile active.
- The CA certificate is deployment configuration (file path), never committed.

## Consequences

- An attacker in the network path can neither read the traffic nor impersonate
  the database server.
- A production-like run without TLS configuration can't start by accident.
- Tests need `@ActiveProfiles("test")` (or an equivalent shared annotation);
  milestone 6 adds it together with the check (`HRD-13..17`).
- Running the jar locally without a profile now requires `--spring.profiles.active=local`.

## Alternatives considered

| Option | Why not |
|---|---|
| `sslmode=require` | Encrypts but doesn't authenticate the server; vulnerable to MITM |
| `sslmode=verify-ca` | Checks the chain but not the hostname; any server with a cert from the same CA passes |
| Document only, no startup check | Relies on every operator getting it right; deny by default says otherwise |
| Exempt by host (`localhost`) instead of by profile | A sidecar or tunnel on localhost could hide a plaintext hop; profiles are explicit |
