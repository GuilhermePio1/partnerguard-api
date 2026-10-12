# ADR-0015: Flyway for schema; seed data only in the local profile

- **Status:** Accepted; seed naming and grants superseded by [ADR-0019](0019-repeatable-seed-and-least-privilege-grants.md)
- **Date:** 2026-10-12
- **Deciders:** Project owner
- **Related:** [specs/fixtures.md](../../specs/fixtures.md), `.claude/skills/flyway-migration`

## Context

The schema must be reproducible from scratch with `docker compose up` and
`./mvnw spring-boot:run`. Test users exist in Keycloak, and their
`partner_id` must point at real rows.

## Decision

- Schema migrations live in `src/main/resources/db/migration`, named
  `V<n>__<snake_case_description>.sql` with `n` a plain increasing integer.
- Seed data lives in `src/main/resources/db/seed`, named `V1000__seed_*.sql`
  and up, so it always runs after the schema. That location is added to
  `spring.flyway.locations` **only** in the `local` profile and in tests that
  need it.
- `spring.jpa.hibernate.ddl-auto=validate`. Hibernate never changes the schema.
- Applied migrations are never edited; a fix is a new migration.
- Every table has a primary key, `NOT NULL` where the domain requires it,
  foreign keys with explicit `ON DELETE` behavior, and an index on every
  `partner_id` column (ownership-scoped queries).
- The application's database role is not the schema owner. A migration grants
  it `SELECT, INSERT, UPDATE` on business tables and `SELECT, INSERT` on
  `audit_log`.

## Consequences

- A production-like profile never contains test data.
- Seed UUIDs and Keycloak's `partner_id` attribute must stay in sync. The
  fixtures spec is the single place that lists them.

## Alternatives considered

| Option | Why not |
|---|---|
| Liquibase | Equivalent; Flyway is in the source document |
| `data.sql` / `CommandLineRunner` seed | Not versioned with the schema; easy to leak into other profiles |
