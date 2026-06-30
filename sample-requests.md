# Sample API Requests

Base URL: `http://localhost:8080`

All requests require `X-Tenant-Id`. Mutating invoice/payment actions also use
`X-User-Id` where a specific actor matters (e.g. approval, for segregation-of-duties
enforcement).

Seed data IDs (from `V2__seed_data.sql`):
- Tenant (Acme Corp): `11111111-1111-1111-1111-111111111111`
- Entity (Acme US Inc): `22222222-2222-2222-2222-222222222222`
- User Alice (AR Clerk): `44444444-4444-4444-4444-444444444444`
- User Bob (AR Manager): `55555555-5555-5555-5555-555555555555`
- Customer (Globex Industries): `77777777-7777-7777-7777-777777777771`
- GL Account - Revenue (Services): `66666666-6666-6666-6666-666666666663`

---

## 1. Create an invoice (Draft)

```bash
curl -X POST http://localhost:8080/invoices \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 11111111-1111-1111-1111-111111111111" \
  -d '{
    "entityId": "22222222-2222-2222-2222-222222222222",
    "customerId": "77777777-7777-7777-7777-777777777771",
    "invoiceDate": "2026-06-15",
    "transactionCurrency": "USD",
    "createdBy": "44444444-4444-4444-4444-444444444444",
    "lineItems": [
      {
        "description": "Consulting services - June",
        "quantity": 40,
        "unitPrice": 150.00,
        "revenueGlAccountId": "66666666-6666-6666-6666-666666666663"
      }
    ]
  }'
```

Response includes the generated `invoiceId` — save it for the next steps. Note
`status: "DRAFT"` and no GL entries exist yet.

---

## 2. Approve the invoice (generates GL journal entry)

Note `X-User-Id` here is Bob (the manager), NOT Alice (who created it) —
this satisfies the segregation-of-duties check. Approving with Alice's ID
will return `403 Forbidden`.

```bash
curl -X POST http://localhost:8080/invoices/{invoiceId}/approve \
  -H "X-Tenant-Id: 11111111-1111-1111-1111-111111111111" \
  -H "X-User-Id: 55555555-5555-5555-5555-555555555555"
```

Response: `status: "APPROVED"`. A journal entry now exists — Debit AR $6,000 /
Credit Revenue $6,000.

---

## 3. View the journal entries for this invoice

```bash
curl http://localhost:8080/journal-entries?invoice={invoiceId}
```

Confirms the GL entry: one debit line to the AR account, one credit line to
the Revenue account, balanced.

---

## 4. Record a partial payment

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 11111111-1111-1111-1111-111111111111" \
  -d '{
    "idempotencyKey": "test-payment-001",
    "entityId": "22222222-2222-2222-2222-222222222222",
    "customerId": "77777777-7777-7777-7777-777777777771",
    "amount": 2500.00,
    "paymentCurrency": "USD",
    "paymentDate": "2026-06-20",
    "paymentMethod": "ACH",
    "allocations": [
      { "invoiceId": "{invoiceId}", "amount": 2500.00 }
    ]
  }'
```

Re-running this exact request (same `idempotencyKey`) will return the same
result without recording a duplicate payment — try it twice to confirm.

---

## 5. Check the invoice's updated balance

```bash
curl http://localhost:8080/invoices/{invoiceId} \
  -H "X-Tenant-Id: 11111111-1111-1111-1111-111111111111"
```

`status` should now be `"PARTIALLY_PAID"`, `amountPaid: 2500.00`,
`amountOutstanding: 3500.00`.

---

## 6. Pay the remainder (without specifying allocations — tests oldest-first default)

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 11111111-1111-1111-1111-111111111111" \
  -d '{
    "idempotencyKey": "test-payment-002",
    "entityId": "22222222-2222-2222-2222-222222222222",
    "customerId": "77777777-7777-7777-7777-777777777771",
    "amount": 3500.00,
    "paymentCurrency": "USD",
    "paymentDate": "2026-06-25",
    "paymentMethod": "WIRE"
  }'
```

No `allocations` array — the service auto-allocates oldest-invoice-first.
Invoice should now show `status: "PAID"`, `amountOutstanding: 0.00`.

---

## 7. Check AR aging for the customer

```bash
curl "http://localhost:8080/customers/77777777-7777-7777-7777-777777777771/aging?asOfDate=2026-07-30" \
  -H "X-Tenant-Id: 11111111-1111-1111-1111-111111111111"
```

With the invoice fully paid, this should show all buckets at zero. Create
a second invoice and don't pay it to see it land in the correct aging bucket
based on its due date relative to `asOfDate`.

---

## 8. Negative test — segregation of duties

Try approving an invoice with the SAME user who created it:

```bash
curl -X POST http://localhost:8080/invoices/{invoiceId}/approve \
  -H "X-Tenant-Id: 11111111-1111-1111-1111-111111111111" \
  -H "X-User-Id: 44444444-4444-4444-4444-444444444444"
```

Expect `403 Forbidden` with message about segregation of duties.

---

## 9. Negative test — unbalanced journal entry safety net

This isn't directly callable via the API (the service always builds balanced
lines), but it's covered by `JournalEntryServiceTest` — see the test class
for how the balance check is verified in isolation.
