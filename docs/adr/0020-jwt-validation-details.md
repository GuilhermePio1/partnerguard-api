# ADR-0020: Validate JWTs with Boot's decoder plus a clock-aware expiry check, and challenge with a bare `Bearer`

- **Status:** Accepted
- **Date:** 2026-10-13
- **Deciders:** Project owner (approved on 2026-10-13)
- **Related:** [ADR-0002](0002-keycloak-stateless-jwt-resource-server.md) (complements it; nothing there changes), [specs/authentication.md](../../specs/authentication.md), [ADR-0008](0008-problem-details-for-all-errors.md)

## Context

ADR-0002 decides *what* the API validates (signature, `iss`, `aud`, `exp`,
`nbf`, RS256 only, 30 s skew) and that failures are `401`. Implementing it in
milestone 3 raised three questions it doesn't answer:

- Spring Boot already builds a `JwtDecoder` from `issuer-uri` and `audiences`,
  but its expiry check uses the system clock with a 60 s skew, and with an
  issuer URI it accepts whatever algorithms the JWKS advertises.
- Spring Security's default `401` challenge copies the failure into
  `WWW-Authenticate: Bearer error_description="..."` (for example "Jwt expired
  at ...", "Invalid signature", "The iss claim is not valid"). The spec's rule 5
  originally named only the body.
- An expiry test must reach `exp + 31 s` on a 300 s token. CLAUDE.md already
  forbids `sleep` in rate-limit tests for the same reason.

## Decision

- **Decoder:** keep Spring Boot's auto-configured decoder (issuer discovery,
  JWKS, issuer and audience validators), configured only through
  `spring.security.oauth2.resourceserver.jwt.issuer-uri` and `audiences`. Add
  two beans that Boot applies to it:
  - a `JwtTimestampValidator` with a 30 s skew that reads the application
    `Clock`, which replaces Boot's default expiry check;
  - a `JwkSetUriJwtDecoderBuilderCustomizer` that limits signatures to RS256.
- **Clock:** one `java.time.Clock` bean (`Clock.systemUTC()`) is the only source
  of "now" for security decisions: token expiry now, rate-limit refills in
  milestone 5. Tests replace it with a fake clock.
- **Challenge:** every `401`, whatever check failed, has exactly
  `WWW-Authenticate: Bearer` (no `error`, no `error_description`) and the same
  `unauthorized` Problem Details body. The reason is logged as
  `event=authentication_failed` with IP, method, path (without the query
  string) and reason. Logged values are stripped of control characters and
  truncated to 200 characters.

## Consequences

- Little custom security code: the decoder, JWKS caching and key rotation stay
  Spring Boot's. A Boot upgrade that changes how extra validators are applied
  would show up in `AUTHN-04` and `AUTHN-05`.
- Clients can't tell an expired token from a forged one. RFC 6750's optional
  `error="invalid_token"` hint is lost, so a client simply fetches a new token
  on any `401`.
- Tests move time instead of sleeping; the same bean serves the rate limits.
- Proven by `AUTHN-02`–`AUTHN-06` (signature, issuer, audience, expiry with the
  29 s / 31 s skew boundary, `alg: none` and HS256 key confusion), `AUTHN-10`
  (neither body nor header reveals the reason) and `AUTHN-11` (one log event,
  no token).

## Alternatives considered

| Option | Why not |
|---|---|
| Own `JwtDecoder` bean (`NimbusJwtDecoder.withIssuerLocation`) | Re-implements what Boot already does; Boot backs off entirely, so issuer and audience validation become our code to maintain |
| Boot's decoder unchanged (60 s skew, system clock) | Doesn't meet the specified 30 s skew; expiry can't be tested without waiting minutes |
| Spring's default challenge with `error_description` | Tells an attacker which check failed, the information rule 5 keeps out of the body |
| `WWW-Authenticate: Bearer error="invalid_token"` without description | Harmless, but distinguishes "no token" from "bad token" for no client benefit; the bare challenge is what the spec states |
