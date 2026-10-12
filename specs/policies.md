# Policies

## Scope

`GET /api/v1/policies/{number}`. Related: [contract](../contracts/openapi.yaml) tag `policies`.

## Model

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | |
| `policyNumber` | string | `^[A-Z0-9-]{6,30}$`, unique |
| `holderName` | string | |
| `vehiclePlate` | string | `^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$` (old or Mercosul format) |
| `status` | `ACTIVE` \| `CANCELLED` \| `EXPIRED` | |

## Rules

1. Only ADMIN and ANALYST can look up policies. `PARTNER` → `403`.
2. Lookup by exact `policyNumber`. Unknown → `404`.
3. A number that doesn't match the pattern → `400` before any query.
4. Read-only in this scope (ADR-0017).

## Acceptance criteria

| ID | Given / When / Then | Type |
|---|---|---|
| POL-01 | Given `analyst`, when `GET /policies/POL-2026-000001`, then `200` with holder `Maria Silva` and plate `ABC1D23` | Integration |
| POL-02 | Given `shop-a`, when `GET /policies/POL-2026-000001`, then `403` | Slice |
| POL-03 | Given `admin`, when `GET /policies/POL-2026-999999`, then `404` | Integration |
| POL-04 | Given `admin`, when `GET /policies/POL'%20OR%20'1'='1`, then `400` and no query is executed | Slice |
| POL-05 | Given `admin`, when `GET /policies/pol-2026-000001` (lowercase), then `400` | Slice |

## Open questions

- Should `holderName` and `vehiclePlate` (personal data under LGPD) be masked
  or restricted? The brief only masks `taxId`; today internal roles see them in
  full.
- Policy lifecycle (who sets `CANCELLED`/`EXPIRED`, and when) isn't defined.
