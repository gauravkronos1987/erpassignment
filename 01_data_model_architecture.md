# Multi-Tenant Invoicing & Accounts Receivable Module
## Data Model and Architecture Design

---

## 1. Entity Relationship Model

### Core entities and relationships

```
Tenant (Company)
  └── Entity (Legal Entity / Subsidiary)        [1 Tenant : N Entities]
        ├── Customer                             [1 Entity : N Customers]
        │      └── Invoice                       [1 Customer : N Invoices]
        │            ├── InvoiceLineItem          [1 Invoice : N Line Items]
        │            ├── Payment Allocation       [N Invoices : N Payments]
        │            └── CreditMemo               [1 Invoice : N Credit Memos]
        ├── Payment                               [1 Entity : N Payments]
        ├── GLAccount (Chart of Accounts)         [1 Entity : N GL Accounts]
        └── JournalEntry                          [1 Entity : N Journal Entries]
                └── JournalEntryLine               [1 Journal Entry : N Lines]
```

### Entity definitions

**Tenant** — the top-level isolation boundary. One row per customer company on the ERP platform.

| Column | Type | Notes |
|---|---|---|
| tenant_id | UUID (PK) | |
| name | VARCHAR | |
| base_currency | CHAR(3) | tenant-level reporting currency (e.g. USD) |
| created_at | TIMESTAMPTZ | |

**Entity** — a legal entity / subsidiary within a tenant. This is what enables multi-entity structures (parent + subsidiaries) without breaking tenant isolation.

| Column | Type | Notes |
|---|---|---|
| entity_id | UUID (PK) | |
| tenant_id | UUID (FK) | every entity belongs to exactly one tenant |
| parent_entity_id | UUID (FK, nullable) | self-referencing — null for the parent company |
| legal_name | VARCHAR | |
| functional_currency | CHAR(3) | the entity's own books currency, may differ from tenant base_currency |
| is_active | BOOLEAN | |

**Customer**

| Column | Type | Notes |
|---|---|---|
| customer_id | UUID (PK) | |
| tenant_id | UUID (FK) | denormalized for fast tenant-scoped queries (see isolation strategy below) |
| entity_id | UUID (FK) | which subsidiary this customer belongs to |
| name | VARCHAR | |
| billing_currency | CHAR(3) | default currency for this customer's invoices |
| payment_terms_days | INT | e.g. NET 30 |
| credit_limit | DECIMAL(18,2) | optional control |

**Invoice**

| Column | Type | Notes |
|---|---|---|
| invoice_id | UUID (PK) | |
| tenant_id | UUID (FK) | |
| entity_id | UUID (FK) | |
| customer_id | UUID (FK) | |
| invoice_number | VARCHAR | human-readable, sequential per entity |
| status | ENUM | Draft, Approved, Sent, PartiallyPaid, Paid, Void, WrittenOff |
| invoice_date | DATE | |
| due_date | DATE | derived from invoice_date + customer.payment_terms_days |
| transaction_currency | CHAR(3) | currency the invoice is denominated in |
| exchange_rate | DECIMAL(18,8) | rate to entity's functional_currency, locked at approval time |
| subtotal | DECIMAL(18,2) | sum of line items, in transaction_currency |
| tax_total | DECIMAL(18,2) | |
| total_amount | DECIMAL(18,2) | subtotal + tax_total |
| amount_paid | DECIMAL(18,2) | running total, updated on each payment allocation |
| amount_outstanding | DECIMAL(18,2) | computed: total_amount - amount_paid |
| created_by | UUID (FK → User) | |
| approved_by | UUID (FK → User, nullable) | enforces segregation of duties — see Section 3 |
| version | INT | optimistic concurrency control |

**InvoiceLineItem**

| Column | Type | Notes |
|---|---|---|
| line_item_id | UUID (PK) | |
| invoice_id | UUID (FK) | |
| description | VARCHAR | |
| quantity | DECIMAL(18,4) | |
| unit_price | DECIMAL(18,4) | |
| line_total | DECIMAL(18,2) | quantity * unit_price |
| revenue_gl_account_id | UUID (FK) | which revenue account this line posts to — supports multiple revenue streams |
| deferred | BOOLEAN | flags whether this line should generate a deferred revenue schedule (see Section on revenue recognition) |

**Payment**

