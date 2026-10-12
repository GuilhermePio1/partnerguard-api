# ADR-0006: Two-layer rate limiting with Bucket4j, keyed only on verified identity

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner (approved on 2026-10-12)
- **Related:** [specs/rate-limiting.md](../../specs/rate-limiting.md)

## Context

The source document puts the rate limit filter **before** JWT validation and
keys the bucket by the `sub` claim. Those two requirements conflict: before
validation, `sub` is attacker-controlled. Keying on an unverified `sub` lets a
caller:

- send a forged token with a random `sub` on every call and never hit a limit;
- send forged tokens with a victim's `sub` and drain the victim's bucket.

We also need protection *before* JWT verification, because verifying signatures
costs CPU and invalid-token floods never reach a post-authentication filter
(`BearerTokenAuthenticationFilter` rejects them with `401` immediately).

`bucket4j-spring-boot-starter` support for Spring Boot 4 was not confirmed when
this ADR was written; Bucket4j core is framework-independent.

## Decision

Two servlet filters, both built on **Bucket4j core** (`com.bucket4j:bucket4j_jdk17-core`):

1. **`PreAuthRateLimitFilter`**: runs before `BearerTokenAuthenticationFilter`.
   Keyed by client IP. **120 requests/minute per IP** for every request,
   authenticated or not. It is a flood ceiling; it never looks at the token.
2. **`UserRateLimitFilter`**: runs after `BearerTokenAuthenticationFilter`
   and before `AuthorizationFilter`.
   - Authenticated: keyed by the **verified** `sub`. **60 requests/minute** for
     all calls, plus a separate **20 requests/minute** bucket that write
     operations (`POST`, `PUT`, `PATCH`, `DELETE`) also consume.
   - Anonymous (public endpoints): keyed by client IP, **60 requests/minute**.

Common rules:

- Exceeding any bucket returns `429` with `Retry-After` (whole seconds, rounded
  up, minimum 1) and a Problem Details body
  (`type: .../rate-limit-exceeded`).
- Client IP is `request.getRemoteAddr()`. `X-Forwarded-For` is honoured only
  when `server.forward-headers-strategy` is enabled for a trusted proxy; it is
  off by default.
- Buckets live in memory in a bounded Caffeine cache (expire after 10 minutes
  of inactivity, maximum 100,000 keys) so random keys cannot exhaust memory.
- All limits are configuration (`partnerguard.rate-limit.*`); tests use small
  limits.
- `/actuator/health` is counted by layer 1 only.

The effective order becomes: **IP flood limit → JWT validation → user rate
limit → role check → ownership check → controller**. This differs from the
diagram in the source document, which is updated in the README.

## Consequences

- A forged token can't dodge or drain anyone's per-user bucket.
- Many users behind one NAT share the 120/min IP ceiling. That's acceptable
  for this scope; the value is configurable.
- In-memory buckets do not survive restarts and are not shared across
  instances. That's fine because deployment is a single local instance
  (non-goal: cloud). A distributed backend (Bucket4j + Redis/PostgreSQL) would
  need a new ADR.

## Alternatives considered

| Option | Why not |
|---|---|
| One filter before JWT validation, keyed by unverified `sub` | Bypass and denial-of-service against other users (see Context) |
| One filter after JWT validation only | Invalid-token floods are never limited |
| Decode `sub` without verifying, then verify later | Same flaw as the first option |
| `bucket4j-spring-boot-starter` | Spring Boot 4 support unconfirmed; the filters are small enough to own |
| Rate limit at a gateway (NGINX, Spring Cloud Gateway) | Extra component; the project must demonstrate it in the API |
