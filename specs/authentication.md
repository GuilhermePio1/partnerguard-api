# Authentication

## Scope

How the API authenticates requests. Related:
[ADR-0002](../docs/adr/0002-keycloak-stateless-jwt-resource-server.md),
[ADR-0003](../docs/adr/0003-roles-and-partner-identity-from-token.md),
[fixtures](fixtures.md#keycloak).

## Rules

1. The API MUST accept only JWT access tokens signed by the configured Keycloak
   realm, sent as `Authorization: Bearer <token>`.
2. For every request the API MUST validate: signature (RS256, key from JWKS),
   `iss` equal to the configured issuer, `aud` containing `partnerguard-api`,
   `exp` in the future and `nbf` (if present) in the past. Allowed clock skew: 30 s.
3. Tokens in query parameters (`?access_token=`) or cookies MUST be ignored.
4. The API MUST NOT create HTTP sessions or set cookies.
5. Failure responses are `401` Problem Details with a
   `WWW-Authenticate: Bearer` header. The body doesn't say *which* check failed.
   The server log does.
6. Failed authentications are logged as `event=authentication_failed` with IP,
   method, path and reason (no token contents).
7. Configuration (`application.yml`):
   ```yaml
   spring.security.oauth2.resourceserver.jwt:
     issuer-uri: ${KEYCLOAK_ISSUER:http://localhost:8180/realms/partnerguard}
     audiences: partnerguard-api
   ```
8. Keycloak realm export (`keycloak/realm-export.json`) MUST configure: client
   `partnerguard-cli` (public, direct access grants on, local only), an audience
   mapper for `partnerguard-api`, a user-attribute mapper `partner_id` →
   claim `partner_id` (access token only), an admin-only `partner_id` user
   profile attribute, and a 300 s access token lifespan.

## Acceptance criteria

| ID | Given / When / Then | Type |
|---|---|---|
| AUTHN-01 | Given no `Authorization` header, when calling `GET /api/v1/claims`, then `401` with `WWW-Authenticate: Bearer` and Problem Details | Slice |
| AUTHN-02 | Given a token signed by a different key, then `401` | E2E-auth |
| AUTHN-03 | Given a token with a different `iss`, then `401` | E2E-auth |
| AUTHN-04 | Given a token without `partnerguard-api` in `aud`, then `401` | E2E-auth |
| AUTHN-05 | Given an expired token (exp + 31 s), then `401` | E2E-auth |
| AUTHN-06 | Given a token with `alg: none` or `HS256`, then `401` | E2E-auth |
| AUTHN-07 | Given a valid token from Keycloak for `shop-a`, when calling `GET /api/v1/claims`, then `200` | E2E-auth |
| AUTHN-08 | Given a valid token passed as `?access_token=...` instead of the header, then `401` | Slice |
| AUTHN-09 | Given any authenticated call, then the response has no `Set-Cookie` header | Slice |
| AUTHN-10 | Given a `401`, then the body doesn't contain the words "expired", "signature", "issuer" or "audience" | Slice |
| AUTHN-11 | Given a `401`, then one `authentication_failed` log event is written | Slice |
| AUTHN-12 | Given the Keycloak realm, then an access token for `shop-a` contains `partner_id = 10000000-0000-4000-8000-00000000000a`, `aud` contains `partnerguard-api` and `exp - iat = 300` | E2E-auth |
| AUTHN-13 | Given the local stack, `scripts/get-token.*` returns a token for each fixture user | Script |

## Open questions

- None.