| Column | Type | Notes |
|---|---|---|
| payment_id | UUID (PK) | |
| tenant_id | UUID (FK) | |
| entity_id | UUID (FK) | |
| customer_id | UUID (FK) | |
| payment_date | DATE | |
| amount | DECIMAL(18,2) | total amount received, in payment_currency |
| payment_currency | CHAR(3) | |
| payment_method | ENUM | ACH, Wire, Check, Card |
| idempotency_key | VARCHAR (UNIQUE) | client-supplied, prevents duplicate payment recording — see Section 4 (API Design) |
| status | ENUM | Recorded, Allocated, PartiallyAllocated, Reversed |

**PaymentAllocation** — the join entity that lets one payment apply to multiple invoices, or one invoice receive multiple payments

| Column | Type | Notes |
|---|---|---|
| allocation_id | UUID (PK) | |
| payment_id | UUID (FK) | |
| invoice_id | UUID (FK) | |
| allocated_amount | DECIMAL(18,2) | portion of the payment applied to this specific invoice |
| created_at | TIMESTAMPTZ | |

**CreditMemo**

| Column | Type | Notes |
|---|---|---|
| credit_memo_id | UUID (PK) | |
| invoice_id | UUID (FK) | the invoice being credited |
| amount | DECIMAL(18,2) | |
| reason | VARCHAR | |
| reason_code | ENUM | Discount, Return, Write-off, Billing Error |
| status | ENUM | Draft, Approved, Applied |
| approved_by | UUID (FK → User) | |

**GLAccount** (Chart of Accounts)

| Column | Type | Notes |
|---|---|---|
| gl_account_id | UUID (PK) | |
| entity_id | UUID (FK) | chart of accounts is per legal entity, not shared across tenant |
| account_code | VARCHAR | e.g. "1200" for AR |
| account_name | VARCHAR | e.g. "Accounts Receivable" |
| account_type | ENUM | Asset, Liability, Equity, Revenue, Expense |
| normal_balance | ENUM | Debit, Credit — determines which side increases the account |

**JournalEntry**

| Column | Type | Notes |
|---|---|---|
| journal_entry_id | UUID (PK) | |
| entity_id | UUID (FK) | |
| source_type | ENUM | InvoiceApproval, PaymentReceipt, CreditMemo, ManualAdjustment |
| source_id | UUID | polymorphic reference back to the invoice/payment/credit memo that generated this entry |
| entry_date | DATE | the accounting date this entry posts to (may differ from creation date for backdated entries within an open period) |
| posting_period | VARCHAR | e.g. "2026-06" — used for period close enforcement |
| created_by | UUID (FK → User) | |
| created_at | TIMESTAMPTZ | |

**JournalEntryLine**

| Column | Type | Notes |
|---|---|---|
| line_id | UUID (PK) | |
| journal_entry_id | UUID (FK) | |
| gl_account_id | UUID (FK) | |
| debit_amount | DECIMAL(18,2) | nullable — exactly one of debit/credit is non-zero per line |
| credit_amount | DECIMAL(18,2) | nullable |

**Constraint**: for any given `journal_entry_id`, `SUM(debit_amount) = SUM(credit_amount)` across all its lines. This is enforced at the application layer on write, and verified by a nightly reconciliation job (see Section 3).

---

## 2. Multi-Tenant and Multi-Entity Isolation Strategy

**Tenant isolation: row-level, with tenant_id as a mandatory column on every table** — not schema-per-tenant or database-per-tenant. At this scale (mid-market ERP, likely thousands of tenants), row-level isolation with proper indexing and application-layer enforcement is the right tradeoff between operational simplicity and isolation strength. Every query is required to filter by `tenant_id`, enforced via:

