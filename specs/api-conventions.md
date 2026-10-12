# API conventions

## Scope

Rules shared by every endpoint: media types, pagination, ids, validation,
documentation and code structure. Related: [ADR-0007](../docs/adr/0007-contract-first-openapi.md),
[ADR-0011](../docs/adr/0011-package-by-feature.md), [ADR-0014](../docs/adr/0014-uuid-identifiers.md).

## Rules

1. Base path `/api/v1`. The version changes only on breaking changes.
2. Request and response bodies are `application/json` (UTF-8). Errors are
   `application/problem+json`. Other request media types → `415`.
3. Ids are UUIDs. A malformed id in the path → `400`.
4. Unknown JSON properties in request bodies → `400` (Jackson
   `FAIL_ON_UNKNOWN_PROPERTIES=true`). Explicit `null` for an optional field in a
   PATCH → `400`.
5. All list endpoints are paginated:
   - `page` ≥ 0 (default 0, max 10000), `size` 1–100 (default 20).
   - `sort` must be one of the values enumerated in the contract. Anything else → `400`.
   - Response shape: `{ content, page, size, totalElements, totalPages }`.
     Spring's `Page` JSON is never exposed directly.
6. Dates are ISO-8601 (`2026-10-12`). Timestamps are UTC with offset
   (`2026-10-12T14:00:00Z`).
7. Money is a JSON number with at most 2 decimals, mapped to `BigDecimal`
   (scale 2, `HALF_EVEN`). Never `double`.
8. `POST` that creates a resource → `201` + `Location` header.
9. The contract in `contracts/openapi.yaml` is the source of truth. Code that
   adds, removes or changes an operation, parameter, schema or status code
   without the same change in the contract is a defect.

## Acceptance criteria

| ID | Given / When / Then | Type |
|---|---|---|
| API-01 | Given any authenticated caller, when `size=101`, then `400` with `errors[0].field = "size"` | Slice |
| API-02 | Given any authenticated caller, when `sort=password,asc` (not enumerated), then `400` | Slice |
| API-03 | Given no `page`/`size`, when listing, then `page=0`, `size=20` in the response | Slice |
| API-04 | Given a body with an unknown property, when calling any write endpoint, then `400` | Slice |
| API-05 | Given `Content-Type: text/plain`, when calling a write endpoint, then `415` Problem Details | Slice |
| API-06 | Given a malformed UUID in the path, when calling `GET /api/v1/claims/not-a-uuid`, then `400` | Slice |
| API-07 | Given a successful `POST /api/v1/partners`, then `201` and `Location: /api/v1/partners/{id}` | Slice |
| DOC-01 | Given the app running with the `local` profile, when comparing `/v3/api-docs` with `contracts/openapi.yaml`, then paths, methods and response codes match | Integration |
| DOC-02 | Given the `local` profile, `/swagger-ui.html` is reachable without a token; given any other profile, it returns `401` or `404` | Integration |
| DOC-03 | Given CI, `contracts/openapi.yaml` passes `redocly lint` with no errors | CI |
| ARCH-01 | No dependency cycles between feature packages | Arch |
| ARCH-02 | No class outside a feature package accesses that feature's repository | Arch |
| ARCH-03 | `@RestController` classes don't depend on repositories | Arch |

## Open questions

- Should `ETag`/`If-Match` optimistic locking protect `PATCH /claims/{id}` from
  lost updates? Not in the brief. Lost updates are possible today.
