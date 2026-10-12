# PartnerGuard API

A secure REST API for managing insurance policies and partner repair shops, built with Java 21 and Spring Boot.

The project shows how to protect a business API end to end: authentication with OAuth2/OIDC, role-based and ownership-based authorization, rate limiting, and defenses against the most common OWASP risks.

---

## 1. Context

Insurance companies work with networks of partner repair shops. Each shop needs access to its own contracts and claims, and nothing else. Internal staff need a broader view.

In legacy systems this separation is often weak. Endpoints may be public, data may be filtered only on the frontend, and sensitive fields may travel in plain text. PartnerGuard is a reference implementation of how this API should be built and secured.

## 2. Goals

- Authenticate every request through an OpenID Connect provider (Keycloak).
- Make sure a partner can only read and change its own data.
- Limit abusive traffic per client.
- Prevent SQL injection, XSS and exposure of sensitive data.
- Make every security decision testable and documented.

### Non-goals

- A frontend.
- Real payment or claims processing logic.
- Cloud deployment. Everything runs locally with Docker.

## 3. Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4, Spring Web, Spring Data JPA |
| Security | Spring Security, OAuth2 Resource Server (JWT) |
| Identity provider | Keycloak |
| Database | PostgreSQL |
| Migrations | Flyway |
| Rate limiting | Bucket4j |
| Validation | Jakarta Bean Validation |
| API docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Mockito, Spring Security Test, Testcontainers |
| Build and CI | Maven, GitHub Actions |
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
┌──────────────────────────────────────────┴─────┐
│               PartnerGuard API                  │
│                                                 │
│  Rate limit filter → JWT validation →           │
│  Role check → Ownership check → Controller      │
│                         │                       │
│                  Service → Repository           │
└─────────────────────────┬───────────────────────┘
                          ▼
                   ┌────────────┐
                   │ PostgreSQL │
                   └────────────┘
```

Every request goes through the same chain: rate limit, token validation, role check and ownership check, in that order. A request that fails any step never reaches the business logic.

## 5. Domain model

| Entity | Main fields | Notes |
|---|---|---|
| `Partner` | id, legalName, taxId, status | `taxId` (company registration number) is masked in responses |
| `Policy` | id, policyNumber, holderName, vehiclePlate, status | |
| `Contract` | id, partnerId, startDate, endDate, terms | Belongs to one partner |
| `Claim` | id, policyId, partnerId, description, status, amount | Assigned to one partner |
| `AuditLog` | id, userId, action, resource, timestamp | Written for every write operation |

## 6. Roles and permissions

Roles come from Keycloak and are mapped from the JWT.

| Role | Who | Can do |
|---|---|---|
| `ADMIN` | Internal staff | Manage partners, policies, contracts and claims |
| `ANALYST` | Internal staff | Read everything, update claim status |
| `PARTNER` | Repair shop user | Read its own contracts and claims, update its own claims |

The partner's identity comes from a `partner_id` claim in the token, never from the request body or URL.

### Ownership rule

A role check alone is not enough. A `PARTNER` user calling `GET /claims/{id}` for a claim that belongs to another partner gets `404 Not Found`, not `403 Forbidden`. This avoids confirming that the resource exists.

## 7. API endpoints

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/v1/partners` | ADMIN, ANALYST | List partners |
| POST | `/api/v1/partners` | ADMIN | Create a partner |
| GET | `/api/v1/partners/me` | PARTNER | Current partner's profile |
| GET | `/api/v1/contracts` | all | List contracts (partners see only their own) |
| GET | `/api/v1/contracts/{id}` | all | Contract details (ownership enforced) |
| GET | `/api/v1/claims` | all | List claims (partners see only their own) |
| PATCH | `/api/v1/claims/{id}` | ANALYST, PARTNER | Update a claim (ownership enforced) |
| GET | `/api/v1/policies/{number}` | ADMIN, ANALYST | Find a policy |
| GET | `/actuator/health` | public | Health check |

All list endpoints are paginated. Interactive docs are available at `/swagger-ui.html`.

## 8. Security design

### 8.1 Authentication

- The API is an OAuth2 Resource Server and accepts only JWTs signed by Keycloak.
- It checks signature, issuer, audience and expiration on every request.
- Access tokens are short-lived (5 minutes). Refresh is handled by the client.
- There are no sessions and no custom login endpoint.

### 8.2 Authorization

- Role checks with `@PreAuthorize` on each endpoint.
- Ownership checks in the service layer, so they apply no matter which controller calls them.
- Deny by default: any endpoint without an explicit rule is blocked.

### 8.3 Rate limiting

