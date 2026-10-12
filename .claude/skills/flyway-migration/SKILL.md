---
name: flyway-migration
description: Create the next PartnerGuard Flyway migration (schema in db/migration or local seed data in db/seed) following the project's naming, constraint, index and grant conventions. Use when a feature needs a new table, column, index, constraint or grant, or when seed fixtures change.
---

# Flyway migration

Rules come from ADR-0015 and ADR-0012. Applied migrations are **never
edited**; every change is a new file.

## 1. Pick the location and number

| Kind | Folder | Name |
|---|---|---|
| Schema change | `src/main/resources/db/migration` | `V<n>__<snake_case_description>.sql` |
| Local seed data | `src/main/resources/db/seed` | `V<1000+n>__seed_<what>.sql` |

Find the highest existing `V<n>` in the folder and use `n + 1`. Don't leave
gaps, and don't use timestamps.

## 2. Write the SQL (PostgreSQL)

Checklist for every table:

- `id uuid PRIMARY KEY`. The application generates ids; no DB default.
- `NOT NULL` on every column the domain requires.
- Foreign keys named `fk_<table>_<column>` with explicit `ON DELETE RESTRICT`
  (or a justified alternative).
- `CHECK` constraints for invariants the DB can enforce, e.g.
  `CHECK (end_date >= start_date)`, `CHECK (amount > 0)`,
  `CHECK (status IN ('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED'))`.
- Unique constraints for business keys (`uq_partner_tax_id`,
  `uq_policy_policy_number`).
- **An index on every `partner_id` column** (`ix_<table>_partner_id`).
  Ownership-scoped queries rely on it.
- Money: `numeric(12,2)`. Timestamps: `timestamptz`. Free text: `text` with a
  length `CHECK` matching the contract.
- Names in `snake_case`; tables singular (`partner`, `claim`, `audit_log`).

Grants (the app role is not the schema owner):

```sql
GRANT SELECT, INSERT, UPDATE ON <table> TO partnerguard_app;
-- audit_log is append-only:
GRANT SELECT, INSERT ON audit_log TO partnerguard_app;
```

Never put secrets (passwords, tokens) in a migration. Role passwords come from
environment variables at container start, not from SQL.

## 3. Seed data

- Values must match `specs/fixtures.md` **exactly** (ids, CNPJs, statuses,
  amounts). Change the fixtures spec first if a value must change, then the
  Keycloak realm export (`partner_id` attribute) if a partner id changes.
- Store `tax_id` normalized: 14 uppercase characters, no punctuation.
- Seed files are loaded only by the `local` profile and by tests that opt in
  (`spring.flyway.locations=classpath:db/migration,classpath:db/seed`).

## 4. Verify

- `./mvnw verify`: the Testcontainers integration tests run all migrations
  from scratch and Hibernate runs `ddl-auto=validate`.
- For grants: the `AUD-04` style test (app role can't `UPDATE`/`DELETE`
  `audit_log`) still passes.
- If the entity changed, update the JPA entity, mapper, contract schema and
  spec model table in the same change.
