# ADR-0024: Swagger UI renders the contract, not the generated docs

- **Status:** Accepted
- **Date:** 2026-10-13
- **Deciders:** Project owner (approved on 2026-10-13)
- **Related:** [specs/api-conventions.md](../../specs/api-conventions.md) rule 11, [ADR-0007](0007-contract-first-openapi.md)

## Context

[ADR-0007](0007-contract-first-openapi.md) makes `contracts/openapi.yaml` the
source of truth and has springdoc serve Swagger UI in the `local` profile, from
docs it generates out of the controllers. The drift test (DOC-01) keeps the
generated paths, methods and status codes equal to the contract's.

The generated docs only hold what the generated annotations carry. In
Swagger UI they showed:

- the title "OpenAPI definition", version `v0`, and none of the contract's
  description (authentication, roles, errors, rate limits);
- no `securitySchemes`, so there was no *Authorize* button and no way to call
  the API with a token from the page;
- no `x-required-roles` on any operation.

The brief asks for "Swagger complete" in milestone 7.

## Decision

- In the `local` profile, the contract file is served unchanged at
  `/openapi.yaml`, and Swagger UI loads it (`springdoc.swagger-ui.url`).
  What the page shows is exactly what the contract says.
- `/v3/api-docs` stays generated from the code. It is no longer what people
  read; it is what DOC-01 compares with the contract.
- Outside `local`, `/openapi.yaml` is not served (deny by default, ADR-0005),
  like Swagger UI and `/v3/api-docs` (DOC-02).
- The file is read from `contracts/` at runtime, through the `local` profile's
  static resource locations; it is not packaged into the jar.

## Consequences

- Swagger UI has the contract's title and description, *Authorize* with a
  bearer token, and each operation's `x-required-roles`.
- Swagger UI can't drift from the contract, because it is the contract. The
  implementation can't drift either, because DOC-01 still checks it.
- `spring-boot:run` must start from the repository root, which is what it does;
  a jar started elsewhere with the `local` profile would show no Swagger
  content.
- Proved by `DOC-04` (and `DOC-01`, `DOC-02` unchanged).

## Alternatives considered

| Option | Why not |
|---|---|
| Keep the generated docs | No title, no *Authorize*, no roles: the page can't be used to try the secured API |
| A springdoc customizer that copies `info` and `securitySchemes` from the contract into the generated docs | Fixes the title and *Authorize*, but extensions such as `x-required-roles` and other contract details still go missing; two documents to reason about |
| Package the contract into the jar as a static file | Ships documentation in every profile's artifact, only to have security refuse it outside `local` |
