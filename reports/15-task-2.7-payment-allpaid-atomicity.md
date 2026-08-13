# Report 15 — task-2.7

**Predecessor Task:** task-2.6

## Objective
Ensure atomic execution of the `allPaid == true` check in `PaymentService` so `PaymentCompleted` reliably fires exactly once, even under concurrent split payments on the same bill.

## Modified Files
- `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`

## What Changed?
- Added `BillRepository.findByIdForUpdate(Long)`, annotated `@Lock(LockModeType.PESSIMISTIC_WRITE)`, backed by an explicit `@Query` selecting the `Bill` by id.
- `PaymentService.registerPhysicalPayment` now loads the `Bill` via `findByIdForUpdate` instead of `findById`.
- `PaymentService.confirmDigitalPayment` now additionally locks the associated `Bill` via `findByIdForUpdate` before mutating the `BillSplit` and evaluating `allPaid`.
- Updated `PaymentServiceTest` mocks to stub `findByIdForUpdate` wherever the locked path is exercised, and left `initiateDigitalPayment` mocks on `findById` since that method doesn't touch splits/allPaid and remains unlocked.

## Why It Changed?
Both payment-confirmation paths read all `BillSplit` rows for a bill and check `allMatch(BillSplit::isPaid)` after marking their own split. Under the default `READ_COMMITTED` isolation level, two concurrent payments against different splits of the same bill could each read the other's split as still-unpaid (uncommitted), so both transactions would compute `allPaid = false` and neither would ever publish `PaymentCompleted`. Acquiring a `PESSIMISTIC_WRITE` lock on the `Bill` row at the start of each transaction serializes concurrent payment operations per bill, guaranteeing the second transaction observes the first transaction's committed split state before performing its own `allPaid` check.

## Verification
`cd backend && ./mvnw test` — 284 tests run, 0 failures, 1 pre-existing error (`E2EOrderFlowTest.setUp`, `restaurant_id` NULL constraint — tracked separately under task-2.10, unrelated to this change).
