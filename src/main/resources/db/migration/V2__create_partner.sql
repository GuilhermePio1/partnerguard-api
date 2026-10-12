-- Partner repair shops (specs/partners.md). tax_id is the normalized CNPJ:
-- 14 uppercase characters, alphanumeric base and numeric check digits (ADR-0010).
CREATE TABLE partner (
    id          uuid         PRIMARY KEY,
    legal_name  text         NOT NULL,
    tax_id      varchar(14)  NOT NULL,
    status      varchar(16)  NOT NULL,
    CONSTRAINT uq_partner_tax_id UNIQUE (tax_id),
    CONSTRAINT ck_partner_legal_name_length CHECK (char_length(legal_name) BETWEEN 3 AND 200),
    CONSTRAINT ck_partner_tax_id_format CHECK (tax_id ~ '^[0-9A-Z]{12}[0-9]{2}$'),
    CONSTRAINT ck_partner_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- Partners are created through the API but never updated or deleted in this scope.
GRANT SELECT, INSERT ON partner TO "${appDbUser}";
