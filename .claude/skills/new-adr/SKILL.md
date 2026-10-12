---
name: new-adr
description: Create a new numbered Architecture Decision Record in docs/adr from the template and update the ADR index; also supersede an existing ADR. Use when a design or security decision is made or changed, when an open question in specs/ gets answered with a decision, or when asked to document why something is built a certain way.
---

# New ADR

Format and rules: `docs/adr/0001-record-architecture-decisions.md`.

## Steps

1. **Number:** list `docs/adr/` and take the highest `NNNN` + 1 (4 digits,
   zero-padded). `0000` is the template.
2. **File:** `docs/adr/NNNN-<kebab-case-title>.md`, copied from
   `docs/adr/0000-template.md`. The title is a decision in the imperative
   ("Use X for Y"), not a topic ("Rate limiting").
3. **Fill it in:**
   - `Status: Accepted` if the user decided; `Proposed` if it still needs a
     decision. Ask when unsure.
   - `Date:` today (ISO-8601).
   - `Deciders:` who decided. Write "Project owner" when the user decided.
   - **Context:** facts and constraints, citing `partnerguard-api.md`, specs or
     earlier ADRs. Say explicitly where the brief is silent or contradictory.
   - **Decision:** precise and testable: status codes, values, names, order
     of checks.
   - **Consequences:** include the spec criterion IDs that prove the decision
     (add them to the spec if missing).
   - **Alternatives considered:** at least two real options with the reason
     each was rejected.
4. **Index:** add a row to `docs/adr/README.md` (number, linked title, status).
5. **Cross-links:** link the ADR from the related spec's *Scope* line. If the
   decision answers an open question, move it from *Open questions* into
   *Rules*.
6. **README:** if the decision changes user-visible behavior or the security
   design, update the matching section of `README.md`.

## Superseding

Accepted ADRs are immutable. To change one:

1. Create the new ADR as above. In **Context**, say what changed and link the
   old ADR.
2. In the old ADR, change only the status line to
   `Superseded by [ADR-NNNN](NNNN-title.md)`.
3. Update both rows in the index.

## Don't

- Don't record decisions the user hasn't made as `Accepted`.
- Don't put implementation tutorials in an ADR. Link to the spec or code.
- Don't renumber or delete ADRs.