- Token bucket per authenticated user (by `sub` claim), and per IP for unauthenticated calls.
- Default limit: 60 requests per minute. Stricter limit on write endpoints.
- Exceeding the limit returns `429 Too Many Requests` with a `Retry-After` header.

### 8.4 OWASP coverage

| Risk | Mitigation |
|---|---|
| Broken access control | Role checks plus ownership checks; deny by default; 404 for resources of other partners |
| Injection (SQL) | Only parameterized queries through JPA; no string concatenation in queries |
| Injection (XSS) | Input validation with Bean Validation; output encoding; JSON responses only; security headers |
| Cryptographic failures | No sensitive data in plain text; `taxId` masked in responses; TLS expected in production |
| Security misconfiguration | Hardened headers (CSP, X-Content-Type-Options, X-Frame-Options); stack traces never returned; actuator restricted |
| Identification and authentication failures | Delegated to Keycloak; short-lived tokens; full JWT validation |
| Vulnerable components | Dependency scanning in CI (OWASP Dependency-Check) |
| Security logging failures | Audit log for every write operation; failed access attempts logged |

### 8.5 Error handling

All errors follow the RFC 7807 Problem Details format. Error responses never include stack traces, SQL messages or internal class names.

## 9. Project structure

```
partnerguard-api/
├── src/main/java/com/partnerguard/
│   ├── config/          # Security, rate limiting, OpenAPI
│   ├── partner/         # Controller, service, repository, DTOs
│   ├── contract/
│   ├── claim/
│   ├── policy/
│   ├── audit/
│   └── common/          # Error handling, ownership checks, masking
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/    # Flyway scripts
├── src/test/java/...    # Unit and integration tests
├── keycloak/
│   └── realm-export.json
├── docker-compose.yml
└── .github/workflows/ci.yml
```

The code is organized by feature, not by layer, so each domain area stays together.

## 10. Running locally

Requirements: Docker and Java 21.

```bash
git clone https://github.com/<your-user>/partnerguard-api.git
cd partnerguard-api
docker compose up -d
./mvnw spring-boot:run
```

This starts PostgreSQL and Keycloak with a pre-configured realm and three test users:

| User | Password | Role |
|---|---|---|
| `admin` | `admin` | ADMIN |
| `analyst` | `analyst` | ANALYST |
| `shop-a` | `shop-a` | PARTNER (Partner A) |

Get a token:

```bash
curl -X POST http://localhost:8180/realms/partnerguard/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=partnerguard-cli" \
  -d "username=shop-a" \
  -d "password=shop-a"
```

Then call the API:

```bash
curl http://localhost:8080/api/v1/claims \
  -H "Authorization: Bearer <access_token>"
```

> The test users and the password grant are for local development only.

## 11. Testing strategy

| Type | What it covers |
|---|---|
| Unit tests | Services, ownership rules, masking |
| Security tests | Each endpoint with each role: allowed, forbidden, and other partner's data |
| Integration tests | Full flow with PostgreSQL in Testcontainers |
| Rate limit tests | Limit reached returns 429 with `Retry-After` |
| Injection tests | Malicious payloads (SQL and script) are rejected or neutralized |

The security tests are the core of the project. Every rule in sections 6 and 8 has at least one test proving it works and one proving it can't be bypassed.

## 12. CI pipeline

On every push and pull request, GitHub Actions runs:

1. Build and unit tests
2. Integration tests with Testcontainers
3. OWASP Dependency-Check
4. Test coverage report

## 13. Milestones

| # | Milestone | Done when |
|---|---|---|
| 1 | Project setup | Spring Boot app, PostgreSQL and Flyway running in Docker Compose |
| 2 | Domain and CRUD | Entities, repositories and endpoints working without security |
| 3 | Authentication | Keycloak realm configured; API accepts only valid JWTs |
| 4 | Authorization | Role and ownership checks with full test coverage |
| 5 | Rate limiting | Bucket4j limits per user and per IP, with tests |
| 6 | OWASP hardening | Validation, masking, headers, error handling and audit log |
| 7 | CI and docs | Pipeline green, Swagger complete, README finished |

## 14. Definition of done

- `docker compose up` followed by `./mvnw spring-boot:run` is enough to run everything.
- All tests pass in CI.
- Every security decision is explained in this README.
- No secrets committed to the repository.

## 15. What this project demonstrates

- Building a REST API with modern Java and Spring Boot
- Implementing OAuth2/OIDC authentication with an external identity provider
- Designing authorization beyond roles, with data ownership
- Applying OWASP practices in a real business domain
- Writing security tests that prove the rules hold
