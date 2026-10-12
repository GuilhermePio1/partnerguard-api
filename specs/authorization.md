# Authorization

## Scope

Role checks, ownership checks and deny-by-default. Related:
[ADR-0003](../docs/adr/0003-roles-and-partner-identity-from-token.md),
[ADR-0004](../docs/adr/0004-ownership-checks-in-service-layer.md),
[ADR-0005](../docs/adr/0005-deny-by-default.md),
[ADR-0009](../docs/adr/0009-claim-update-field-level-permissions.md),
[ADR-0016](../docs/adr/0016-security-test-matrix.md).

## Rules

1. Roles are read from `realm_access.roles`. Only `ADMIN`, `ANALYST` and
   `PARTNER` are mapped; others are ignored.
2. Every controller method has `@PreAuthorize` with the roles from
   `x-required-roles` in the contract. A wrong role → `403`.
3. The partner identity is the `partner_id` claim (UUID). It MUST NOT be read
   from the path, query, body or any header other than the token.
4. A `PARTNER` token with a missing or malformed `partner_id` → `403` on every
   endpoint, before any lookup.
5. Ownership is enforced in services with scoped queries. A resource owned by
   another partner → `404`, with a body identical to a missing resource.
6. List endpoints return only the caller's resources for `PARTNER`; for internal
   roles they return everything.
7. Anything not explicitly allowed is denied: `denyAll()` outside `/api/**` and
   the public paths; `@PreAuthorize` required on every controller method.
8. Public paths: `GET /actuator/health`; in the `local` profile also Swagger UI
   and `/v3/api-docs/**`. The servlet error dispatch (`DispatcherType.ERROR` to
   `/error`) must be permitted too, or security errors can't be rendered. It is
   served by `common.error.ProblemDetailsErrorController`, which only returns
   Problem Details and is the one controller exempt from `AUTHZ-01`.
9. Every `403` and ownership `404` is logged (`event=access_denied` /
   `event=ownership_denied`, `sub`, method, path).

### Access matrix

`✔` = allowed, `—` = `403`, `own` = allowed with ownership (others → `404`).

| Operation | ADMIN | ANALYST | PARTNER | Anonymous |
|---|---|---|---|---|
| `GET /api/v1/partners` | ✔ | ✔ | — | 401 |
| `POST /api/v1/partners` | ✔ | — | — | 401 |
| `GET /api/v1/partners/me` | — | — | own | 401 |
| `GET /api/v1/contracts` | ✔ (all) | ✔ (all) | own (filtered) | 401 |
| `GET /api/v1/contracts/{id}` | ✔ | ✔ | own | 401 |
| `GET /api/v1/claims` | ✔ (all) | ✔ (all) | own (filtered) | 401 |
| `GET /api/v1/claims/{id}` | ✔ | ✔ | own | 401 |
| `PATCH /api/v1/claims/{id}` | ✔ (all fields) | ✔ (`status`) | own (`description`, `amount`, only `SUBMITTED`) | 401 |
| `GET /api/v1/policies/{number}` | ✔ | ✔ | — | 401 |
| `GET /actuator/health` | ✔ | ✔ | ✔ | ✔ |

## Acceptance criteria

| ID | Given / When / Then | Type |
|---|---|---|
| AUTHZ-01 | Every public method of every `@RestController` has `@PreAuthorize` | Arch |
| AUTHZ-02 | Every operation in `contracts/openapi.yaml` has an entry in the security matrix test; a missing entry fails the build | Unit |
| AUTHZ-03 | For each cell of the access matrix, the status matches (parameterized) | Slice |
| AUTHZ-04 | Given a token whose only role is `SUPERUSER` (unmapped), when calling any `/api/**` endpoint, then `403` | Slice |
| AUTHZ-05 | Given a `PARTNER` token without `partner_id`, when calling `GET /api/v1/claims`, then `403` | Slice |
| AUTHZ-06 | Given a `PARTNER` token with `partner_id = "abc"`, when calling `GET /api/v1/partners/me`, then `403` | Slice |
| AUTHZ-07 | Given `shop-a`, when calling `GET /api/v1/claims?partnerId=10000000-0000-4000-8000-00000000000b`, then the response contains only Partner A's claims (the parameter is ignored or rejected, never applied) | Integration |
| AUTHZ-08 | Given `shop-a`, when reading own contract `30000000-…000a`, then `200` | Integration |
| AUTHZ-09 | Given `shop-a`, when reading Partner B's contract `30000000-…000b`, then `404` | Integration |
| AUTHZ-10 | The `404` bodies of AUTHZ-09 and of the never-existing id are identical except for `instance` | Integration |
| AUTHZ-11 | Given `shop-a`, when listing contracts and claims, then no item has `partnerId ≠ A` | Integration |
| AUTHZ-12 | Given `analyst`, when listing claims, then claims of A and B are returned | Integration |
| AUTHZ-13 | Given an authenticated caller, when calling `GET /internal/anything` (outside `/api/**`, no rule), then `403`; when calling `GET /api/v1/unknown`, then `404` | Slice |
| AUTHZ-14 | Given anonymous, when calling `GET /actuator/health`, then `200` with body `{"status":"UP"}` and no details | Integration |
| AUTHZ-15 | Given anonymous, when calling `GET /actuator/env`, `/actuator/beans` or `/actuator/heapdump`, then not `200` | Integration |
| AUTHZ-16 | Given a call that results in `403`, then one `access_denied` log event is written | Slice |
| AUTHZ-17 | Given a call that results in an ownership `404`, then one `ownership_denied` log event is written | Integration |
| AUTHZ-18 | Ownership checks are applied by the service: calling `ClaimService.get(id, currentUser)` directly (no controller) with Partner A and claim `…b001` throws `ResourceNotFoundException` | Unit |

## Open questions

- A token with both `PARTNER` and an internal role: the API currently treats
  internal roles as winning (broader view). Should that combination be
  rejected as a misconfiguration instead?
- Should a `SUSPENDED` partner's users be blocked (e.g. `403` on everything)?
  The brief defines the status but no behavior for it.
