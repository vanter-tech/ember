# Report 145 — Task EMB-RV-final-review-fix-wave: Final Whole-Branch Review Fixes

## Identification
- **Report:** 145
- **Task ID:** EMB-RV-final-review-fix-wave (post-backlog fix wave, not a numbered EMB-RV-0x task)
- **Predecessor Task:** EMB-RV-08 / bugfix-refund-modal-review-findings (report 144)

## Objective
Fix the 2 Critical + 4 Important findings from the final whole-branch review of the Refunds &
Voids feature (EMB-RV backlog): the admin refund button 403ing for everyone (C1), voiding a bill
never actually freeing its session to re-bill (C2), a stale JPA `@UniqueConstraint` that would
silently undo the V8 migration (I3), `SplitPaidMessage`'s unmigrated `paid:boolean` field (I4), a
voided bill still able to receive a payment (I5), and `RefundPaymentModal` picking the wrong
payment when a participant has more than one on the same bill (I6). Full details for each finding
in `.superpowers/sdd/2026-08-17-refunds-and-voids/final-review-fix-report.md`.

## Modified Files
- `backend/src/main/java/com/vanter/ember/billing/dto/SplitPaidMessage.java`
- `backend/src/main/java/com/vanter/ember/billing/model/Bill.java`
- `backend/src/main/java/com/vanter/ember/billing/service/BillingService.java`
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`
- `backend/src/test/java/com/vanter/ember/billing/service/BillingServiceTest.java`
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`
- `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx`
- `frontend/src/pages/waiter/cashRegister/CashRegister.tsx`
- `frontend/src/pages/waiter/components/RefundPaymentModal.tsx`
- `frontend/src/store/websocket.ts`

## What Changed?
- **C2:** `BillingService.calculateBill`'s already-billed guard now calls
  `billRepository.findBySessionIdAndStatusNot(sessionId, BillStatus.VOIDED)` instead of
  `findBySessionId`, so a VOIDED bill no longer blocks a fresh bill on the same session. Updated
  all six `calculateBill` tests' stubs to match (Mockito strict stubbing), and added
  `calculateBill_succeedsAfterThePreviousBillWasVoided`.
- **I3:** `Bill.java`'s `@Table` dropped its `uniqueConstraints` attribute (and the now-unused
  `UniqueConstraint` import) — left as a bare `@Table(name = "bills")` with a comment pointing at
  `V8__refunds_and_voids.sql`'s partial unique index, matching `CashShift.java`'s existing pattern.
- **I4:** `SplitPaidMessage` now carries `String status` instead of `boolean paid`. Both
  `PaymentService` call sites pass `split.getStatus().name()`. `websocket.ts`'s two `SPLIT_PAID`
  handlers dropped their `?? 'PAID'` fallback. No `backend-types.ts` regen needed — confirmed
  `SplitPaidMessage` never appears in the generated schema (WS-only DTO).
- **I5:** Both `registerPhysicalPayment` and `initiateDigitalPayment` in `PaymentService` now throw
  `IllegalStateException` if the loaded `Bill.status == VOIDED`, right after the bill lookup. Added
  two new tests.
- **C1:** Removed the "Reembolsar" button/modal from `ShiftHistoryTable.tsx` (admin, read-only
  status label instead). Added a "Pagos" card with a working "Reembolsar" button to the waiter's
  own `CashRegister.tsx`, reusing the already-fetched `cashShiftDetail` query and the shared
  `RefundPaymentModal`. Added `refundPayment_forbiddenForAdmin` to `BillingControllerTest`.
- **I6:** `RefundPaymentModal` now resolves the target payment by exact `paymentId` match when
  `modalPayload.paymentId` is present, falling back to name match otherwise; the refund button's
  `disabled` also checks `remaining > 0`. New waiter-page call site passes `paymentId: payment.id`;
  `TableInformation.tsx` (name-only) left untouched.

## Why It Changed?
All six findings trace back to gaps in the original implementation plan text, not implementer
mistakes — see the detailed root-cause analysis per finding in the `.superpowers/sdd/...` report.
In short: C1 and I6 stem from the plan only ever describing the admin-side payments UI, never the
waiter-side one the design spec actually called for; C2/I3 are two halves of the same "void frees
the session" feature where the Java-level guard and the DB-level constraint were never updated
together; I4 is an incomplete DTO migration; I5 is a missing guard the plan's void feature should
have added alongside the existing `existsByBillIdAndStatus` check.

## Build Verification
- `cd backend && ./mvnw test` — **PASS**, 686 tests, 0 failures, 0 errors (was 682 before this
  wave).
- `cd frontend && pnpm run build` — **PASS**, zero `tsc` errors, `vite build` succeeded (pre-existing
  chunk-size warning only, unrelated).

## Manual Verification
No browser-automation tool was available this session (same constraint as every prior EMB-RV task).
Read back the new/changed frontend code paths by hand instead — full detail in the `.superpowers/sdd/...`
report's "Manual Sanity Check" section. Did not visually confirm the new waiter "Pagos" card or the
removed admin button in a running browser.

## Commit
See git log for the commit(s) covering this report.
