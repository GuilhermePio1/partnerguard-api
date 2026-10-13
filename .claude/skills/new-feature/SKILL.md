---
name: new-feature
description: Add or change a PartnerGuard API endpoint or feature slice end to end, following spec → contract → tests → code. Use when asked to add an endpoint, add a field to a resource, create a new feature package (controller/service/repository/DTO), or change what an existing endpoint accepts or returns.
---

# New feature / endpoint

PartnerGuard is contract-first and security-first. An endpoint is done only
when the spec, the OpenAPI contract, the security matrix tests and the code
agree. Follow these steps in order. Don't write production code before step 4.

## 1. Spec

1. Find the feature's spec in `specs/` (index: `specs/README.md`). Create
   `specs/<feature>.md` with the same sections as its siblings if it's new.
2. Write or update the **Rules** and add **acceptance criteria** with new IDs
   (next free number for the prefix; never renumber). Cover, at least:
   - allowed role → success;
   - each disallowed role → `403`;
   - anonymous → `401`;
   - if the resource has an owner: own → `200`, other partner's → `404`,
     missing → `404` with an identical body;
   - validation failures → `400`; state conflicts → `409`;
   - for writes: exactly one audit entry; none on failure.
3. If the brief doesn't settle something, add it to **Open questions** and ask
   the user. Don't invent business rules.
4. If an architectural or security decision was made, use the `new-adr` skill.

## 2. Contract

Edit `contracts/openapi.yaml`:

- Path under `/api/v1`, `operationId` in camelCase, a `tag` equal to the
  feature name.
- `x-required-roles: [...]` with the exact roles; `x-write-operation: true`
  for `POST/PUT/PATCH/DELETE`.
- Reuse `components/parameters` (`ResourceId`, `Page`, `Size`) and add a
  `<Feature>Sort` enum if the endpoint lists items.
- Request schemas: `additionalProperties: false`; free text as `type: string`
  with the `PlainText` pattern **inlined** (don't use `allOf` on primitives; the
  generator mishandles it); explicit `minLength/maxLength/pattern/minimum/maximum`.
  Keywords the generator doesn't enforce (`minProperties`, `multipleOf`,
  `additionalProperties`, explicit `null`) need code. See ADR-0007.
  **Never** put `partnerId` (or any owner id) in a request schema.
- Responses: success schema plus the shared `400/401/403/404/409/415/429/500`
  responses that apply. Every error is `application/problem+json`.
- New error kinds: add the `type` to the catalog in `specs/errors.md`.

## 3. Tests first

Use the `security-tests` skill to add the endpoint to the security matrix,
then write the feature tests named after the criterion IDs
(`@DisplayName("CLM-12 ...")`). Run them and confirm they fail.

## 4. Code

Package by feature (`com.partnerguard.<feature>`, ADR-0011):

| Piece | Rules |
|---|---|
| Controller | Implements the generated `<Tag>Api` interface (DTOs are `*Dto`). `@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")` on **each implementation method**, matching `x-required-roles`. No business logic, no repository access. Gets the caller's `OwnershipScope` and partner id **only** from `common.caller.CallerContext` and passes the scope down. Validates contract rules the generator can't (`minProperties`, `multipleOf`) and sorts via `common.web.SortOptions` |
| Service | `@Transactional`. Receives an `OwnershipScope`. Enforces ownership in the query: `switch` on the scope, `findByIdAndPartnerId` for `PartnerOnly`, throwing `OwnershipDenials.notFound(...)` for both "missing" and "not yours". Field-level permission checks before the lookup. For writes, calls `AuditService.record(action, type, id, fieldNames)` inside the same transaction (it refuses to run without one), with field **names** only |
| Repository | Spring Data derived queries or `@Query` JPQL with named parameters. **No string concatenation or `String.format` in queries.** Sort only from the contract's allow-list |
| Entity | UUID id (`UUID.randomUUID()`), package-private where possible, `toString()` excludes sensitive fields |
| Mapper | Entity → generated DTO. Apply masking (`common/masking`) here, e.g. `taxId` |

If the change needs a table or column, use the `flyway-migration` skill.

Errors: throw `ProblemException` (catalog type) or `ResourceNotFoundException`;
`ApiExceptionHandler` renders them. Never put request data in an exception
message.

## 5. Verify

- `./mvnw verify` passes (unit, slice, integration, ArchUnit, contract drift test `DOC-01`).
- `grep -rn "<NEW-ID>" src/test` finds a test for every new criterion.
- If user-visible behavior changed: update `README.md` (endpoint table, roles,
  security design) and `scripts/smoke-test.sh` **and** `scripts/smoke-test.ps1`
  with non-mutating checks.

## Checklist

- [ ] Spec rules + criteria (+ open questions)
- [ ] Contract updated; `x-required-roles` set
- [ ] Security matrix entry; feature tests fail first, then pass
- [ ] `@PreAuthorize` on every controller method
- [ ] Ownership in the service, `404` for other partners
- [ ] No owner id from the request; unknown fields rejected
- [ ] Audit entry for writes, same transaction
- [ ] README / smoke tests / ADR updated as needed
