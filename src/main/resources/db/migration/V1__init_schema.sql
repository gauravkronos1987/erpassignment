-- ============================================================
-- V1: Core schema for Multi-Tenant Invoicing & AR Module
-- ============================================================

CREATE TABLE tenant (
    tenant_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    base_currency   CHAR(3) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE entity (
    entity_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenant(tenant_id),
    parent_entity_id    UUID REFERENCES entity(entity_id),
    legal_name          VARCHAR(255) NOT NULL,
    functional_currency CHAR(3) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_entity_tenant ON entity(tenant_id);

CREATE TABLE app_user (
    user_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenant(tenant_id),
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_tenant ON app_user(tenant_id);

CREATE TABLE customer (
    customer_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenant(tenant_id),
    entity_id           UUID NOT NULL REFERENCES entity(entity_id),
    name                VARCHAR(255) NOT NULL,
    billing_currency    CHAR(3) NOT NULL,
    payment_terms_days  INT NOT NULL DEFAULT 30,
    credit_limit        DECIMAL(18,2),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_customer_tenant_entity ON customer(tenant_id, entity_id);

CREATE TABLE gl_account (
    gl_account_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id       UUID NOT NULL REFERENCES entity(entity_id),
    account_code    VARCHAR(20) NOT NULL,
    account_name    VARCHAR(255) NOT NULL,
    account_type    VARCHAR(20) NOT NULL CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    normal_balance  VARCHAR(10) NOT NULL CHECK (normal_balance IN ('DEBIT','CREDIT')),
    UNIQUE(entity_id, account_code)
);
CREATE INDEX idx_gl_account_entity ON gl_account(entity_id);

CREATE TABLE exchange_rate (
    exchange_rate_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_currency        CHAR(3) NOT NULL,
    to_currency           CHAR(3) NOT NULL,
    rate                  DECIMAL(18,8) NOT NULL,
    effective_date        DATE NOT NULL,
    UNIQUE(from_currency, to_currency, effective_date)
);

CREATE TABLE invoice (
    invoice_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenant(tenant_id),
    entity_id               UUID NOT NULL REFERENCES entity(entity_id),
    customer_id              UUID NOT NULL REFERENCES customer(customer_id),
    invoice_number            VARCHAR(50) NOT NULL,
    status                    VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN ('DRAFT','APPROVED','SENT','PARTIALLY_PAID','PAID','VOID','WRITTEN_OFF')),
    invoice_date              DATE NOT NULL,
    due_date                  DATE NOT NULL,
    transaction_currency      CHAR(3) NOT NULL,
    exchange_rate             DECIMAL(18,8) NOT NULL DEFAULT 1,
    subtotal                  DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_total                 DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount               DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_paid                DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_by                 UUID NOT NULL REFERENCES app_user(user_id),
    approved_by                UUID REFERENCES app_user(user_id),
    version                    INT NOT NULL DEFAULT 0,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(entity_id, invoice_number)
);
CREATE INDEX idx_invoice_tenant ON invoice(tenant_id);
CREATE INDEX idx_invoice_customer ON invoice(tenant_id, customer_id);
CREATE INDEX idx_invoice_due_date ON invoice(tenant_id, due_date) WHERE status NOT IN ('PAID','VOID');

CREATE TABLE invoice_line_item (
    line_item_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id              UUID NOT NULL REFERENCES invoice(invoice_id) ON DELETE CASCADE,
    description              VARCHAR(500) NOT NULL,
    quantity                 DECIMAL(18,4) NOT NULL,
    unit_price                DECIMAL(18,4) NOT NULL,
    line_total                 DECIMAL(18,2) NOT NULL,
    revenue_gl_account_id      UUID NOT NULL REFERENCES gl_account(gl_account_id),
    deferred                   BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_line_item_invoice ON invoice_line_item(invoice_id);

CREATE TABLE payment (
    payment_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenant(tenant_id),
    entity_id            UUID NOT NULL REFERENCES entity(entity_id),
    customer_id           UUID NOT NULL REFERENCES customer(customer_id),
    payment_date           DATE NOT NULL,
    amount                  DECIMAL(18,2) NOT NULL,
    payment_currency        CHAR(3) NOT NULL,
    payment_method            VARCHAR(20) NOT NULL CHECK (payment_method IN ('ACH','WIRE','CHECK','CARD')),
    idempotency_key            VARCHAR(100) NOT NULL UNIQUE,
    status                     VARCHAR(20) NOT NULL DEFAULT 'RECORDED'
                               CHECK (status IN ('RECORDED','ALLOCATED','PARTIALLY_ALLOCATED','REVERSED')),
    unallocated_amount          DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_tenant ON payment(tenant_id);
CREATE INDEX idx_payment_customer ON payment(tenant_id, customer_id);

CREATE TABLE payment_allocation (
    allocation_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id            UUID NOT NULL REFERENCES payment(payment_id),
    invoice_id            UUID NOT NULL REFERENCES invoice(invoice_id),
    allocated_amount       DECIMAL(18,2) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_allocation_payment ON payment_allocation(payment_id);
CREATE INDEX idx_allocation_invoice ON payment_allocation(invoice_id);

CREATE TABLE credit_memo (
    credit_memo_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id           UUID NOT NULL REFERENCES invoice(invoice_id),
    amount                 DECIMAL(18,2) NOT NULL,
    reason                  VARCHAR(500),
    reason_code              VARCHAR(30) NOT NULL CHECK (reason_code IN ('DISCOUNT','RETURN','WRITE_OFF','BILLING_ERROR')),
    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPROVED','APPLIED')),
    approved_by               UUID REFERENCES app_user(user_id),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_credit_memo_invoice ON credit_memo(invoice_id);

CREATE TABLE journal_entry (
    journal_entry_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id            UUID NOT NULL REFERENCES entity(entity_id),
    source_type           VARCHAR(30) NOT NULL CHECK (source_type IN ('INVOICE_APPROVAL','PAYMENT_RECEIPT','CREDIT_MEMO','MANUAL_ADJUSTMENT','REVERSAL')),
    source_id              UUID NOT NULL,
    entry_date              DATE NOT NULL,
    posting_period           VARCHAR(7) NOT NULL,  -- e.g. '2026-06'
    reverses_journal_entry_id UUID REFERENCES journal_entry(journal_entry_id),
    created_by                UUID NOT NULL REFERENCES app_user(user_id),
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_journal_entry_source ON journal_entry(source_type, source_id);
CREATE INDEX idx_journal_entry_entity_period ON journal_entry(entity_id, posting_period);

CREATE TABLE journal_entry_line (
    line_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id      UUID NOT NULL REFERENCES journal_entry(journal_entry_id) ON DELETE CASCADE,
    gl_account_id           UUID NOT NULL REFERENCES gl_account(gl_account_id),
    debit_amount             DECIMAL(18,2) NOT NULL DEFAULT 0,
    credit_amount             DECIMAL(18,2) NOT NULL DEFAULT 0,
    CHECK (
        (debit_amount > 0 AND credit_amount = 0) OR
        (credit_amount > 0 AND debit_amount = 0)
    )
);
CREATE INDEX idx_je_line_entry ON journal_entry_line(journal_entry_id);
CREATE INDEX idx_je_line_account ON journal_entry_line(gl_account_id);

CREATE TABLE accounting_period (
    period_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id          UUID NOT NULL REFERENCES entity(entity_id),
    period             VARCHAR(7) NOT NULL,  -- '2026-06'
    status              VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    closed_by            UUID REFERENCES app_user(user_id),
    closed_at             TIMESTAMPTZ,
    UNIQUE(entity_id, period)
);

CREATE TABLE audit_log (
    audit_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    entity_type        VARCHAR(50) NOT NULL,
    entity_record_id     UUID NOT NULL,
    action                VARCHAR(20) NOT NULL CHECK (action IN ('CREATE','UPDATE','STATUS_CHANGE','DELETE_SOFT')),
    field_name             VARCHAR(100),
    old_value                TEXT,
    new_value                 TEXT,
    changed_by                 UUID NOT NULL,
    changed_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_record_id);
CREATE INDEX idx_audit_log_tenant ON audit_log(tenant_id, changed_at);
