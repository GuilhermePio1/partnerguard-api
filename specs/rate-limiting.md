# Rate limiting

## Scope

Per-IP and per-user request limits. Related:
[ADR-0006](../docs/adr/0006-two-layer-rate-limiting.md),
[ADR-0022](../docs/adr/0022-rate-limit-anonymous-bucket-and-refusals.md).

## Rules

1. Layer 1, `PreAuthRateLimitFilter`, runs before JWT validation: a bucket
   per client IP, **120 req/min**, for every request.
2. Layer 2, `UserRateLimitFilter`, runs after JWT validation and before
   authorization:
   - authenticated callers: a bucket per verified `sub`, **60 req/min**;
   - write operations (`POST`, `PUT`, `PATCH`, `DELETE`) also consume from a
     second bucket per `sub`, **20 req/min**;
   - anonymous callers: a bucket per IP, **60 req/min**. Anonymous means any
     request that reaches layer 2 without an authentication, whatever the path:
     the public paths (`/actuator/health` included) and protected paths called
     without a token, which consume the anonymous bucket before they get their
     `401`. A request with an invalid token never reaches layer 2: JWT
     validation answers `401` first.
3. Buckets refill greedily over the minute (Bucket4j `refillGreedy`), not in
   one burst at the end of the window.
4. A request takes one token from each layer it reaches, and no more:
   - a request rejected by JWT validation (`401`) has already taken its
     layer-1 token;
   - a layer-1 rejection never reaches JWT validation or layer 2;
   - a rejected request takes nothing from the buckets of the layer that
     rejected it. A write refused by the write bucket doesn't spend the user
     bucket, and a write refused by the user bucket doesn't spend the write
     bucket. A caller who waits `Retry-After` therefore gets through.
5. Over the limit → `429` Problem Details
   (`type: https://problems.partnerguard.example/rate-limit-exceeded`) with
   `Retry-After` in whole seconds (rounded up, ≥ 1).
6. Client IP = `request.getRemoteAddr()`. `X-Forwarded-For` is ignored unless a
   trusted proxy is configured (`server.forward-headers-strategy`).
7. Buckets are held in a bounded Caffeine cache (max 100,000 keys, expire after
   10 min of inactivity).
8. All values are configurable:
   ```yaml
   partnerguard.rate-limit:
     ip-ceiling-per-minute: 120
     user-per-minute: 60
     user-writes-per-minute: 20
     anonymous-per-minute: 60
   ```
9. Every `429` is logged as `event=rate_limited` with the key type (`ip`/`sub`),
   key and path, at most once per key per minute.

## Acceptance criteria

Tests use small configured limits (e.g. 10/3/2/3) to stay fast.

| ID | Given / When / Then | Type |
|---|---|---|
| RL-01 | Given user `shop-a`, when sending `user-per-minute + 1` GETs within a minute, then the last one is `429` with `Retry-After ≥ 1` and Problem Details | Slice |
| RL-02 | Given user `shop-a` is limited, when `analyst` calls from the same IP, then `200` (buckets are per `sub`) | Slice |
| RL-03 | Given `user-writes-per-minute + 1` PATCH calls by one user, then the last one is `429` while that user's GETs still succeed | Slice |
| RL-04 | Given forged tokens (invalid signature) each with a random `sub`, when sent from one IP more than `ip-ceiling-per-minute` times, then `429` (the forged `sub` doesn't create new buckets) | Slice |
| RL-05 | Given forged tokens carrying `analyst`'s `sub`, when sent many times, then `analyst`'s own bucket is untouched (a valid `analyst` call still returns `200`) | Slice |
| RL-06 | Given anonymous calls to `/actuator/health`, when exceeding the anonymous limit, then `429` | Slice |
| RL-07 | Given a `429`, when waiting `Retry-After` seconds (fake clock), then the next call succeeds | Unit |
| RL-08 | Given a request with `X-Forwarded-For: 1.2.3.4` and no trusted proxy configured, then the bucket key is the socket address | Unit |
| RL-09 | Given 200,000 distinct keys, then the cache holds at most 100,000 buckets | Unit |
| RL-10 | Given the local stack, `scripts/rate-limit-check.*` observes `429` with `Retry-After` and Problem Details within 100 calls (greedy refill adds about one token per second, so the first `429` arrives a few calls after the 60th) | Script |
| RL-11 | Given a key over its limit, when it gets several `429` within a minute, then exactly one `event=rate_limited` log line with the key type, the key and the path; after a minute (fake clock), the next `429` logs again | Slice |
| RL-12 | Given anonymous calls to `GET /api/v1/claims` without a token, when sending `anonymous-per-minute + 1` of them, then the last one is `429`, not `401` | Slice |
| RL-13 | Given the IP ceiling is exhausted, when a request with a token arrives from that IP, then `429` and the token is never decoded | Slice |

With the example limits, RL-02 needs `ip-ceiling-per-minute ≥ user-per-minute + 2`
(both users share one IP), and RL-03 needs `user-per-minute ≥ user-writes-per-minute + 1`
so the GET after the refused write still has a token.

## Open questions

- Should responses expose remaining quota (`RateLimit-*` headers, IETF draft)?
  Not in the brief; not implemented.
