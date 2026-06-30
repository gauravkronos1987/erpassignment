-- ============================================================
-- V2: Seed data for local testing
-- ============================================================

INSERT INTO tenant (tenant_id, name, base_currency) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Acme Corp', 'USD');

INSERT INTO entity (entity_id, tenant_id, parent_entity_id, legal_name, functional_currency) VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', NULL, 'Acme US Inc', 'USD'),
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Acme Europe GmbH', 'EUR');

INSERT INTO app_user (user_id, tenant_id, name, email) VALUES
    ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'Alice (AR Clerk)', 'alice@acme.com'),
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'Bob (AR Manager)', 'bob@acme.com');

-- Chart of accounts for Acme US Inc
INSERT INTO gl_account (gl_account_id, entity_id, account_code, account_name, account_type, normal_balance) VALUES
    ('66666666-6666-6666-6666-666666666661', '22222222-2222-2222-2222-222222222222', '1200', 'Accounts Receivable', 'ASSET', 'DEBIT'),
    ('66666666-6666-6666-6666-666666666662', '22222222-2222-2222-2222-222222222222', '1000', 'Cash', 'ASSET', 'DEBIT'),
    ('66666666-6666-6666-6666-666666666663', '22222222-2222-2222-2222-222222222222', '4000', 'Revenue - Services', 'REVENUE', 'CREDIT'),
    ('66666666-6666-6666-6666-666666666664', '22222222-2222-2222-2222-222222222222', '4010', 'Revenue - Products', 'REVENUE', 'CREDIT');

INSERT INTO customer (customer_id, tenant_id, entity_id, name, billing_currency, payment_terms_days) VALUES
    ('77777777-7777-7777-7777-777777777771', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Globex Industries', 'USD', 30),
    ('77777777-7777-7777-7777-777777777772', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Initech LLC', 'USD', 45);

INSERT INTO accounting_period (entity_id, period, status) VALUES
    ('22222222-2222-2222-2222-222222222222', '2026-06', 'OPEN'),
    ('22222222-2222-2222-2222-222222222222', '2026-05', 'CLOSED');
