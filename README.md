# Multi-Tenant Invoicing & Accounts Receivable Module — Prototype

A Spring Boot prototype demonstrating the data model, GL accounting integration,
and core API surface for an invoicing/AR module within a multi-tenant ERP platform.

See `01_data_model_architecture.md` for the full design document (data model,
multi-tenancy strategy, currency handling, audit trail design, state machine,
and API design rationale). This README covers running the prototype and what's
in/out of scope.

## Running locally

```bash
docker-compose up --build
```

This starts PostgreSQL, applies the Flyway migrations (schema + seed data),
and starts the app on `http://localhost:8080`.

See `sample-requests.md` for a complete walkthrough — create an invoice,
approve it (generating GL entries), record payments, check aging, and two
negative-path tests (segregation of duties, idempotency).

## What's implemented vs. scoped out

**Fully implemented:**
- All 6 required endpoints (create invoice, get invoice, approve invoice,
  record payment, customer aging, journal entries by invoice)
- Multi-tenant isolation via `X-Tenant-Id` header, enforced at the repository
  query level (every query is tenant-scoped)
- Multi-entity (subsidiary) data model, with GL accounts scoped per entity
- Real double-entry GL posting on invoice approval (Debit AR / Credit Revenue)
  and payment receipt (Debit Cash / Credit AR), with a hard balance-check
  safety net in `JournalEntryService` that throws rather than silently persists
  an unbalanced entry
- Payment idempotency via a client-supplied `idempotencyKey` with a DB-level
  unique constraint — replaying the same request returns the original result
  rather than double-recording
- Oldest-invoice-first default payment allocation, with support for explicit
  per-invoice allocation when the client specifies it
- Partial payment handling (`PARTIALLY_PAID` status, running balance) and
  overpayment handling (`unallocatedAmount` tracked on the Payment record)
- Invoice state machine (Draft → Approved → Sent/PartiallyPaid/Paid → Void/
  WrittenOff) with the rule that line items lock once a GL entry has posted
- Segregation of duties: invoice approver must differ from invoice creator,
  enforced at the service layer, returns `403` on violation
- Optimistic concurrency control on Invoice via JPA `@Version`
- Field-level audit logging (who/what/when) for invoice creation, status
  transitions, and payment recording
- AR aging report with standard current/30/60/90+ buckets
- A small unit test suite for the journal entry balance-check and reversal
  logic specifically, since that's the highest-risk correctness surface

**Designed but not fully implemented (documented in the design doc, time-boxed
out of the prototype per the assignment's own guidance to favor depth over
breadth):**
- Multi-currency conversion — the schema supports it (`exchange_rate` locked
  at approval time, `ExchangeRate` table), but live rate-fetching and actual
  cross-currency conversion logic isn't wired up; everything in the running
  prototype uses USD-to-USD for simplicity
- Intercompany invoicing — the data model supports it naturally (a subsidiary
  can be modeled as a Customer of another subsidiary), but no dedicated
  workflow/endpoint was built
- Revenue recognition schedules — the `deferred` flag exists on line items
  as a hook, but no recognition schedule generation logic was built
- Period close enforcement — `accounting_period` table exists in the schema
  with `OPEN`/`CLOSED` status, but the prototype's `approve`/payment endpoints
  don't currently check it before posting. In a non-prototype build this check
  belongs in `JournalEntryService.post()` as a guard clause
- Credit memo application — the `credit_memo` table and entity exist in the
  schema, but no service/controller layer was built for it given the time
  budget; the accounting logic for it is documented in the design doc

## Tech choices and why

- **Spring Boot 3 / Java 17** — my strongest stack, fastest path to a
  defensible prototype in the time available
- **PostgreSQL via Flyway** — schema is explicit and versioned rather than
  Hibernate auto-DDL, which matters for a financial system where every schema
  change should be reviewable
- **JPA `@Version` for optimistic locking** — chosen over a manual version
  column + WHERE clause because it's the idiomatic Spring Data approach, and
  produces the same `OptimisticLockException` behavior with less hand-written
  code
- **Synchronous, transactional GL posting** (not event-driven/async) — for a
  financial system, the invoice status change and its GL entry must commit
  atomically in the same transaction. An async/eventual-consistency approach
  here would risk a window where an invoice shows `APPROVED` with no
  corresponding journal entry, which is exactly the kind of bug an auditor
  would flag immediately

## AI tool usage disclosure

Built with Claude assistance for: initial data model and architecture
document drafting, Spring Boot/JPA boilerplate generation (entities,
repositories, DTOs), and the accounting integration logic (GL entry
generation on invoice approval and payment allocation). The debit/credit
mechanics, state machine rules, and segregation-of-duties enforcement were
reviewed and verified manually for correctness against standard accounting
practice rather than taken as-is. The unit test suite for the journal entry
balance check was written to directly verify the one piece of logic where a
silent bug would be most damaging.

## Time tracking

- Data model and architecture design document: ~55 minutes
- Working prototype (schema, entities, services, controllers): ~100 minutes
- Sample requests, README, tests: ~30 minutes
- Financial controls and compliance analysis, experience showcase: ~45 minutes
