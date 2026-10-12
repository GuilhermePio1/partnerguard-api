# ADR-0011: Package by feature

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner
- **Related:** README "Project structure"

## Context

Layer packages (`controllers/`, `services/`, `repositories/`) spread one
business area across the tree and let any class reach any repository.

## Decision

- One package per feature under `com.partnerguard`: `partner`, `contract`,
  `claim`, `policy`, `audit`. Each holds its controller, service, repository,
  entity and mappers.
- `config/` holds security, rate limiting and OpenAPI configuration. `common/`
  holds cross-cutting code: error handling, `CurrentUser`, `OwnershipScope`,
  masking, validation constraints.
- Repositories and entities are package-private where possible. Other
  features call a feature's **service**, never its repository.
- ArchUnit tests enforce: no cycles between features; no access to another
  feature's repository; controllers don't access repositories (`ARCH-01..03`).

## Consequences

- A feature can be read and reviewed in one folder.
- Some DTOs are generated from the contract into a separate package
  (`com.partnerguard.api.model`). Mappers live in the feature package.

## Alternatives considered

| Option | Why not |
|---|---|
| Package by layer | See Context |
| Spring Modulith | Good fit later; extra dependency for five small modules |
