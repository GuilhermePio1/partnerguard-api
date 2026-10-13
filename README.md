# PartnerGuard API

[![CI](https://github.com/GuilhermePio1/partnerguard-api/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/GuilhermePio1/partnerguard-api/actions/workflows/ci.yml)

A secure REST API for managing insurance policies and partner repair shops, built with Java 21 and Spring Boot 4.

The project shows how to protect a business API end to end: OAuth2/OIDC
authentication, role-based and ownership-based authorization, rate limiting,
and defenses against the most common OWASP risks. Every security decision is
written down, specified and tested.

> **Status:** milestone 4 (authorization) is done. Every endpoint in the
> contract works against PostgreSQL with the seed fixtures, errors are Problem
> Details, and the runtime API docs match the contract. Every request except
> `/actuator/health` (plus Swagger in the `local` profile) needs a valid
> Keycloak access token, and roles, ownership and field permissions are
> enforced. `scripts/smoke-test.*` passes against the local stack.
>
> **Until milestones 5–6 land, these accepted decisions are not in force yet,
> so run it locally only:**
> - No rate limiting (ADR-0006): `scripts/rate-limit-check.*` fails by design
>   until milestone 5.
> - No audit log (ADR-0012); only Spring Security's default security headers.

>
> The original brief is
> [`partnerguard-api.md`](partnerguard-api.md). This README supersedes it where
> they differ, and each difference is marked and linked to an ADR.

---

## Contents

1. [Context](#1-context) · 2. [Goals](#2-goals) · 3. [Tech stack](#3-tech-stack) ·
4. [Architecture](#4-architecture) · 5. [Domain model](#5-domain-model) ·
6. [Roles and permissions](#6-roles-and-permissions) · 7. [API endpoints](#7-api-endpoints) ·
8. [Security design](#8-security-design) · 9. [Project structure](#9-project-structure) ·
10. [Running locally](#10-running-locally) · 11. [Testing strategy](#11-testing-strategy) ·
12. [CI pipeline](#12-ci-pipeline) · 13. [Milestones](#13-milestones) ·
14. [Definition of done](#14-definition-of-done) · 15. [Documentation map](#15-documentation-map)

## 1. Context

Insurance companies work with networks of partner repair shops. Each shop needs
access to its own contracts and claims, and nothing else. Internal staff need a
broader view.

In legacy systems this separation is often weak. Endpoints may be public, data
may be filtered only on the frontend, and sensitive fields may travel in plain
text. PartnerGuard is a reference implementation of how this API should be
built and secured.

## 2. Goals

- Authenticate every request through an OpenID Connect provider (Keycloak).
- Make sure a partner can only read and change its own data.
- Limit abusive traffic per client.
- Prevent SQL injection, XSS and exposure of sensitive data.
- Make every security decision testable and documented.

**Non-goals:** a frontend; real payment or claims processing logic; cloud
deployment (everything runs locally with Docker); admin CRUD for contracts,
policies and claims ([ADR-0017](docs/adr/0017-add-get-claim-by-id.md)).

## 3. Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1, Spring Web MVC, Spring Data JPA |
| Security | Spring Security, OAuth2 Resource Server (JWT) |
| Identity provider | Keycloak |
| Database | PostgreSQL |
| Migrations | Flyway |
| Rate limiting | Bucket4j core + Caffeine ([ADR-0006](docs/adr/0006-two-layer-rate-limiting.md)) |
| Validation | Jakarta Bean Validation |
| API contract | OpenAPI 3.0.3, contract-first with openapi-generator 7.25 ([ADR-0007](docs/adr/0007-contract-first-openapi.md)) |
| API docs | springdoc-openapi 3.x (Swagger UI, `local` profile) |
| Tests | JUnit 5, Mockito, Spring Security Test, Testcontainers, ArchUnit |
| Build and CI | Maven, GitHub Actions, OWASP Dependency-Check, JaCoCo |
| Runtime | Docker Compose |

## 4. Architecture

```
┌────────────┐   1. login (OIDC)    ┌────────────┐
│   Client   │ ───────────────────▶ │  Keycloak  │
│ (Postman / │ ◀─────────────────── │            │
│  Swagger)  │   2. access token    └────────────┘
└─────┬──────┘                            ▲
      │ 3. request + Bearer token         │ 4. public keys (JWKS)
      ▼                                   │
┌──────────────────────────────────────────┴──────────┐
│                  PartnerGuard API                    │
│                                                      │
│  IP flood limit → JWT validation → User rate limit → │
│  Role check → Ownership check → Controller           │
│                          │                           │
│                   Service → Repository               │
└──────────────────────────┬───────────────────────────┘
                           ▼
                    ┌────────────┐
                    │ PostgreSQL │
                    └────────────┘
```

A request that fails any step never reaches the business logic.

> **Changed from the brief:** the brief puts a single rate-limit filter before
> JWT validation but keys it by the token's `sub`. Before validation, `sub` is
> attacker-controlled, so a forged token could dodge its own limit or drain
> someone else's. The limit is therefore split in two: a per-IP ceiling before
> validation and a per-user limit after it.
> See [ADR-0006](docs/adr/0006-two-layer-rate-limiting.md) (approved by the project owner).

Ownership is checked in the service layer (so it applies to every caller),
which is why it appears after the role check even though it runs "inside" the
controller call.

## 5. Domain model

| Entity | Main fields | Notes |
|---|---|---|
| `Partner` | id, legalName, taxId, status (`ACTIVE`, `SUSPENDED`) | `taxId` is a CNPJ (numeric or alphanumeric), always masked in responses |
| `Policy` | id, policyNumber, holderName, vehiclePlate, status (`ACTIVE`, `CANCELLED`, `EXPIRED`) | |
| `Contract` | id, partnerId, startDate, endDate, terms | Belongs to one partner |
| `Claim` | id, policyId, partnerId, description, status, amount | Assigned to one partner; status follows a state machine |
| `AuditLog` | id, userId, action, resource, details, timestamp | Written for every write operation, in the same transaction |

All ids are random UUIDs ([ADR-0014](docs/adr/0014-uuid-identifiers.md)).
Claim status: `SUBMITTED → UNDER_REVIEW → APPROVED | REJECTED` (final states).

## 6. Roles and permissions

Roles come from Keycloak realm roles (`realm_access.roles`) and are mapped
from the JWT. Unknown roles are ignored.

| Role | Who | Can do |
|---|---|---|
| `ADMIN` | Internal staff | Create and list partners; read everything; update any claim field |
| `ANALYST` | Internal staff | Read everything; update claim **status** |
| `PARTNER` | Repair shop user | Read its own profile, contracts and claims; update **description and amount** of its own claims while they are `SUBMITTED` |

The partner's identity comes from the `partner_id` claim in the token, never
from the request body, path, query or headers. A `PARTNER` token without a
valid `partner_id` is rejected with `403`, so a misconfigured account fails
closed ([ADR-0003](docs/adr/0003-roles-and-partner-identity-from-token.md)).
The same `403` on every endpoint applies to a `PARTNER` token that also carries
`ADMIN` or `ANALYST`, and to a partner that is `SUSPENDED` or doesn't exist.
The status is checked on every request, so a suspension also stops tokens
already issued ([ADR-0021](docs/adr/0021-reject-mixed-roles-and-block-suspended-partners.md)).

### Ownership rule

A role check alone is not enough. A `PARTNER` user calling
`GET /api/v1/claims/{id}` for a claim that belongs to another partner gets
`404 Not Found`, not `403 Forbidden`, with the same body as for a claim that
doesn't exist. This avoids confirming that the resource exists
([ADR-0004](docs/adr/0004-ownership-checks-in-service-layer.md)).

### Claim updates: field-level permissions

> **Changed from the brief:** the brief doesn't say which fields each role may
> change, and it leaves ADMIN out of `PATCH /claims/{id}`. Both were decided
> with the project owner ([ADR-0009](docs/adr/0009-claim-update-field-level-permissions.md)).

| Role | `status` | `description` | `amount` | Condition |
|---|---|---|---|---|
| ADMIN | ✔ | ✔ | ✔ | Status changes follow the state machine |
| ANALYST | ✔ | ✘ | ✘ | Status changes follow the state machine |
| PARTNER | ✘ | ✔ | ✔ | Own claims only, only while `SUBMITTED` |

Checks run in this order: body validation (`400`) → field permission (`403`) →
ownership (`404`) → state rules (`409`). The `403` never depends on whether
the claim exists.

## 7. API endpoints

The full contract, with schemas and every status code, is
[`contracts/openapi.yaml`](contracts/openapi.yaml).

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/v1/partners` | ADMIN, ANALYST | List partners |
| POST | `/api/v1/partners` | ADMIN | Create a partner |
| GET | `/api/v1/partners/me` | PARTNER | Current partner's profile |
| GET | `/api/v1/contracts` | all | List contracts (partners see only their own) |
| GET | `/api/v1/contracts/{id}` | all | Contract details (ownership enforced) |
| GET | `/api/v1/claims` | all | List claims (partners see only their own); optional `status` filter |
| GET | `/api/v1/claims/{id}` | all | Claim details (ownership enforced). **Added**, [ADR-0017](docs/adr/0017-add-get-claim-by-id.md) |
| PATCH | `/api/v1/claims/{id}` | ADMIN, ANALYST, PARTNER | Update a claim (field-level permissions, ownership enforced) |
| GET | `/api/v1/policies/{number}` | ADMIN, ANALYST | Find a policy |
| GET | `/actuator/health` | public | Health check |

All list endpoints are paginated (`page` from 0, `size` 1–100, default 20;
`sort` from an allow-list). Interactive docs are at `/swagger-ui.html` in the
`local` profile.

## 8. Security design

### 8.1 Authentication ([ADR-0002](docs/adr/0002-keycloak-stateless-jwt-resource-server.md), [ADR-0020](docs/adr/0020-jwt-validation-details.md))

- The API is an OAuth2 Resource Server and accepts only JWTs signed by Keycloak (RS256).
- On every request it checks signature, issuer, audience (`partnerguard-api`),
  expiration and not-before, with 30 s clock skew.
- Access tokens are short-lived (5 minutes). Refresh is handled by the client.
- Only RS256 is accepted, whatever the JWKS advertises, so `alg: none` and
  HS256 key-confusion tokens are rejected.
- No sessions, no cookies, no custom login endpoint. Only the
  `Authorization` header is read: tokens in query parameters, form bodies or
  cookies are ignored.
- `401` responses are Problem Details with a bare `WWW-Authenticate: Bearer`
  header. Neither the body nor the header says which check failed (Spring's
  default challenge would name it in `error_description`). The server log
  does, as `event=authentication_failed` with IP, method, path and reason,
  never the token.
- Expiry is checked against one application `Clock` bean, so tests move time
  instead of sleeping.

### 8.2 Authorization ([ADR-0004](docs/adr/0004-ownership-checks-in-service-layer.md), [ADR-0005](docs/adr/0005-deny-by-default.md))

- Role checks with `@PreAuthorize` on every controller method. An ArchUnit
  test fails the build if one is missing.
- Ownership checks in the service layer, through partner-scoped queries
  (`findByIdAndPartnerId`), so they apply no matter which controller calls
  them.
- Deny by default: anything without an explicit rule is blocked. Only
  `/actuator/health` (and Swagger in `local`) is public; outside `/api/**`
  everything else is `403`.
- Every `/api/**` request first passes a caller gate: misconfigured partner
  accounts (no valid `partner_id`, or an internal role as well) and partners
  that are `SUSPENDED` or don't exist get `403`, with the partner's status read
  from the database on every request
  ([ADR-0021](docs/adr/0021-reject-mixed-roles-and-block-suspended-partners.md)).
- Status code rules: no or invalid token → `401`; wrong role, forbidden field,
  misconfigured, suspended or unknown partner → `403`; another partner's
  resource → `404` (same as missing).
- Every `403` is logged as `event=access_denied` (sub, method, path, reason)
  and every partner-scoped `404` as `event=ownership_denied`. The client never
  sees the reason.

### 8.3 Rate limiting ([ADR-0006](docs/adr/0006-two-layer-rate-limiting.md))

| Layer | Runs | Key | Limit |
|---|---|---|---|
| IP flood ceiling | before JWT validation | client IP | 120 req/min, all requests |
| User limit | after JWT validation | verified `sub` | 60 req/min |
| User write limit | after JWT validation | verified `sub` | 20 req/min for `POST/PUT/PATCH/DELETE` |
| Anonymous limit | after JWT validation | client IP | 60 req/min (public endpoints) |

- Token buckets (Bucket4j core) with greedy refill, held in a bounded in-memory
  cache. A single local instance is in scope.
- Exceeding a limit returns `429 Too Many Requests` with `Retry-After` and a
  Problem Details body.
- `X-Forwarded-For` is ignored unless a trusted proxy is configured.

### 8.4 OWASP coverage

| Risk | Mitigation | Decision |
|---|---|---|
| Broken access control | Role checks plus ownership checks; deny by default; `404` for other partners' resources; field-level permissions on claim updates; owner id only from the token | ADR-0003, 0004, 0005, 0009 |
| Injection (SQL) | Only parameterized queries (Spring Data, JPQL named parameters, Criteria); no string concatenation; sort allow-list; static check in CI | ADR-0013 |
| Injection (XSS) | Free text is plain text only (`<`, `>` and control characters rejected); JSON responses only; `nosniff`; restrictive CSP | ADR-0013 |
| Mass assignment | Unknown JSON properties rejected (`400`); request schemas never contain owner ids or status where not allowed | ADR-0003, 0009 |
| Cryptographic failures / sensitive data | `taxId` validated as CNPJ (numeric or alphanumeric) and masked as `**.***.***/****-NN` in every response and log; TLS expected in front of the API in production; database connections require `sslmode=verify-full` outside `local`/`test`, and the app refuses to start without it | ADR-0010, 0018 |
| Security misconfiguration | CSP `default-src 'none'`, `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Cache-Control: no-store`; stack traces never returned; actuator limited to `health` without details; CORS disabled | ADR-0005, 0008 |
| Identification and authentication failures | Delegated to Keycloak; short-lived tokens; full JWT validation; RS256 only | ADR-0002 |
| Vulnerable components | OWASP Dependency-Check in CI, failing on CVSS ≥ 7 | — |
| Security logging failures | Audit log row for every write (same transaction, append-only table); structured security events for `401`, `403`, ownership `404` and `429` | ADR-0012 |
| Enumeration | Random UUIDs; identical `404` for missing and foreign resources | ADR-0004, 0014 |

### 8.5 Error handling ([ADR-0008](docs/adr/0008-problem-details-for-all-errors.md))

All errors, including those produced by Spring Security and the rate-limit
filters, follow RFC 7807 Problem Details (`application/problem+json`). Error
responses never include stack traces, SQL messages, constraint names or
internal class names. The error type catalog is in
[`specs/errors.md`](specs/errors.md). Every response carries an
`X-Correlation-Id` that links it to the server log.

## 9. Project structure

```
partnerguard-api/
├── CLAUDE.md                 # Guidance for Claude Code
├── README.md
├── partnerguard-api.md       # Original brief
├── contracts/
│   └── openapi.yaml          # API contract (source of truth)
├── docs/adr/                 # Architecture Decision Records
├── specs/                    # Behavioral specs with acceptance criteria
├── scripts/                  # Local helpers (.sh and .ps1)
├── .claude/skills/           # Claude Code skills for this repo
├── src/main/java/com/partnerguard/
│   ├── config/               # Security, rate limiting, OpenAPI
│   ├── partner/              # Controller, service, repository, entity, mapper
│   ├── contract/
│   ├── claim/
│   ├── policy/
│   ├── audit/
│   └── common/               # Errors, CurrentUser, ownership, masking, validation
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/         # Flyway schema migrations
│   └── db/seed/              # Local-only seed data
├── src/test/java/...         # Unit, slice, integration and architecture tests
├── docker/postgres/init/      # Creates the non-owner application role
├── keycloak/
│   └── realm-export.json      # (milestone 3)
├── docker-compose.yml
├── .env.example               # Local overrides template (copy to .env)
├── pom.xml, mvnw, mvnw.cmd
└── .github/workflows/ci.yml   # Build and tests (security scans in milestone 7)
```

The code is organized by feature, not by layer
([ADR-0011](docs/adr/0011-package-by-feature.md)). DTOs and API interfaces are
generated from the contract at build time.

## 10. Running locally

Requirements: Docker (Compose v2) and Java 21 (`JAVA_HOME` must point to a
JDK 21 when several are installed).

PostgreSQL runs as two roles: Flyway migrates as the schema owner and the API
connects as `partnerguard_app`, which owns nothing
([ADR-0015](docs/adr/0015-flyway-and-local-seed-data.md)). If port 5432 is
taken by a local PostgreSQL, copy `.env.example` to `.env` and set
`POSTGRES_PORT=5433`.

```bash
git clone https://github.com/GuilhermePio1/partnerguard-api.git
cd partnerguard-api
scripts/env-up.sh            # or: docker compose up -d
./mvnw spring-boot:run       # local profile (reads .env)
```

On Windows PowerShell: `.\scripts\env-up.ps1` and `.\mvnw.cmd spring-boot:run`.

Check it with `curl http://localhost:8080/actuator/health` (`{"status":"UP"}`).

This also starts Keycloak (port 8180) with a pre-configured realm and three
test users. Keycloak keeps no volume, so recreating its container resets the
realm:

| User | Password | Role |
|---|---|---|
| `admin` | `admin` | ADMIN |
| `analyst` | `analyst` | ANALYST |
| `shop-a` | `shop-a` | PARTNER (Partner A) |

The seed also contains Partner B with its own contract and claim, so you can
see that Partner A can't reach them. All ids are listed in
[`specs/fixtures.md`](specs/fixtures.md).

Get a token and call the API:

```bash
TOKEN=$(scripts/get-token.sh shop-a)
curl http://localhost:8080/api/v1/claims -H "Authorization: Bearer $TOKEN"
```

```powershell
$token = .\scripts\get-token.ps1 -User shop-a
curl.exe http://localhost:8080/api/v1/claims -H "Authorization: Bearer $token"
```

Check that the security rules hold against the running stack:

```bash
scripts/smoke-test.sh          # role matrix, ownership 404s, masking, headers
scripts/rate-limit-check.sh    # 429 + Retry-After
```

> The test users, the `partnerguard-cli` client and the password grant are for
> local development only. See [`scripts/README.md`](scripts/README.md).

## 11. Testing strategy

| Type | What it covers |
|---|---|
| Unit tests | Services, ownership rules, field permissions, state machine, CNPJ validation, masking |
| Security tests | Each endpoint with each caller (anonymous, each role, partner without `partner_id`, unmapped role): allowed, forbidden, other partner's data |
| Integration tests | Full flow with PostgreSQL in Testcontainers; migrations; audit transactions and grants |
| Auth tests | Keycloak in Testcontainers: issuer, audience, signature, algorithm, expiry |
| Rate limit tests | Limits reached return `429` with `Retry-After`; forged `sub` can't dodge or drain buckets |
| Injection tests | Script payloads rejected; SQL payloads stored verbatim with no effect |
| Architecture tests | `@PreAuthorize` everywhere, no feature cycles, no cross-feature repository access |
| Contract test | Runtime `/v3/api-docs` matches `contracts/openapi.yaml` |

The security tests are the core of the project
([ADR-0016](docs/adr/0016-security-test-matrix.md)). Every rule in sections 6
and 8 has acceptance criteria in [`specs/`](specs/README.md), at least one test
proving it works and one proving it can't be bypassed. Each test's display name
starts with its criterion ID (e.g. `CLM-12`).

## 12. CI pipeline

On every push to `main` and every pull request, GitHub Actions
([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs:

| # | Step | Status |
|---|---|---|
| 1 | Build: code generated from the contract, compilation | ✅ running |
| 2 | Unit, integration (Testcontainers PostgreSQL) and architecture tests (`./mvnw verify`) | ✅ running |
| 3 | Contract lint (`redocly lint contracts/openapi.yaml`) | Milestone 7 |
| 4 | OWASP Dependency-Check (fails on CVSS ≥ 7) and secret scanning | Milestone 7 |
| 5 | Test coverage report (JaCoCo) | Milestone 7 |

The workflow is hardened like the API: the token is read-only
(`permissions: contents: read`), checkout doesn't persist credentials, and
every action is pinned to a full commit SHA, so a moved tag can't change what
runs. Surefire reports are uploaded when a run fails.

## 13. Milestones

| # | Milestone | Done when |
|---|---|---|
| 1 ✅ | Project setup | Spring Boot app, PostgreSQL and Flyway running in Docker Compose; openapi-generator + Spring Boot 4 compatibility confirmed (ADR-0007) |
| 2 ✅ | Domain and CRUD | Entities, repositories and endpoints working without security; contract drift test green |
| 3 ✅ | Authentication | Keycloak realm configured; API accepts only valid JWTs (`AUTHN-*`) |
| 4 ✅ | Authorization | Role, ownership and field checks with full test coverage (`AUTHZ-*`, `CLM-*`) |
| 5 | Rate limiting | Two-layer Bucket4j limits, with tests (`RL-*`) |
| 6 | OWASP hardening | Validation, masking, headers, error handling and audit log (`HRD-*`, `ERR-*`, `AUD-*`) |
| 7 | CI and docs | Pipeline green, Swagger complete, README finished, smoke tests pass |

## 14. Definition of done

- `docker compose up` followed by `./mvnw spring-boot:run` is enough to run everything.
- All tests pass in CI, and every spec criterion has a test.
- Every security decision is explained in this README and recorded in an ADR.
- No secrets committed to the repository.

## 15. Documentation map

| Where | What |
|---|---|
| [`specs/`](specs/README.md) | What must be true: rules and acceptance criteria per feature |
| [`docs/adr/`](docs/adr/README.md) | Why: decisions and rejected alternatives |
| [`contracts/openapi.yaml`](contracts/openapi.yaml) | The HTTP contract |
| [`scripts/`](scripts/README.md) | Local environment and smoke-test helpers |
| [`CLAUDE.md`](CLAUDE.md), [`.claude/skills/`](.claude/skills) | How Claude Code works in this repo |

### Open questions

These are not decided yet and are not implemented. Each is tracked in its spec:

- An endpoint to change a partner's status ([partners](specs/partners.md#open-questions)).
- Masking of `holderName`/`vehiclePlate` under LGPD ([policies](specs/policies.md#open-questions)).
- ADMIN edits on final-state claims; optimistic locking ([claims](specs/claims.md#open-questions)).

## What this project demonstrates

- Building a REST API with modern Java and Spring Boot, contract-first
- Implementing OAuth2/OIDC authentication with an external identity provider
- Designing authorization beyond roles, with data ownership and field-level rules
- Applying OWASP practices in a real business domain
- Writing security tests that prove the rules hold
