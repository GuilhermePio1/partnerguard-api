# ADR-0019: Seed data as a repeatable migration; least-privilege grants per table

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner (approved on 2026-10-12)
- **Related:** supersedes the seed-naming and grants bullets of [ADR-0015](0015-flyway-and-local-seed-data.md); [specs/fixtures.md](../../specs/fixtures.md)

## Context

ADR-0015 named seed files `V1000__seed_*.sql` so they would run after the schema.
That breaks the first time a new schema migration is added: once `V1000` is
applied, Flyway rejects `V6` as an out-of-order migration (`validate` fails with
a resolved migration that was not applied). Turning on `outOfOrder` would hide
real ordering mistakes.

ADR-0015 also granted the application `SELECT, INSERT, UPDATE` on every business
table. The API, however, only creates partners and only updates claims; policies
and contracts are read-only (ADR-0017).

## Decision

- Seed data is a **repeatable migration**: `src/main/resources/db/seed/R__seed_fixtures.sql`.
  Flyway runs repeatable migrations after all versioned ones, and again whenever
  the file's checksum changes. Every statement is an idempotent upsert
  (`INSERT ... ON CONFLICT (id) DO UPDATE`) in foreign-key order.
- The `db/seed` location is still enabled only in the `local` profile and in
  tests (unchanged from ADR-0015).
- Each table grants the application role **only what the API does with it**:

  | Table | Grant |
  |---|---|
  | `partner` | `SELECT, INSERT` |
  | `policy` | `SELECT` |
  | `contract` | `SELECT` |
  | `claim` | `SELECT, UPDATE` |
  | `audit_log` (milestone 6) | `SELECT, INSERT` |

  `DELETE` is never granted. A new endpoint that writes a table adds the grant in
  its own migration.

## Consequences

- Schema migrations keep plain increasing numbers forever; the seed can never
  block them.
- Changing the seed file re-applies it on the next local start and resets the
  fixture rows to their documented values. Rows created locally through the API
  are kept.
- A bug or an injection that reaches the database can't delete data or change
  read-only tables: `ApplicationStartupTests` asserts `permission denied` for
  `DELETE FROM claim`, `UPDATE policy` and `UPDATE partner`.

## Alternatives considered

| Option | Why not |
|---|---|
| Keep `V1000+` and enable `outOfOrder` | Hides genuinely misordered migrations |
| Seed via `CommandLineRunner` / `data.sql` | Not versioned with the schema (rejected in ADR-0015) |
| One shared grant set for all tables | More privilege than the API uses |
