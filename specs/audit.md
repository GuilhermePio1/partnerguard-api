# Audit log and security logging

## Scope

Audit trail for write operations and security event logging. Related:
[ADR-0012](../docs/adr/0012-audit-log-in-same-transaction.md),
[ADR-0023](../docs/adr/0023-audit-details-and-server-generated-correlation-ids.md).

## Model: `audit_log`

| Column | Type | Rules |
|---|---|---|
| `id` | uuid | |
| `user_id` | text | JWT `sub` |
| `action` | text | `PARTNER_CREATED`, `CLAIM_UPDATED` |
| `resource` | text | `<type>:<uuid>`, e.g. `claim:40000000-…a001` |
| `details` | jsonb | Field names only, never values: `{"fields":["status"]}` (rule 2) |
| `timestamp` | timestamptz | UTC, from injected `Clock` |

## Rules

1. Every successful write operation inserts exactly one audit row in the same
   transaction. A failed write inserts none. The audit insert can only run
   inside the write's transaction: called without one, it fails.
2. `details.fields` lists names, never values:
   - `PARTNER_CREATED`: the fields the request set (`legalName`, `taxId`);
   - `CLAIM_UPDATED`: the fields the PATCH carried, in the order `status`,
     `description`, `amount`, even when a value equals the current one.
3. Audit rows never contain field values, tokens, tax ids or request bodies.
4. The application database role has only `SELECT, INSERT` on `audit_log`.
5. Security events go to the application log, not to `audit_log`, as structured
   key/value pairs at WARN:

   | Event | When | Fields |
   |---|---|---|
   | `authentication_failed` | `401` | ip, method, path, reason |
   | `access_denied` | `403` | sub, roles, method, path, reason |
   | `ownership_denied` | ownership `404` | sub, partnerId, resourceType, resourceId |
   | `rate_limited` | `429` | limit, keyType, key, method, path (at most once per key per minute) |

   Client-supplied values (path, sub) are sanitized: no control characters or
   quotes, bounded length.
6. Logs never contain `Authorization` headers, tokens or unmasked tax ids.

## Acceptance criteria

| ID | Given / When / Then | Type |
|---|---|---|
| AUD-01 | Given a successful `POST /partners`, then one `PARTNER_CREATED` row with the admin's `sub` | Integration |
| AUD-02 | Given a successful `PATCH /claims/{id}`, then one `CLAIM_UPDATED` row with `details.fields` listing the changed fields | Integration |
| AUD-03 | Given a rejected write (`400`/`403`/`404`/`409`), then no audit row | Integration |
| AUD-04 | Given the app's DB role, when executing `UPDATE audit_log ...` or `DELETE FROM audit_log`, then a permission error | Integration |
| AUD-05 | Given any audit row, then `details` contains no values from the request body | Integration |
| AUD-06 | Given each security event type, then exactly one log line with the listed fields and no `Authorization` value | Slice |
| AUD-07 | Given a fixed `Clock`, then `timestamp` equals the clock's instant | Unit |
| AUD-08 | Given no transaction, when the audit service is asked to record an entry, then it fails and writes no row | Integration |

## Open questions

- Retention period for audit rows and logs isn't defined.
