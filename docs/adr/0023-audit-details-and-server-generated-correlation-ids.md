# ADR-0023: Audit the field names a request set, and generate correlation ids on the server

- **Status:** Accepted
- **Date:** 2026-10-13
- **Deciders:** Project owner (approved on 2026-10-13)
- **Related:** [specs/audit.md](../../specs/audit.md) rules 1 and 2, [specs/errors.md](../../specs/errors.md) rule 3, [ADR-0012](0012-audit-log-in-same-transaction.md)

## Context

[ADR-0012](0012-audit-log-in-same-transaction.md) puts "a JSON list of changed
field names, never their values" in each audit row's `details`. Implementing it
left two gaps:

- **Creation.** Nothing is "changed" when a partner is created, so the rule
  didn't say what `PARTNER_CREATED` records.
- **"Changed".** A PATCH can carry a field whose new value equals the current
  one. Either the fields the request carried or only the fields whose value
  actually differs could count as changed.

`specs/errors.md` also returns a correlation id in `X-Correlation-Id` on every
response and logs it with each `500`, but didn't say where the id comes from.
If a client could choose it, the client would choose what appears in the
server's logs: fake ids that collide with someone else's request, or ids
crafted to confuse log tooling.

## Decision

1. **`details` names what the request set, never values.**
   - `PARTNER_CREATED`: `{"fields":["legalName","taxId"]}`, the fields the
     create request set.
   - `CLAIM_UPDATED`: the fields the PATCH carried, in the order `status`,
     `description`, `amount`, even when a value equals the current one.
2. **The audit insert only runs inside the write's transaction.**
   `AuditService.record` uses `Propagation.MANDATORY`. Called without a
   transaction, it throws instead of writing an entry that could outlive a
   rolled-back change.
3. **Correlation ids come from the server only.** A filter that runs before
   Spring Security generates a random UUID for every request, returns it in
   `X-Correlation-Id` (on `401`, `403` and `429` from filters too) and puts it
   in the logging context. An incoming `X-Correlation-Id` header is ignored.
   The error dispatch of the same request reuses the id already on the
   response.

## Consequences

- Every audit row has the same shape, `{"fields":[...]}`, whatever the action.
- A row records what the caller asked to change, not a computed diff. The
  service doesn't compare old and new values, and a PATCH that repeats current
  values still leaves a trace.
- A future write service that forgets `@Transactional` fails its tests at the
  first audit call instead of auditing outside the transaction.
- A client can't correlate its own request id with server logs. It quotes the
  `X-Correlation-Id` the server returned instead.
- Proved by `AUD-01`, `AUD-02`, `AUD-05`, `AUD-08`, `PRT-12`, `CLM-22` and
  `ERR-06`.

## Alternatives considered

| Option | Why not |
|---|---|
| `PARTNER_CREATED` with an empty field list | Loses the fact that the request set a tax id; the shape differs from updates in practice |
| `CLAIM_UPDATED` lists only fields whose value changed | Needs before/after comparison in the service; a no-op PATCH would leave an empty list that tells less than the request did |
| Accept a client `X-Correlation-Id` when it is a valid UUID | Lets clients place chosen values in server logs; nothing in scope needs end-to-end tracing across services |
| `Propagation.REQUIRED` for the audit insert | A caller without a transaction would silently get a separate one, the inconsistency ADR-0012 rules out |
