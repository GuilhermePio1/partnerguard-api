# ADR-0007: Contract-first API with OpenAPI

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner
- **Related:** [contracts/openapi.yaml](../../contracts/openapi.yaml)

## Context

The API surface, including every error status, is part of the security design.
If documentation is generated from code, the code is the only spec and
reviewers can't check intent against implementation.

## Decision

- `contracts/openapi.yaml` (OpenAPI **3.0.3**) is the source of truth for
  paths, schemas, status codes and allowed roles (`x-required-roles`).
- `openapi-generator-maven-plugin` (generator `spring`, **≥ 7.16.0**) generates
  API interfaces and DTOs into `target/generated-sources` at build time:
  `interfaceOnly=true`, `useSpringBoot4=true`, `useJackson3=true`,
  `useBeanValidation=true`, `useTags=true`, `skipDefaultInterface=true`,
  `openApiNullable=false`.
- Controllers implement the generated interfaces. `@PreAuthorize` goes on the
  **controller implementation methods**, not on the interfaces.
- Generated code is never edited or committed.
- springdoc-openapi **3.x** serves Swagger UI in the `local` profile. A contract
  test compares the runtime `/v3/api-docs` with `contracts/openapi.yaml`
  (paths, methods, status codes) and fails on drift (`DOC-01`).
- The file is linted in CI (`@redocly/cli lint`).
- Version 3.0.3 rather than 3.1 because generator support for 3.1 is still
  incomplete. Revisit in a new ADR.

## Consequences

- Any API change starts in the contract and shows up in the pull request diff.
- Bean Validation constraints come from the schema (`pattern`, `minLength`,
  `maximum`...). Rules that a schema can't express (CNPJ check digits, status
  transitions, field permissions) live in the service and are listed in the
  specs.
- The generator does **not** turn every schema keyword into Bean Validation.
  These need explicit code (custom constraint, `@Digits`, or a service check),
  each covered by its spec criterion: `minProperties: 1` (`CLM-19`),
  `additionalProperties: false` (Jackson `FAIL_ON_UNKNOWN_PROPERTIES`, `API-04`),
  `multipleOf: 0.01` (`@Digits(integer = 7, fraction = 2)`, `CLM-21`), and
  explicit `null` rejection in PATCH (`CLM-20`). Schemas avoid `allOf` on
  primitive types for the same reason; the plain-text pattern is inlined.
- Likewise, `enum` on a **query parameter** (the `sort` allow-lists) is generated
  as a plain `String` with no constraint. The allow-list check (`API-02`) needs code.
- Since milestone 2 the generator runs with `documentationProvider=springdoc`
  and `annotationLibrary=swagger2`, so springdoc 3.x reproduces every documented
  status code; `useSpringBuiltInValidation=true` (no class-level `@Validated`, so
  constraint failures arrive as `HandlerMethodValidationException`); and
  `modelNameSuffix=Dto` (`ClaimDto`, `ClaimStatusDto`...), which keeps contract
  types apart from domain entities of the same name.
- Because the generated annotations come from the contract itself, `DOC-01`
  mainly catches drift on the code side: an endpoint added outside the generated
  interfaces, or an interface no controller implements. A mutation check (a
  rogue `GET /api/v1/rogue`) confirmed it fails in that case.
- **Verified in milestone 1 (2026-10-12):** openapi-generator **7.25.0** with
  `useSpringBoot4=true` and `useJackson3=true` generates code that compiles against
  **Spring Boot 4.1.1** (Jakarta EE, Jackson 3; model annotations stay in
  `com.fasterxml.jackson.annotation`, as Jackson 3 intends). `GeneratedContractTests`
  checks that every `operationId` becomes a generated method and that contract
  constraints become Bean Validation annotations. The hand-written fallback is not
  needed.

## Alternatives considered

| Option | Why not |
|---|---|
| Code-first with springdoc annotations | The code becomes the spec; harder to review intent |
| Spring Cloud Contract | Solves consumer-driven testing, not API design; no consumers here |
| OpenAPI 3.1 | Generator support incomplete |
