# ADR-0002: Delegate authentication to Keycloak and run as a stateless JWT resource server

- **Status:** Accepted
- **Date:** 2026-10-12
- **Deciders:** Project owner
- **Related:** [specs/authentication.md](../../specs/authentication.md)

## Context

Every request must be authenticated through an OpenID Connect provider. Custom
login code is a common source of authentication failures (OWASP A07). The API
has no frontend and no need for server-side sessions.

## Decision

- Keycloak (realm `partnerguard`) is the only identity provider.
- The API is a Spring Security **OAuth2 Resource Server** that accepts only
  JWT access tokens signed by Keycloak. Keys are fetched from the realm's JWKS
  endpoint and cached by Spring Security.
- On every request the API validates **signature, issuer
  (`http://localhost:8180/realms/partnerguard` locally), audience
  (`partnerguard-api`), expiration and not-before**. Missing audience → `401`.
- Allowed signing algorithm: `RS256` only.
- Access token lifespan: 5 minutes (configured in Keycloak). Refresh is the
  client's job.
- `SessionCreationPolicy.STATELESS`; no `JSESSIONID`, no form login, no HTTP
  Basic, no custom login endpoint. CSRF protection is disabled because there are
  no cookies or sessions to ride on; this is safe only while that stays true.
- The audience claim is added by a Keycloak *audience mapper* on the client
  scope, so tokens issued for other clients in the realm are rejected.

## Consequences

- Login, password policy, MFA and brute-force protection live in Keycloak.
- The API cannot revoke a token before it expires; the 5-minute lifespan bounds
  that window.
- Tests use `spring-security-test` JWT post-processors for slice tests and a
  real Keycloak (Testcontainers) only where the full flow matters.
- Issuer and audience are configuration (`application.yml`), never hard-coded.

## Alternatives considered

| Option | Why not |
|---|---|
| Opaque tokens + introspection | One extra network call per request; JWT is enough for a short lifespan |
| Session-based login in the API | Out of scope; reintroduces CSRF and session fixation risks |
| Spring Authorization Server embedded | Mixes identity and business concerns in one service |
