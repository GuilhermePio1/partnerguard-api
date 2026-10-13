-- Audit trail of successful writes (specs/audit.md, ADR-0012, ADR-0023).
-- details holds field names only, never values.
CREATE TABLE audit_log (
    id         uuid        PRIMARY KEY,
    user_id    text        NOT NULL,
    action     varchar(32) NOT NULL,
    resource   text        NOT NULL,
    details    jsonb       NOT NULL,
    timestamp  timestamptz NOT NULL,
    CONSTRAINT ck_audit_log_action CHECK (action IN ('PARTNER_CREATED', 'CLAIM_UPDATED')),
    CONSTRAINT ck_audit_log_resource CHECK (resource ~ '^[a-z]+:[0-9a-f-]{36}$')
);

-- Append-only for the application: no UPDATE, no DELETE (AUD-04).
GRANT SELECT, INSERT ON audit_log TO "${appDbUser}";
