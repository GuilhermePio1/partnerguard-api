# ADR-0012: Write the audit log synchronously in the same transaction

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner
- **Related:** [specs/audit.md](../../specs/audit.md)

## Context

Every write operation must be recorded (OWASP A09). An audit entry that can be
lost while the change is committed, or kept while the change is rolled back,
is unreliable.

## Decision

- Services call `AuditService.record(action, resource)` inside the same
  `@Transactional` method as the write. If the audit insert fails, the write is
  rolled back.
- Entry fields: `id`, `userId` (JWT `sub`), `action` (e.g. `PARTNER_CREATED`,
  `CLAIM_UPDATED`), `resource` (`<type>:<uuid>`), `timestamp` (UTC, from an
  injected `Clock`), and `details`: a JSON list of changed field names, never
  their values.
- The `audit_log` table is append-only for the application: the API's database
  user has `INSERT, SELECT` on it, but no `UPDATE`/`DELETE` (granted in a
  Flyway migration).
- **Failed access attempts** (`401`, `403`, ownership `404`, `429`) are not
  audit rows. They are structured security log events (WARN, `event=...`,
  `sub` or IP, method, path) so a flood can't fill the audit table.
- There is no HTTP endpoint for reading the audit log in this scope.

## Consequences

- Strong consistency between data and audit trail.
- Write latency includes one extra insert, which is negligible here.

## Alternatives considered

| Option | Why not |
|---|---|
| Async event / `@TransactionalEventListener(AFTER_COMMIT)` | Entry can be lost after commit |
| Hibernate Envers | Records entity history, not who did what through which action |
| Audit failed attempts in the table | Attackers could fill the table; logs are the right sink |
