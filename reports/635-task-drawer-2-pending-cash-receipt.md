# Report 635

## 1. Identification
- **Report Number:** 635
- **Task ID:** EMB-DRAWER task-drawer-2 — pending cash receipt from physical payments
- **Predecessor Task:** report 634 (task-drawer-1, kick print job + `cashDrawer` printer flag; commit still pending at the time of writing)

## 2. Objective
When a waiter confirms a cash (PHYSICAL) payment, record a *pending cash receipt* the accountant can later accept (task-drawer-3), without changing the payment/billing flow.

## 3. Modified Files
- `backend/src/main/resources/db/migration/V20__cash_drawer_events.sql` (new)
- `backend/src/main/java/com/vanter/ember/billing/event/PhysicalPaymentRegistered.java` (new)
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/main/java/com/vanter/ember/cashregister/model/CashDrawerEvent.java`, `CashDrawerEventType.java`, `CashDrawerEventStatus.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/repository/CashDrawerEventRepository.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/listener/CashDrawerEventListener.java` (new)
- `backend/src/test/java/com/vanter/ember/cashregister/listener/CashDrawerEventListenerTest.java` (new), `.../repository/CashDrawerEventRepositoryTest.java` (new)
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`
- `PROGRESS.md`

## 4. What Changed?
- **V20** creates `cash_drawer_events` (idempotent) with CHECKs on `type` (`CASH_SALE|MANUAL`) and `status` (`PENDING|RECEIVED`), indexes on `(tenant_id, status)` and `cash_shift_id`, and a partial **unique index on `payment_id`** so one payment can never produce two receipts.
- `PaymentService.registerPhysicalPayment` now publishes `PhysicalPaymentRegistered` right after the `Payment` is saved. It is per split payment on purpose: `PaymentCompleted` only fires when the whole bill is paid.
- `CashDrawerEvent` entity (`@TenantId`, `@Version`, plain-column references like `CashMovement`); repository `findForPanel(tenantId, shiftId)` returns every `PENDING` event plus all events of the open shift (newest first), or only the pending ones when there is no open shift.
- `CashDrawerEventListener` (synchronous, same transaction as the payment) creates the `PENDING` `CASH_SALE`. Only the table-number lookup (session → dining table) is best-effort: if it fails the receipt is still recorded with a null table and a warning is logged.

## 5. Why It Changed?
The accountant owns the drawer (report 473's separation of duties) and must be told which cash arrived before opening it, instead of the drawer opening silently on the waiter's action. Recording the receipt in the payment's own transaction guarantees no confirmed cash payment goes unrecorded.

## 6. Verification
- `cd backend && ./mvnw test` — **1611/1611**, 0 failures (+5: 2 listener, 3 repository incl. tenant isolation). The existing physical-payment test in `PaymentServiceTest` now also asserts the event is published. RED first: the new tests failed to compile (missing classes) before the implementation.
- **V20 verified on real Postgres 16** (scratch container, V1…V20; dev DB untouched, container removed): a pending sale inserts, a duplicate `payment_id` is rejected by the unique index, several `MANUAL` rows with null `payment_id` are allowed, an invalid `status` is rejected, and re-running V20 is a no-op.
- **Schema validation:** booted the app (`./mvnw spring-boot:run`, dev profile, `DDL_AUTO=validate`, Flyway off) against that V1…V20 schema → `Started EmberApplication`, i.e. Hibernate accepts `CashDrawerEvent` and the task-1 `PrinterConfig.cashDrawer` column. (The test suite cannot show this: it runs H2 with `create-drop`.)
- **Deploy note:** V20 must ship with this code. Flyway runs at startup, but if the code ran without the table, every cash payment's transaction would fail on the listener's insert.
- **Known gap (follow-up):** refunding a physical payment does not cancel a still-pending receipt; the accountant would see a stale pending row.
- Not done: nothing consumes the receipts yet (API is task-drawer-3).
