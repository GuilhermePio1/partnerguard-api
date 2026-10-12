-- Local and test fixtures. Values must match specs/fixtures.md exactly.
-- Repeatable migration (ADR-0019): runs after all versioned migrations and again
-- whenever this file changes, so every statement is an idempotent upsert.

INSERT INTO partner (id, legal_name, tax_id, status) VALUES
    ('10000000-0000-4000-8000-00000000000a', 'Oficina Alfa Ltda', '11222333000181', 'ACTIVE'),
    ('10000000-0000-4000-8000-00000000000b', 'Oficina Beta Ltda', '98765432000198', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    legal_name = EXCLUDED.legal_name, tax_id = EXCLUDED.tax_id, status = EXCLUDED.status;

INSERT INTO policy (id, policy_number, holder_name, vehicle_plate, status) VALUES
    ('20000000-0000-4000-8000-000000000001', 'POL-2026-000001', 'Maria Silva', 'ABC1D23', 'ACTIVE'),
    ('20000000-0000-4000-8000-000000000002', 'POL-2026-000002', 'João Souza', 'XYZ9876', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    policy_number = EXCLUDED.policy_number, holder_name = EXCLUDED.holder_name,
    vehicle_plate = EXCLUDED.vehicle_plate, status = EXCLUDED.status;

INSERT INTO contract (id, partner_id, start_date, end_date, terms) VALUES
    ('30000000-0000-4000-8000-00000000000a', '10000000-0000-4000-8000-00000000000a',
     DATE '2026-01-01', DATE '2026-12-31', 'Standard repair agreement, Partner A'),
    ('30000000-0000-4000-8000-00000000000b', '10000000-0000-4000-8000-00000000000b',
     DATE '2026-01-01', DATE '2026-12-31', 'Standard repair agreement, Partner B')
ON CONFLICT (id) DO UPDATE SET
    partner_id = EXCLUDED.partner_id, start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date, terms = EXCLUDED.terms;

INSERT INTO claim (id, policy_id, partner_id, description, status, amount) VALUES
    ('40000000-0000-4000-8000-00000000a001', '20000000-0000-4000-8000-000000000001',
     '10000000-0000-4000-8000-00000000000a', 'Front bumper replacement', 'SUBMITTED', 1250.50),
    ('40000000-0000-4000-8000-00000000a002', '20000000-0000-4000-8000-000000000002',
     '10000000-0000-4000-8000-00000000000a', 'Left door and paint', 'UNDER_REVIEW', 3400.00),
    ('40000000-0000-4000-8000-00000000b001', '20000000-0000-4000-8000-000000000001',
     '10000000-0000-4000-8000-00000000000b', 'Windshield replacement', 'SUBMITTED', 980.00)
ON CONFLICT (id) DO UPDATE SET
    policy_id = EXCLUDED.policy_id, partner_id = EXCLUDED.partner_id,
    description = EXCLUDED.description, status = EXCLUDED.status, amount = EXCLUDED.amount;
