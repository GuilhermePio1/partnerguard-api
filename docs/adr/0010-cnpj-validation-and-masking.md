# ADR-0010: Validate `taxId` as CNPJ (numeric and alphanumeric) and always mask it

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner (decided on 2026-10-12)
- **Related:** [specs/partners.md](../../specs/partners.md), [specs/hardening.md](../../specs/hardening.md)

## Context

`Partner.taxId` is the company's Brazilian registration number (CNPJ) and must
be masked in responses. Since July 2026 the Receita Federal issues
**alphanumeric CNPJs** for new registrations. The first 12 characters may be
`A-Z`/`0-9`, and the last 2 are numeric check digits. Existing numeric CNPJs
remain valid.

## Decision

- **Input:** accept numeric or alphanumeric CNPJ, with or without punctuation
  (`12.ABC.345/01DE-35` or `12ABC34501DE35`). Letters are uppercased.
- **Validation:** check digits with the modulo-11 algorithm, where each
  character's value is `ASCII code − 48`, so digits keep their value and
  `A = 17` … `Z = 42`. Weights: `5,4,3,2,9,8,7,6,5,4,3,2` for the first digit,
  `6,5,4,3,2,9,8,7,6,5,4,3,2` for the second. Remainder `< 2` → digit `0`,
  else `11 − remainder`. Reject 14 identical characters (e.g. `00000000000000`).
- **Storage:** normalized 14-character uppercase string, unique.
- **Output:** every response masks it as `**.***.***/****-NN`. Only the two
  check digits are visible. No role sees the full value, and no endpoint
  returns it. Masking happens in the DTO mapper (`common/masking`), not in
  Jackson serializers attached to entities.
- **Logs:** `taxId` is never logged unmasked. `Partner.toString()` excludes it.

## Consequences

- Masked output is the same for every role, so tests are simple (`PRT-04`).
- Duplicate detection works on the normalized value (`PRT-07`).
- An internal workflow that needs the full value would require a new
  endpoint and a new ADR.
- Reference values: `11.222.333/0001-81` (numeric) and `12.ABC.345/01DE-35`
  (alphanumeric) are valid. Changing any check digit makes them invalid.

## Alternatives considered

| Option | Why not |
|---|---|
| Numeric-only CNPJ validation | Rejects legitimate CNPJs issued since July 2026 |
| Show the last 4 characters | Exposes part of the base number; the check digits alone reveal less |
| Unmasked for ADMIN | The source document says it's masked in responses, without exceptions |
| Encrypt at rest | Useful, but not required here; possible future ADR |
