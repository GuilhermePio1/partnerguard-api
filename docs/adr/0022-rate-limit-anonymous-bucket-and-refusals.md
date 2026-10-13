# ADR-0022: Count every unauthenticated request in the anonymous bucket, and let refusals take no tokens

- **Status:** Accepted
- **Date:** 2026-10-13
- **Deciders:** Project owner (approved on 2026-10-13)
- **Related:** [specs/rate-limiting.md](../../specs/rate-limiting.md) rules 2 and 4, [ADR-0006](0006-two-layer-rate-limiting.md)

## Context

[ADR-0006](0006-two-layer-rate-limiting.md) set up the two rate-limit layers.
Implementing them showed three points where it, the spec and the README
disagreed or said nothing:

- **`/actuator/health`.** ADR-0006 says the health endpoint "is counted by
  layer 1 only". The spec (rule 2, `RL-06`) and README §8.3 put it in the
  anonymous bucket (60/min per IP) as well. ADR-0006 is Accepted and can't be
  edited (ADR-0001).
- **Which requests are anonymous.** Rule 2 said "anonymous callers (public
  paths)". A request without a token to a protected path, such as
  `GET /api/v1/claims`, also reaches layer 2 unauthenticated, before it gets
  its `401`. Nothing said whether it counts.
- **What a refusal costs.** Rule 4 said "a request that is rejected by a filter
  consumes from that filter's bucket". That could mean a refused request still
  takes a token, which would push a caller's wait further with every retry and
  contradict `RL-07` (wait `Retry-After`, then the next call succeeds).

## Decision

1. **Health counts in both layers.** `/actuator/health` takes a token from the
   IP ceiling (120/min) and from the anonymous bucket of its IP (60/min). This
   replaces the line "`/actuator/health` is counted by layer 1 only" in
   ADR-0006. The rest of ADR-0006 stands.
2. **Anonymous means unauthenticated, whatever the path.** Every request that
   reaches layer 2 without an authentication takes a token from the anonymous
   bucket of its IP: the public paths, and protected paths called without a
   token (which then get `401`). A request with an invalid token never reaches
   layer 2, because JWT validation refuses it first.
3. **A request takes one token from each layer it reaches, and a refusal takes
   nothing from the layer that refused it.**
   - A `401` from JWT validation has already taken its layer-1 token.
   - A layer-1 `429` never reaches JWT validation or layer 2.
   - A write takes a token from both the write bucket and the user bucket, or
     from neither: when one of them refuses, the token taken from the other is
     given back.

## Consequences

- A health probe gets 60 calls per minute per IP. That is plenty for a probe,
  and an anonymous flood of the one public endpoint hits the lower limit.
- Scanning protected paths without a token is limited at 60/min per IP, not
  only by the 120/min ceiling.
- A caller who waits `Retry-After` is guaranteed a token; hammering during the
  wait doesn't extend it.
- Proved by `RL-03`, `RL-06`, `RL-07`, `RL-12` and `RL-13`.

## Alternatives considered

| Option | Why not |
|---|---|
| Keep health in layer 1 only (ADR-0006 as written) | The spec, the README and `RL-06` already said otherwise; the only public endpoint would be the one anonymous path with no anonymous limit |
| Anonymous bucket on public paths only | Tokenless scans of protected paths would count only against the IP ceiling, and the filter would need its own copy of the public-path list |
| Refused requests take a token (bucket can go negative) | A client retrying during the wait stays blocked indefinitely; contradicts `RL-07` and makes `Retry-After` a lie |
