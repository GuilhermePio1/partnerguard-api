-- Claims assigned to partners (specs/claims.md).
CREATE TABLE claim (
    id           uuid           PRIMARY KEY,
    policy_id    uuid           NOT NULL,
    partner_id   uuid           NOT NULL,
    description  text           NOT NULL,
    status       varchar(16)    NOT NULL,
    amount       numeric(12, 2) NOT NULL,
    CONSTRAINT fk_claim_policy_id FOREIGN KEY (policy_id) REFERENCES policy (id) ON DELETE RESTRICT,
    CONSTRAINT fk_claim_partner_id FOREIGN KEY (partner_id) REFERENCES partner (id) ON DELETE RESTRICT,
    CONSTRAINT ck_claim_description_length CHECK (char_length(description) BETWEEN 1 AND 2000),
    CONSTRAINT ck_claim_status CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_claim_amount CHECK (amount BETWEEN 0.01 AND 1000000.00)
);

-- Ownership-scoped queries filter on partner_id (ADR-0004); lists filter on status.
CREATE INDEX ix_claim_partner_id ON claim (partner_id);
CREATE INDEX ix_claim_status ON claim (status);

-- Claims are updated through the API but never created or deleted in this scope.
GRANT SELECT, UPDATE ON claim TO "${appDbUser}";
