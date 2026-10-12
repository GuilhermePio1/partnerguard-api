-- Contracts between the insurer and a partner (specs/contracts.md). Read-only for the application.
CREATE TABLE contract (
    id          uuid  PRIMARY KEY,
    partner_id  uuid  NOT NULL,
    start_date  date  NOT NULL,
    end_date    date  NOT NULL,
    terms       text  NOT NULL,
    CONSTRAINT fk_contract_partner_id FOREIGN KEY (partner_id) REFERENCES partner (id) ON DELETE RESTRICT,
    CONSTRAINT ck_contract_dates CHECK (end_date >= start_date)
);

-- Ownership-scoped queries filter on partner_id (ADR-0004).
CREATE INDEX ix_contract_partner_id ON contract (partner_id);

GRANT SELECT ON contract TO "${appDbUser}";
