-- Insurance policies (specs/policies.md). Read-only for the application (ADR-0017).
CREATE TABLE policy (
    id             uuid         PRIMARY KEY,
    policy_number  varchar(30)  NOT NULL,
    holder_name    text         NOT NULL,
    vehicle_plate  varchar(7)   NOT NULL,
    status         varchar(16)  NOT NULL,
    CONSTRAINT uq_policy_policy_number UNIQUE (policy_number),
    CONSTRAINT ck_policy_policy_number_format CHECK (policy_number ~ '^[A-Z0-9-]{6,30}$'),
    CONSTRAINT ck_policy_holder_name_length CHECK (char_length(holder_name) BETWEEN 1 AND 200),
    CONSTRAINT ck_policy_vehicle_plate_format CHECK (vehicle_plate ~ '^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$'),
    CONSTRAINT ck_policy_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED'))
);

GRANT SELECT ON policy TO "${appDbUser}";