- A `tenant_id` column on every table that can be queried directly (denormalized onto Customer, Invoice, Payment even though it's derivable through Entity, specifically to avoid join-based isolation bugs)
- A request-scoped tenant context (set from the JWT/API key on every incoming request) that the data access layer automatically injects into every query — no query path can bypass it
- Composite indexes always lead with `tenant_id` (e.g. `(tenant_id, customer_id)`, `(tenant_id, invoice_date)`) so isolation doesn't cost a separate index scan

**Multi-entity within a tenant: Entity table with self-referencing parent_entity_id.** A tenant (the company on our platform) can have multiple Entities (legal subsidiaries). This models the real-world structure: "Acme Corp" (tenant) has "Acme US Inc" and "Acme Europe GmbH" (entities), each with their own functional currency, chart of accounts, and books — but they share tenant-level administration, billing, and user access.

Critically, **GL accounts and journal entries are scoped to Entity, not Tenant** — because each legal entity must have its own balanced books for statutory and tax reporting. A Customer, however, is scoped to a specific Entity too (a customer belongs to whichever subsidiary is invoicing them), which naturally supports **intercompany invoicing**: Subsidiary A can have a Customer record representing Subsidiary B, and invoice it through the normal flow — the data model doesn't need a special case for this.

---

## 3. Currency Handling

Three currency concepts, kept distinct deliberately because conflating them is a common and costly modeling mistake:

- **tenant.base_currency** — the reporting currency for consolidated, tenant-wide views (e.g. a CFO dashboard rolling up all subsidiaries)
- **entity.functional_currency** — the currency each legal entity's books are kept in (its GL is always balanced in this currency)
- **invoice.transaction_currency** — the currency the invoice is actually issued in, which may differ from both of the above (e.g. a US entity invoicing a European customer in EUR)

**Exchange rate storage**: a separate `ExchangeRate` table (`from_currency`, `to_currency`, `rate`, `effective_date`) populated from a daily feed (mocked in the prototype). The rate used on an invoice is **locked at approval time** and stored on the invoice row itself (`exchange_rate` column) — not re-derived later — because once GL entries are posted, the journal must reflect the rate in effect at that moment for audit and reconciliation purposes. Re-deriving rates retroactively would make journal entries non-reproducible, which is unacceptable for a financial system.

*(Note: full exchange-rate-feed integration and multi-currency conversion logic is designed here but not fully implemented in the prototype — flagged explicitly given the time budget. The schema and rate-locking design are real; the live rate-fetching is stubbed.)*

---

## 4. Audit Trail Implementation

Two complementary mechanisms, serving different purposes:

**1. Field-level audit log** — every mutation to Invoice, Payment, CreditMemo, and JournalEntry writes a row to a single `audit_log` table:

| Column | Type |
|---|---|
| audit_id | UUID |
| tenant_id | UUID |
| entity_type | VARCHAR (e.g. "Invoice") |
| entity_id | UUID |
| action | ENUM (Create, Update, StatusChange, Delete-soft) |
| field_name | VARCHAR (nullable for Create) |
| old_value | TEXT |
| new_value | TEXT |
| changed_by | UUID (FK → User) |
| changed_at | TIMESTAMPTZ |

This answers "who changed what, when" for any record — required for SOX and general compliance review.

**2. Immutable journal entries** — once a JournalEntry is posted, it is never updated or deleted, only reversed by a new, opposite-signed entry referencing the original (`source_type = Reversal`, with a `reverses_journal_entry_id` pointer). This is standard accounting practice: the GL is an append-only ledger. This gives a complete, tamper-evident financial history independent of the field-level audit log.

---

## 5. Accounting Integration — How Invoices Generate GL Entries

**Invoice approval** (Draft → Approved transition):
```
Debit:  Accounts Receivable   total_amount
Credit: Revenue (per line)    line_total  (one credit line per distinct revenue_gl_account_id)
```
Generated atomically in the same transaction as the status change — an invoice can never be in `Approved` status without a corresponding balanced journal entry existing. This is enforced at the service layer, not left to eventual consistency.

**Payment receipt and allocation**:
```
Debit:  Cash / Bank           allocated_amount
Credit: Accounts Receivable   allocated_amount
```
One journal entry per payment, potentially with multiple credit lines if the payment is allocated across multiple invoices (e.g., one $5,000 payment covering three smaller invoices generates one journal entry with one debit to Cash and three credit lines to AR, one per invoice).

**Credit memo application**:
```
Debit:  Revenue (or Sales Returns & Allowances)   amount
Credit: Accounts Receivable                        amount
```

**Partial payments and overpayments**: a partial payment simply allocates less than `amount_outstanding` — the invoice remains in `PartiallyPaid` status with a reduced `amount_outstanding`. An **overpayment** (payment amount exceeds invoice outstanding balance) creates an unallocated remainder on the Payment record (`status = PartiallyAllocated`), which either waits for allocation to a future invoice or generates a customer credit balance — modeled as a negative-balance line item the customer can draw down later, not as a separate entity type, to keep the model simple.

---

## 6. Invoice State Machine

```
Draft ──approve──> Approved ──send──> Sent ──(payment received, partial)──> PartiallyPaid
                                         │                                         │
                                         └──(payment received, full)──> Paid <─────┘
                                         │
                                         └──void──> Void

Approved/Sent/PartiallyPaid ──writeOff──> WrittenOff
```

**Transition rules:**

| From | To | Trigger | Validation |
|---|---|---|---|
| Draft | Approved | `approve()` | Requires approver ≠ creator (SoD); generates GL entry atomically |
| Approved | Sent | `send()` | No GL impact; purely a delivery status |
| Sent / Approved | PartiallyPaid | payment allocated, amount_outstanding > 0 | |
| Sent / Approved / PartiallyPaid | Paid | payment allocated, amount_outstanding = 0 | |
| Draft | Void | `void()` | Only allowed in Draft — no GL entries exist yet, so nothing to reverse |
| Approved+ | WrittenOff | `writeOff()` | Generates a reversing journal entry; requires separate approval from invoice approval (SoD) |

**What's allowed in each state:**

- **Draft**: fully editable (line items, amounts, customer) — no GL impact since nothing has posted
- **Approved / Sent / PartiallyPaid**: line items and amounts are **locked**. Only status transitions and payment allocations are permitted. An amendment requires a **credit memo against the original** plus a **new invoice** — never a direct edit. This is a hard rule: once an invoice has generated a journal entry, mutating its amount would silently desynchronize the GL, which is exactly the kind of bug that fails an audit.
- **Paid**: read-only except for the (rare) reversal path, which itself goes through credit memo + write-off, not direct edit.
- **Void / WrittenOff**: fully read-only, terminal states.

---

## 7. API Design

### Key endpoints

**POST /invoices** — create a Draft invoice with line items. Validates customer exists in tenant, computes subtotal/tax/total, sets status=Draft. No GL entry yet.

**GET /invoices/{id}** — returns invoice with current `amount_outstanding`, computed payment history (joined from PaymentAllocation), and current state.

**POST /invoices/{id}/approve** — transitions Draft → Approved. Requires `approved_by` ≠ `created_by` at the application layer (segregation of duties enforcement — see Section on financial controls). Generates the GL journal entry in the same DB transaction as the status update.

**POST /payments** — records a payment and allocates it to one or more invoices.

Request:
```json
{
  "idempotency_key": "client-generated-uuid",
  "customer_id": "...",
  "amount": 1500.00,
  "payment_currency": "USD",
  "payment_date": "2026-06-28",
  "allocations": [
    { "invoice_id": "inv-101", "amount": 1000.00 },
    { "invoice_id": "inv-104", "amount": 500.00 }
  ]
}
```
If `allocations` is omitted, defaults to **oldest-invoice-first** allocation (standard AR practice) up to the payment amount.

**GET /customers/{id}/aging** — returns AR aging buckets:
```json
{
  "customer_id": "...",
  "as_of_date": "2026-06-28",
  "current": 4200.00,
  "days_1_30": 1500.00,
  "days_31_60": 800.00,
  "days_61_90": 0.00,
  "days_90_plus": 1200.00,
  "total_outstanding": 7700.00
}
```
Computed by bucketing all non-Paid, non-Void invoices for the customer by `(as_of_date - due_date)`.

**GET /journal-entries?invoice={id}** — returns all journal entries where `source_type = InvoiceApproval` (or related reversals) and `source_id = invoice_id`, with their full debit/credit lines — the direct audit trace from invoice to GL.

### Idempotency for payment operations

The **client supplies an `idempotency_key`** (UUID) with every POST /payments request. This key has a unique constraint at the database level. On receipt:

1. Check if a Payment with this `idempotency_key` already exists
2. If yes, return the existing payment's result (200, not 201) — no duplicate processing
3. If no, proceed with creation inside a transaction

This is essential specifically because payment recording is the operation most likely to be retried by a client after a network timeout — and double-recording a payment is one of the most damaging bugs a financial system can have (directly causing AR/GL imbalance and potential duplicate refunds).

### Bulk operations

**Batch invoicing**: `POST /invoices/batch` accepts an array of invoice payloads, processes each independently, and returns a per-item success/failure result (not all-or-nothing) — so one malformed invoice in a batch of 500 doesn't block the other 499.

**Bulk payment import**: `POST /payments/batch` similarly accepts an array, each entry still requires its own `idempotency_key` for safety, and unmatched/unallocatable payments are returned in the response as `status: "unallocated"` for manual review rather than silently failing.

---

## Time spent on this section: ~55 minutes
