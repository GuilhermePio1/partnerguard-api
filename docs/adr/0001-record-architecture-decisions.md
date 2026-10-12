# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner
- **Related:** [docs/adr/README.md](README.md)

## Context

The definition of done requires that "every security decision is explained".
The README explains *what* the system does; we also need a durable record of
*why* each decision was taken and which alternatives were rejected, so a
reviewer can challenge a decision without reverse-engineering the code.

## Decision

- Architecture and security decisions are recorded as ADRs in `docs/adr/`,
  numbered sequentially (`NNNN-kebab-case-title.md`) from
  [the template](0000-template.md).
- An ADR is immutable once accepted. A changed decision gets a new ADR that
  supersedes the old one; the old one is marked `Superseded by ADR-XXXX`.
- The README restates the current decisions; ADRs keep the reasoning.
- Every ADR that introduces a rule names the spec criteria (e.g. `AUTHZ-04`)
  whose tests prove it.

## Consequences

- Reviewers can trace a rule from README → ADR → spec criterion → test.
- Small extra cost per decision. The `new-adr` skill automates the boilerplate.

## Alternatives considered

| Option | Why not |
|---|---|
| Decisions only in the README | Loses rejected alternatives and history |
| Wiki outside the repo | Drifts from the code; not reviewed in pull requests |
