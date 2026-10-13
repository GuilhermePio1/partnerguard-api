# Architecture Decision Records

Why PartnerGuard is built the way it is. Format and rules: [ADR-0001](0001-record-architecture-decisions.md).
New ADRs start from [the template](0000-template.md). The `new-adr` skill
creates one and updates this index.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-keycloak-stateless-jwt-resource-server.md) | Delegate authentication to Keycloak and run as a stateless JWT resource server | Accepted |
| [0003](0003-roles-and-partner-identity-from-token.md) | Take roles and partner identity only from the validated token | Accepted |
| [0004](0004-ownership-checks-in-service-layer.md) | Enforce ownership in the service layer and answer 404 for other partners' resources | Accepted |
| [0005](0005-deny-by-default.md) | Deny by default | Accepted |
| [0006](0006-two-layer-rate-limiting.md) | Two-layer rate limiting with Bucket4j, keyed only on verified identity | Accepted |
| [0007](0007-contract-first-openapi.md) | Contract-first API with OpenAPI | Accepted |
| [0008](0008-problem-details-for-all-errors.md) | RFC 7807 Problem Details for every error, including security errors | Accepted |
| [0009](0009-claim-update-field-level-permissions.md) | Field-level permissions and a state machine for claim updates | Accepted |
| [0010](0010-cnpj-validation-and-masking.md) | Validate `taxId` as CNPJ (numeric and alphanumeric) and always mask it | Accepted |
| [0011](0011-package-by-feature.md) | Package by feature | Accepted |
| [0012](0012-audit-log-in-same-transaction.md) | Write the audit log synchronously in the same transaction | Accepted |
| [0013](0013-plain-text-input-and-xss.md) | Free-text fields are plain text; SQL-like input is data | Accepted |
| [0014](0014-uuid-identifiers.md) | Use random UUIDs as public identifiers | Accepted |
| [0015](0015-flyway-and-local-seed-data.md) | Flyway for schema; seed data only in the local profile | Accepted (partly superseded by 0019) |
| [0016](0016-security-test-matrix.md) | The security test matrix is the core test suite | Accepted |
| [0017](0017-add-get-claim-by-id.md) | Add `GET /api/v1/claims/{id}`; defer admin CRUD | Accepted |
| [0018](0018-verified-tls-for-database-connections.md) | Require verified TLS (`sslmode=verify-full`) for database connections outside local development | Accepted |
| [0019](0019-repeatable-seed-and-least-privilege-grants.md) | Seed data as a repeatable migration; least-privilege grants per table | Accepted |
| [0020](0020-jwt-validation-details.md) | Validate JWTs with Boot's decoder plus a clock-aware expiry check, and challenge with a bare `Bearer` | Accepted |
| [0021](0021-reject-mixed-roles-and-block-suspended-partners.md) | Reject tokens that mix `PARTNER` with an internal role, and block suspended or unknown partners | Accepted |
| [0022](0022-rate-limit-anonymous-bucket-and-refusals.md) | Count every unauthenticated request in the anonymous bucket, and let refusals take no tokens | Accepted |
