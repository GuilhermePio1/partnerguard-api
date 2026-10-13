# Partners

## Scope

`GET /api/v1/partners`, `POST /api/v1/partners`, `GET /api/v1/partners/me`.
Related: [ADR-0010](../docs/adr/0010-cnpj-validation-and-masking.md),
[contract](../contracts/openapi.yaml) tag `partners`.

## Model

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Generated |
| `legalName` | string | 3–200 chars, plain text (ADR-0013) |
| `taxId` | CNPJ | Numeric or alphanumeric, check digits validated, stored normalized, unique, **always masked in responses** |
| `status` | `ACTIVE` \| `SUSPENDED` | New partners are `ACTIVE`. A `SUSPENDED` partner's users get `403` everywhere ([authorization](authorization.md) rule 5) |

## Rules

1. `GET /partners` (ADMIN, ANALYST): paginated, default sort `legalName,asc`.
2. `POST /partners` (ADMIN): creates an `ACTIVE` partner. Write operation:
   audit entry `PARTNER_CREATED`, write rate limit.
3. `GET /partners/me` (PARTNER): the partner whose id is the token's
   `partner_id`. If the claim points at no existing partner, or at a
   `SUSPENDED` one → `403` ([authorization](authorization.md) rule 5).
4. `taxId` input accepts `NN.NNN.NNN/NNNN-NN` or 14 characters with no
   punctuation. The first 12 may be letters (uppercased on input).
5. `taxId` output is always `**.***.***/****-DD`, where `DD` are the check digits.
6. Duplicate `taxId` (after normalization) → `409`
   (`type: .../duplicate-tax-id`). The body doesn't echo the tax id.

## Acceptance criteria

| ID | Given / When / Then | Type |
|---|---|---|
| PRT-01 | Given `analyst`, when `GET /partners`, then `200` with Partner A and B, sorted by legal name | Integration |
| PRT-02 | Given `admin`, when `POST /partners` with `{"legalName":"Oficina Gama Ltda","taxId":"12.ABC.345/01DE-35"}`, then `201`, status `ACTIVE`, `taxId = "**.***.***/****-35"` | Integration |
| PRT-03 | Given `admin`, when creating with `taxId` `33444555000181` (no punctuation), then `201` | Integration |
| PRT-04 | Given any role and any partner endpoint, then no response body contains an unmasked CNPJ (regex `[0-9A-Z]{12}[0-9]{2}` or the punctuated form) | Integration |
| PRT-05 | Given `admin`, when creating with `taxId` `12.ABC.345/01DE-36` (wrong check digit), then `400` with `errors[].field = "taxId"` | Slice |
| PRT-06 | Given `admin`, when creating with `taxId` `00000000000000`, then `400` | Slice |
| PRT-07 | Given Partner A exists with `11222333000181`, when creating with `11.222.333/0001-81`, then `409` and the body doesn't contain the tax id | Integration |
| PRT-08 | Given `admin`, when creating with `legalName` `<script>alert(1)</script>`, then `400` | Slice |
| PRT-09 | Given `admin`, when creating with an extra `"status":"SUSPENDED"` property, then `400` (unknown/forbidden property) | Slice |
| PRT-10 | Given `shop-a`, when `GET /partners/me`, then `200` with id `10000000-…000a` and `taxId = "**.***.***/****-81"` | Integration |
| PRT-11 | Given a `PARTNER` token with `partner_id` of a partner that doesn't exist, when `GET /partners/me`, then `403` (ADR-0021) | Integration |
| PRT-12 | Given a successful create, then one audit entry `PARTNER_CREATED` with `resource = partner:<id>` and `userId = admin's sub` | Integration |
| PRT-13 | Given a CNPJ check-digit unit test, then `11222333000181`, `98765432000198`, `12ABC34501DE35` are valid and changing any single character makes them invalid | Unit |
| PRT-14 | `Partner.toString()` and log output never contain the unmasked `taxId` | Unit |

## Open questions

- Is there an endpoint to change a partner's status? Not in the brief.
