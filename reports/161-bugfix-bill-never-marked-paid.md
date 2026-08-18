# Report 161 — Task bugfix-bill-never-marked-paid

## Identification
- **Report number:** 161
- **Task ID:** bugfix-bill-never-marked-paid
- **Predecessor Task:** feature-loyalty-tier-progress-and-restaurant-name (report 160)

## Objective
Fix `/admin/analytics`'s "Productos más vendidos", "Por categoría", and "Análisis de mesas" widgets rendering empty, per user report.

## Root Cause (systematic-debugging, Phase 1)
Confirmed via direct query against the local dev Postgres (`docker exec ember-postgres-1 psql`): `bills` had 0 rows with `status = 'PAID'` (only `OPEN`/`VOIDED`), while `payments` had 4 `CONFIRMED` rows. Joining `bills`/`bill_splits` showed bills 1, 2, and 10 each had their single split already `PAID`, yet the bill itself stayed `OPEN`.

Traced in code: `PaymentService.registerPhysicalPayment` and `PaymentService.confirmDigitalPayment` both correctly compute `allPaid` and publish `PaymentCompleted` when every `BillSplit` is `PAID`, but neither ever called `bill.setStatus(BillStatus.PAID)` — a repo-wide search confirmed `BillStatus.PAID` is referenced only in read-side JPQL filters (`BillRepository`), never assigned anywhere in `main`. A `Bill` could therefore never actually reach `PAID`, no matter how many of its splits settled.

This explains the reported symptom precisely: `findPaidSessionIds`/`findPaidBillActivity`/`findSalesTotals`/`findPaidBillsByDay` all filter on `Bill.status = PAID`, so `ProductPerformance`/`TableAnalytics` (100% dependent on that filter, with a Mongo cross-store join beyond it) returned nothing. The Resumen card's `totalRevenue` looked fine only because it reads `Payment.status = CONFIRMED` instead (a different, payment-based measure) — its `paidBillCount`/`averageOrderValue` fields were silently wrong too, just not flagged by the user.

## Modified Files
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`

## What Changed?
Both methods now set `bill.setStatus(BillStatus.PAID)` and persist it (`billRepository.save(bill)`) in the same `if (allPaid)` branch that already fires `PaymentCompleted` — the `bill` object was already loaded in both methods, so no new query was needed. Extended the two existing "all splits paid" tests to assert `bill.getStatus() == BillStatus.PAID` and `billRepository.save(bill)` was called (failed against the old code, confirming the bug, before the fix); added a bill-stays-`OPEN` assertion to the existing not-all-paid test as a regression guard against the opposite mistake.

## Why It Changed?
This was a genuine, previously-unnoticed gap: nothing in the codebase ever transitioned `Bill.status` to `PAID`, so every analytics read gated on that status silently returned nothing while payment-based reads looked fine — a very easy state to miss without deliberately checking cross-store analytics against real settled-bill data.

## Verification
`cd backend && ./mvnw test` — 722/722 passing, no regressions.

## Note: existing dev data still affected
The 3 already-broken rows in the local dev DB (bills 1, 2, 10 — fully paid but stuck at `OPEN`) are **not** backfilled by this fix; the fix only prevents the bug going forward. Asked the user whether to backfill those specific rows.
