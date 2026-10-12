# Contracts

> This spec is about the **Contract** domain entity (an agreement between the
> insurer and a partner), not the OpenAPI contract in `contracts/`.

## Scope

`GET /api/v1/contracts`, `GET /api/v1/contracts/{id}`. Related:
[ADR-0004](../docs/adr/0004-ownership-checks-in-service-layer.md),
[contract](../contracts/openapi.yaml) tag `contracts`.

## Model

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | |
| `partnerId` | UUID | FK to `partner`, indexed |
| `startDate` | date | |
| `endDate` | date | `endDate ≥ startDate` (DB check constraint) |
| `terms` | text | Read-only in this scope |

## Rules

1. Read-only. Contracts are created by seed data (ADR-0017).
2. List: internal roles see all; `PARTNER` sees `partnerId = token.partner_id`
   only. Default sort `startDate,desc`.
3. Detail: ownership enforced. Another partner's contract → `404`.

## Acceptance criteria

| ID | Given / When / Then | Type |
|---|---|---|
| CTR-01 | Given `admin`, when `GET /contracts`, then both seeded contracts are returned | Integration |
| CTR-02 | Given `shop-a`, when `GET /contracts`, then only `30000000-…000a` is returned and `totalElements = 1` | Integration |
| CTR-03 | Given `shop-a`, when `GET /contracts/30000000-…000b`, then `404` | Integration |
| CTR-04 | Given `shop-a`, when `GET /contracts/30000000-…000a`, then `200` | Integration |
| CTR-05 | Given `analyst`, when `GET /contracts/40000000-…000000` (never exists), then `404` | Integration |
| CTR-06 | Given `analyst`, when `POST /contracts`, then not `2xx` (no such operation) | Slice |
| CTR-07 | `totalElements` for `shop-a` doesn't reveal other partners' counts | Integration |

## Open questions

- None.
