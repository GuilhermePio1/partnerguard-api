# Claims

## Scope

`GET /api/v1/claims`, `GET /api/v1/claims/{id}`, `PATCH /api/v1/claims/{id}`.
Related: [ADR-0004](../docs/adr/0004-ownership-checks-in-service-layer.md),
[ADR-0009](../docs/adr/0009-claim-update-field-level-permissions.md),
[ADR-0017](../docs/adr/0017-add-get-claim-by-id.md),
[contract](../contracts/openapi.yaml) tag `claims`.

## Model

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | |
| `policyId` | UUID | FK to `policy` |
| `partnerId` | UUID | FK to `partner`, indexed. Never writable through the API |
| `description` | text | 1–2000 chars, plain text |
| `status` | `SUBMITTED` \| `UNDER_REVIEW` \| `APPROVED` \| `REJECTED` | State machine below |
| `amount` | decimal(12,2) | 0.01 – 1,000,000.00 |

### State machine

```
SUBMITTED ──► UNDER_REVIEW ──► APPROVED
                    │
                    └────────► REJECTED
```

`APPROVED` and `REJECTED` are final. Any other transition, including to the
same status, → `409` (`type: .../invalid-status-transition`).

## Rules

1. List: internal roles see all; `PARTNER` sees own only. Optional `status`
   filter. Default sort `status,asc`.
2. Detail: ownership enforced. Another partner's claim → `404`.
3. PATCH field permissions:

   | Role | `status` | `description` | `amount` | Condition |
   |---|---|---|---|---|
   | ADMIN | ✔ | ✔ | ✔ | Transitions follow the state machine |
   | ANALYST | ✔ | ✘ | ✘ | Transitions follow the state machine |
   | PARTNER | ✘ | ✔ | ✔ | Own claim, only while `SUBMITTED` (else `409`, `type: .../claim-not-editable`) |

4. PATCH evaluation order: validation `400` → field permission `403` →
   ownership `404` → state `409` → apply and audit → `200`.
5. A successful PATCH writes one audit entry `CLAIM_UPDATED`, `resource =
   claim:<id>`, `details` = list of changed field names, all in the same
   transaction.
6. PATCH is a write operation (write rate limit).

## Acceptance criteria

All ids from [fixtures](fixtures.md).

| ID | Given / When / Then | Type |
|---|---|---|
| CLM-01 | Given `shop-a`, when `GET /claims`, then exactly `…a001` and `…a002` | Integration |
| CLM-02 | Given `analyst`, when `GET /claims?status=SUBMITTED`, then `…a001` and `…b001` | Integration |
| CLM-03 | Given `shop-a`, when `GET /claims/40000000-…b001`, then `404`, identical to a never-existing id | Integration |
| CLM-04 | Given `shop-a`, when `GET /claims/40000000-…a001`, then `200` | Integration |
| CLM-05 | Given `analyst`, when `PATCH …a002 {"status":"APPROVED"}`, then `200` and status `APPROVED` | Integration |
| CLM-06 | Given `analyst`, when `PATCH …a001 {"status":"APPROVED"}` (skips `UNDER_REVIEW`), then `409` | Integration |
| CLM-07 | Given a claim in `APPROVED`, when any role patches `status`, then `409` | Integration |
| CLM-08 | Given `analyst`, when `PATCH …a002 {"status":"UNDER_REVIEW"}` (same status), then `409` | Integration |
| CLM-09 | Given `shop-a`, when `PATCH …a001 {"description":"Front bumper and grille"}`, then `200` | Integration |
| CLM-10 | Given `analyst`, when `PATCH …a001 {"amount": 10}`, then `403` | Slice |
| CLM-11 | Given `analyst`, when `PATCH …a001 {"description":"x"}`, then `403` | Slice |
| CLM-12 | Given `shop-a`, when `PATCH …a001 {"status":"APPROVED"}`, then `403` | Slice |
| CLM-13 | Given `shop-a`, when `PATCH …b001 {"status":"APPROVED"}`, then `403` (field check runs before the lookup, so the result doesn't depend on ownership) | Slice |
| CLM-14 | Given `shop-a`, when `PATCH …b001 {"description":"x"}`, then `404` | Integration |
| CLM-15 | Given `shop-a`, when `PATCH …a002 {"description":"x"}` (`UNDER_REVIEW`), then `409` | Integration |
| CLM-16 | Given `admin`, when `PATCH …a001 {"amount": 1300.00, "description":"Revised"}`, then `200` | Integration |
| CLM-17 | Given any role, when `PATCH` with `{"partnerId":"10000000-…000b"}`, then `400` | Slice |
| CLM-18 | Parameterized role × field test covers all 9 cells of the permission table | Slice |
| CLM-19 | Given `PATCH {}`, then `400` (at least one field) | Slice |
| CLM-20 | Given `PATCH {"description": null}`, then `400` | Slice |
| CLM-21 | Given `PATCH {"amount": 0}` or `{"amount": 10.001}`, then `400` | Slice |
| CLM-22 | Given a successful PATCH, then exactly one `CLAIM_UPDATED` audit entry with `details` listing the changed fields and no values | Integration |
| CLM-23 | Given the audit insert fails (simulated), then the claim change is rolled back | Integration |
| CLM-24 | Given `shop-a`, when `PATCH …a001 {"description":"'; DROP TABLE claim; --"}`, then `200`, the value is stored verbatim and the `claim` table still exists | Integration |

## Open questions

- Should ADMIN edits to `description`/`amount` be blocked once a claim is
  `APPROVED` or `REJECTED`? Today ADMIN can edit those fields in any state.
- Optimistic locking for concurrent PATCHes (see [api-conventions](api-conventions.md#open-questions)).
