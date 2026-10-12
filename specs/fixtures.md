# Fixtures

The **single source** for seed data, test users and reference values. Flyway
seed migrations (`db/seed`), the Keycloak realm export, tests and the scripts
in `scripts/` must all use these exact values. Change them here first.

Seed data is loaded only in the `local` profile and in tests that opt in
([ADR-0015](../docs/adr/0015-flyway-and-local-seed-data.md)).

## Keycloak

| Item | Value |
|---|---|
| Realm | `partnerguard` |
| Issuer (local) | `http://localhost:8180/realms/partnerguard` |
| Token endpoint | `http://localhost:8180/realms/partnerguard/protocol/openid-connect/token` |
| Client | `partnerguard-cli`: public, *direct access grants* enabled (local only) |
| Audience added by mapper | `partnerguard-api` |
| Access token lifespan | 300 s |
| Signing algorithm | RS256 |

### Users (local development only)

| Username | Password | Realm role | `partner_id` attribute |
|---|---|---|---|
| `admin` | `admin` | `ADMIN` | — |
| `analyst` | `analyst` | `ANALYST` | — |
| `shop-a` | `shop-a` | `PARTNER` | `10000000-0000-4000-8000-00000000000a` |

There is no `shop-b` user. Partner B exists only as data, so we can prove that
Partner A can't see it.

## Partners

| Id | Legal name | taxId (stored) | taxId (response) | Status |
|---|---|---|---|---|
| `10000000-0000-4000-8000-00000000000a` | Oficina Alfa Ltda | `11222333000181` | `**.***.***/****-81` | `ACTIVE` |
| `10000000-0000-4000-8000-00000000000b` | Oficina Beta Ltda | `98765432000198` | `**.***.***/****-98` | `ACTIVE` |

## Policies

| Id | Number | Holder | Plate | Status |
|---|---|---|---|---|
| `20000000-0000-4000-8000-000000000001` | `POL-2026-000001` | Maria Silva | `ABC1D23` | `ACTIVE` |
| `20000000-0000-4000-8000-000000000002` | `POL-2026-000002` | João Souza | `XYZ9876` | `ACTIVE` |

## Contracts

| Id | Partner | Start | End | Terms |
|---|---|---|---|---|
| `30000000-0000-4000-8000-00000000000a` | A | 2026-01-01 | 2026-12-31 | Standard repair agreement, Partner A |
| `30000000-0000-4000-8000-00000000000b` | B | 2026-01-01 | 2026-12-31 | Standard repair agreement, Partner B |

## Claims

| Id | Partner | Policy | Status | Amount | Description |
|---|---|---|---|---|---|
| `40000000-0000-4000-8000-00000000a001` | A | `POL-2026-000001` | `SUBMITTED` | 1250.50 | Front bumper replacement |
| `40000000-0000-4000-8000-00000000a002` | A | `POL-2026-000002` | `UNDER_REVIEW` | 3400.00 | Left door and paint |
| `40000000-0000-4000-8000-00000000b001` | B | `POL-2026-000001` | `SUBMITTED` | 980.00 | Windshield replacement |

## Reference values for tests

| Purpose | Value |
|---|---|
| Id that never exists | `40000000-0000-4000-8000-000000000000` |
| Valid numeric CNPJ (not seeded) | `33.444.555/0001-81` |
| Valid alphanumeric CNPJ (not seeded) | `12.ABC.345/01DE-35` |
| Invalid CNPJ (wrong check digit) | `12.ABC.345/01DE-36` |
| Invalid CNPJ (repeated) | `00000000000000` |
| Unknown policy number | `POL-2026-999999` |
| Script payload (must be rejected) | `<script>alert(1)</script>` |
| SQL payload (must be stored verbatim) | `'; DROP TABLE claim; --` |

## Non-mutating checks

`scripts/smoke-test.*` must leave the seed data unchanged, so it only uses
requests that fail before any write: `403`, `404`, `409`, `400`. Requests that
change data belong in integration tests, which use a fresh database.
