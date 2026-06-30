# Financial Controls and Compliance Analysis

## Data Integrity

**How do you ensure invoices and payments always balance (AR subledger reconciles to GL)?**

The core guarantee is structural, not procedural: every action that changes an
invoice's financial state — approval, payment allocation, credit memo
application — posts a balanced journal entry **in the same database
transaction** as the state change. `JournalEntryService.post()` is the single
choke point through which every GL-affecting operation must pass, and it
checks `sum(debits) == sum(credits)` before persisting anything, throwing
`UnbalancedJournalEntryException` if they don't match. This means the AR
subledger (the invoice/payment tables) and the GL (journal_entry /
journal_entry_line) can never silently drift apart — they either both commit
together or neither does.

For ongoing assurance beyond the write-time guarantee, I'd add a nightly
reconciliation job that independently sums `SUM(invoice.amount_outstanding)`
across all open invoices per entity and compares it against the AR account's
running GL balance (`SUM(debit) - SUM(credit)` on the AR `gl_account_id` for
that entity). Any mismatch gets flagged for manual review same-day rather
than discovered at period close — this is the kind of check that should run
automatically rather than rely on a human noticing something looks off.

**How do you prevent duplicate payments?**

Two layers. First, the client-supplied `idempotencyKey` with a database
unique constraint — a retried request after a network timeout returns the
original payment's result rather than creating a second one. Second, at a
process level (not yet built into the prototype, but the right next addition):
a daily duplicate-detection job that flags payments from the same customer,
same amount, within a short time window, for manual review — this catches
duplicates that arrive through genuinely separate requests (e.g., the same
check submitted twice by mistake on the customer's end), which idempotency
keys alone can't prevent since that's not a retry, it's a distinct,
legitimately-submitted duplicate transaction.

**What database constraints and application-level validations are critical?**

At the database level: the `journal_entry_line` CHECK constraint that forces
exactly one of debit/credit to be non-zero on every line (you cannot insert a
line that's simultaneously a debit and a credit, or neither); the unique
constraint on `payment.idempotency_key`; the unique constraint on
`(entity_id, invoice_number)` so invoice numbers can't collide within an
entity; foreign keys everywhere relevant data must exist before being
referenced.

At the application level, the more important validations are business-rule
based and can't be expressed as simple constraints: the segregation-of-duties
check (approver ≠ creator), the "no editing line items after approval" rule,
the requirement that a payment allocation can never exceed an invoice's
`amount_outstanding`.

---

## Audit and Compliance

**What audit trail requirements would SOX compliance impose?**

SOX requires that financial records be complete, accurate, and tamper-evident,
with a clear chain of who authorized what. Concretely this means: every GL
entry needs to be traceable to its source document (handled here via
`source_type` + `source_id` on `journal_entry`); every approval needs a named,
non-repudiable approver, not a generic "system" actor (handled via
`approved_by` on Invoice, and the SoD check ensuring that approver is real and
distinct from the creator); the GL itself must be append-only, since SOX
auditors specifically look for evidence that historical financial records
weren't quietly edited after the fact (handled by the reversal-only pattern —
journal entries are never UPDATEd or DELETEd, only reversed by a new offsetting
entry). The field-level `audit_log` table additionally supports the "who
changed what, when" requirement for the surrounding business objects (invoice
status, payment status), which is a SOX expectation distinct from, and
complementary to, the GL's own immutability.

**How do you handle invoice amendments after approval?**

Never as a direct edit. Once an invoice has generated a journal entry, its
line items and amounts are locked at the application layer — `InvoiceService`
has no update path for an Approved+ invoice's financial fields. An amendment
is always a **credit memo against the original invoice, plus a new invoice**
if replacement billing is needed. This preserves the audit trail: an auditor
can see the original invoice, the credit memo that adjusted it, the reason
code, and who approved the credit — rather than seeing an invoice whose amount
silently changed with no visible history. Void is only permitted in `DRAFT`
status specifically because no GL entry exists yet to reverse — voiding a
Draft is a clean delete from an accounting perspective, voiding an Approved
invoice is not.

