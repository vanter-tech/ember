# Report 14

## 1. Identification
- **Report Number:** 14
- **Current Task ID:** task-2.6
- **Predecessor Task:** task-2.5

## 2. Objective
Add `@Transactional` boundaries to multi-write operations in `BillingService` and `PaymentService` to prevent partial writes / data inconsistency under concurrent billing and payment activity.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/billing/service/BillingService.java`
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`

## 4. What Changed?
Added `org.springframework.transaction.annotation.Transactional` to:
- `BillingService.calculateBill` — read-check (`findBySessionId`) gates a `Bill` insert.
- `BillingService.splitByConsumption` — `Bill` lookup gates a `BillSplit` batch write.
- `BillingService.splitEqually` — `Bill` lookup gates a `BillSplit` batch write.
- `PaymentService.registerPhysicalPayment` — writes `BillSplit` then `Payment` in sequence.
- `PaymentService.initiateDigitalPayment` — `Bill`/`BillSplit` lookup gates a `Payment` insert.
- `PaymentService.confirmDigitalPayment` — writes `BillSplit` then `Payment` in sequence.

## 5. Why It Changed?
These methods perform read-then-write or multi-write sequences against shared financial state (`Bill`, `BillSplit`, `Payment`). Without an explicit transaction boundary, a failure between steps (e.g. `BillSplit` saved as paid but `Payment` insert failing) could leave inconsistent billing state, and concurrent requests could interleave reads/writes non-atomically. This aligns with CLAUDE.md §3's "Backend Data Consistency" priority (explicit `@Transactional` boundaries, atomic race-condition prevention in billing/payments).

## Verification
`cd backend && ./mvnw test` — 283/284 passing. The 1 error is the pre-existing `E2EOrderFlowTest` `restaurant_id` NULL constraint failure, unrelated to this change and already tracked under task-2.10.
