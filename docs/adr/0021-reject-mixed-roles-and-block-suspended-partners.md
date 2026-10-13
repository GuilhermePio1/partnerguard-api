# ADR-0021: Reject tokens that mix `PARTNER` with an internal role, and block suspended or unknown partners

- **Status:** Accepted
- **Date:** 2026-10-13
- **Deciders:** Project owner (decided on 2026-10-13)
- **Related:** [specs/authorization.md](../../specs/authorization.md) rules 4 and 5, [ADR-0003](0003-roles-and-partner-identity-from-token.md), [ADR-0004](0004-ownership-checks-in-service-layer.md)

## Context

ADR-0003 takes roles and the partner identity from the token and fails closed
when a `PARTNER` token has no valid `partner_id`. Two cases were left open in
the specs:

- **Mixed roles.** Keycloak lets an admin give one user both `PARTNER` and
  `ADMIN` or `ANALYST`. The draft spec let the internal role win, so such a
  user would see every partner's data. No legitimate user needs both: repair
  shop staff and insurer staff are different people.
- **Partner status.** `partner.status` can be `ACTIVE` or `SUSPENDED`
  (`specs/partners.md`), but `partnerguard-api.md` gives the status no effect.
  The token can't carry it: tokens live 300 s, and a status change in the API's
  database never reaches Keycloak. A `partner_id` can also name a partner that
  doesn't exist (deleted row, typo in the Keycloak attribute).

## Decision

- A token with `PARTNER` **and** `ADMIN` or `ANALYST` is a misconfigured
  account: `403` on every endpoint. Internal roles never widen a partner's
  view. Unmapped roles are still ignored (rule 1).
- A `PARTNER` caller whose `partner_id` names a partner that is `SUSPENDED` or
  doesn't exist gets `403` on every endpoint, `GET /api/v1/partners/me`
  included, with no exception.
- The partner's status is read from the database on every `PARTNER` request
  (a primary-key lookup). It isn't cached and isn't taken from the token, so a
  suspension also stops tokens issued before it.
- These `403`s are ordinary `forbidden` Problem Details, logged as
  `event=access_denied` like every other `403` (rule 10). The body doesn't say
  which of the cases applied.

## Consequences

- A role assigned by mistake in Keycloak locks the user out instead of
  exposing every partner's data.
- One extra indexed query per `PARTNER` request.
- A suspended partner can't see its own profile or its status through the API;
  it learns about the suspension through another channel.
- There is still no endpoint to change a partner's status (open question in
  `specs/partners.md`), so for now suspension is a database change.
- Proven by `AUTHZ-19` (mixed roles), `AUTHZ-20` (suspended partner, every
  operation), `AUTHZ-21` (a token issued before the suspension stops working)
  and `AUTHZ-22` (unknown partner).

## Alternatives considered

| Option | Why not |
|---|---|
| Internal role wins on mixed tokens | A mistaken role assignment exposes every partner's data |
| `PARTNER` wins on mixed tokens | Hides the misconfiguration; the user silently loses the internal role and nobody notices |
| Leave `SUSPENDED` without effect | The status would be decorative; a suspended shop keeps reading and editing claims |
| Allow `GET /partners/me` for suspended partners | One exception in a fail-closed rule, for a convenience the brief doesn't ask for |
| Carry the partner status in the token (Keycloak attribute) | Two sources of truth, and a suspension waits for token expiry |
| Unknown `partner_id` behaves like a partner with no data (empty lists, `404` on `/me`) | The account keeps "working" without a partner; failing closed exposes the misconfiguration |