**Segregation of duties: who can create invoices vs. approve them vs. record
payments?**

The prototype enforces one SoD rule directly: invoice creator ≠ invoice
approver, checked in `InvoiceService.approveInvoice()`. The data model
supports extending this further — a production system would add a
role/permission table and enforce, for example, that the user recording a
payment cannot also be the one who approved the invoice it's being applied
to, and that write-off approval requires a different approver than the
original invoice approval (the design doc's state machine table notes this:
write-off "requires separate approval from invoice approval"). I'd implement
the fuller version as a dedicated `AuthorizationPolicy` check, similar in
spirit to the conflicting-roles / SoD framework I built for compensation
management access control in a previous role — same underlying principle:
certain role combinations should never coexist on the same user for the same
transaction.

---

## Period Close

**How do you handle month-end and year-end close processes?**

The schema includes an `accounting_period` table per entity with `OPEN`/
`CLOSED` status (seeded with June 2026 open, May 2026 closed, as an example).
The close process itself is a workflow, not just a flag flip: verify the AR
subledger reconciles to GL for the period (the nightly job described above,
run as a final check before close), confirm no Draft invoices remain
dated within the period that should have been approved, generate the period's
financial statements, then mark the period `CLOSED`.

**What happens when someone tries to post an invoice to a closed period?**

This should be rejected at the same choke point that already enforces the
balance check — `JournalEntryService.post()` should look up the
`accounting_period` status for the entity and the entry's posting period
before persisting, and reject with a clear error if that period is `CLOSED`.
I scoped this out of the running prototype given the time budget (it's a
straightforward guard clause, not a complex design problem), but the schema
is already in place to support it and it's the first thing I'd add if
extending this prototype.

**How do you support period adjustments and prior-period corrections?**

Never by reopening a closed period for arbitrary edits — that defeats the
purpose of closing it. Instead, prior-period corrections post as a new
journal entry in the **current open period**, with `source_type =
MANUAL_ADJUSTMENT` and a note in the entry (or a linked reference) explaining
which prior period and which original entry it's correcting. This keeps the
closed period's historical record genuinely immutable while still allowing
the books to be corrected — the correction is visible and dated when it
actually happened, not silently inserted into the past.

---

## Operational Concerns

**Deployment strategy for a financial system — zero data loss during updates**

Rolling deployment with the database as the single source of truth and the
application layer kept stateless — any in-flight request that doesn't
complete before a pod is terminated should be safely retryable, which is
exactly why idempotency keys matter beyond just the payment endpoint
specifically. For schema changes, Flyway migrations are applied as a
separate, gated step before new application code is rolled out — never
auto-applied by application startup in a financial system, since a failed
migration mid-deployment is a much worse failure mode than a few minutes of
deployment delay. Database backups should be taken immediately before any
migration runs, not just on a nightly schedule.

**Backup and recovery requirements for financial data**

Point-in-time recovery (PITR) via continuous WAL archiving, not just nightly
snapshots — for a financial system, "we lost the last 18 hours of
transactions" is not an acceptable recovery point objective. Backups should
be tested via actual periodic restore drills, not just assumed to work.

**How do you handle system failures during a payment transaction?**

This is exactly what the idempotency key plus the single-transaction GL
posting are designed to handle together. If the application crashes after
writing the Payment row but before committing the journal entry, the whole
transaction rolls back — Postgres guarantees this — so there's no
partially-applied payment sitting in an inconsistent state. If the client
never received the response and retries, the idempotency key ensures the
retry is a safe no-op rather than a duplicate. The one gap worth naming
honestly: if a downstream system (e.g., a notification service) needs to know
a payment was recorded, that notification should go through an
outbox-pattern or CDC-based event rather than being fired synchronously
inside the payment transaction — otherwise a notification-service failure
could block or roll back a successful payment, which would be the wrong
failure mode entirely.
