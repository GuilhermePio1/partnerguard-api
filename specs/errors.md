# Error handling

## Scope

The error format for every response with status ≥ 400. Related:
[ADR-0008](../docs/adr/0008-problem-details-for-all-errors.md).

## Rules

1. Content type `application/problem+json`. Fields: `type`, `title`, `status`,
   optional `detail`, `instance` (request path). `400` adds `errors[]`
   (`field`, `message`).
2. Never in a body: stack traces, exception class names, SQL, constraint or
   table names, library messages, token contents, unmasked tax ids.
3. `500` uses a fixed `detail` ("An unexpected error occurred."). The server
   logs the full exception with a correlation id. The correlation id is
   returned in the `X-Correlation-Id` response header on every response.
4. Errors produced outside MVC (authentication entry point, access denied
   handler, rate-limit filters, unknown paths) use the same format. Anything
   that reaches the servlet `/error` dispatch is rendered by
   `ProblemDetailsErrorController`, which replaces Boot's `BasicErrorController`
   and ignores the `Accept` header. The Whitelabel page is disabled
   (`spring.web.error.whitelabel.enabled=false`). Statuses outside the catalog get
   `type: about:blank` with the standard reason phrase as `title`.
5. The catalog below is implemented once, in `common.error.ProblemType`.

## Catalog

Base URI: `https://problems.partnerguard.example/`

| `type` slug | Status | Title | When |
|---|---|---|---|
| `validation-error` | 400 | Invalid request | Bean Validation, malformed JSON, unknown property, bad UUID, bad sort |
| `unauthorized` | 401 | Authentication required | Missing/invalid/expired token |
| `forbidden` | 403 | Access denied | Role not allowed, field not allowed, `PARTNER` without `partner_id` |
| `not-found` | 404 | Resource not found | Missing **or** owned by another partner |
| `duplicate-tax-id` | 409 | Partner already exists | Duplicate CNPJ |
| `invalid-status-transition` | 409 | Invalid status transition | State machine violation |
| `claim-not-editable` | 409 | Claim can no longer be edited | `PARTNER` edit of a non-`SUBMITTED` claim |
| `unsupported-media-type` | 415 | Unsupported media type | Body not JSON |
| `rate-limit-exceeded` | 429 | Too many requests | Any bucket exhausted |
| `internal-error` | 500 | Internal error | Anything unhandled |

## Acceptance criteria

| ID | Given / When / Then | Type |
|---|---|---|
| ERR-01 | Given each catalog entry, then the response matches the catalog's `type`, `status` and `title` and has `Content-Type: application/problem+json` | Slice |
| ERR-02 | Given malformed JSON (`{"status":`), then `400` `validation-error` without Jackson's parser message | Slice |
| ERR-03 | Given a controller that throws `RuntimeException("SELECT * FROM secret")`, then `500` whose body contains neither `SELECT` nor `RuntimeException` nor `at com.` | Slice |
| ERR-04 | Given a DB unique violation on `taxId`, then `409` `duplicate-tax-id` and the body doesn't contain the constraint name | Integration |
| ERR-05 | Given a `401` from the entry point, a `403` from the access denied handler and a `429` from the filter, then all three are Problem Details | Slice |
| ERR-06 | Given any response, then `X-Correlation-Id` is present, and for a `500` the same id appears in the server log | Slice |
| ERR-07 | Given an unknown path under `/api/v1` with a valid token, then `404` `not-found` Problem Details | Slice |
| ERR-08 | Given a request that reaches the servlet `/error` dispatch (a filter calling `sendError`, or `GET /error` directly), with `Accept: text/html` or `application/json`, then the response is `application/problem+json`: no HTML (neither the Whitelabel page nor Tomcat's error page) and no Boot default attributes (`timestamp`, `error`, status `999`) | Integration (real server, `RANDOM_PORT`; MockMvc doesn't go through the `/error` dispatch) |

## Open questions

- None.
