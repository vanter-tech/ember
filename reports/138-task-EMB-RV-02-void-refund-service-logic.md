# Report 138 — Task EMB-RV-02: Void/Refund Service Logic

## Identification
- **Report:** 138
- **Task ID:** EMB-RV-02
- **Predecessor Task:** EMB-RV-01 (report 137)

## Objective
Implement `BillingService.voidBill` (cancel an `OPEN` bill with no confirmed payment) and
`PaymentService.refundPayment` (full or partial reversal of a `CONFIRMED` payment), plus the
read-side helpers (`listPayments`, `listRefunds`, `toResponses`) and broadcast DTOs Task 3's
controller endpoints and Task 4's shift-detail extension consume.

## Modified Files
- Created: `backend/src/main/java/com/vanter/ember/billing/dto/PaymentResponse.java`
- Created: `backend/src/main/java/com/vanter/ember/billing/dto/RefundResponse.java`
- Created: `backend/src/main/java/com/vanter/ember/billing/dto/BillVoidedMessage.java`
- Created: `backend/src/main/java/com/vanter/ember/billing/dto/SplitRefundedMessage.java`
- Modified: `backend/src/main/java/com/vanter/ember/billing/service/BillingService.java`
- Modified: `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- Modified: `backend/src/test/java/com/vanter/ember/billing/service/BillingServiceTest.java`
- Modified: `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`

## What Changed?
- `BillingService.voidBill(billId, reason, voidedByEmail)`: locks the bill
  (`findByIdForUpdate`), requires `OPEN` status, requires no `CONFIRMED` payment
  (`PaymentRepository.existsByBillIdAndStatus`), stamps `VOIDED`/`voidedBy`/`voidedAt`/
  `voidReason`, saves, and broadcasts `BillVoidedMessage` to `/topic/session/{sessionId}`.
  `BillingService` gained `PaymentRepository`/`UserRepository`/`SimpMessagingTemplate`
  dependencies and a `resolveUserId` helper (same shape as `PaymentService`'s).
- `PaymentService.refundPayment(paymentId, amount, reason, refundedByEmail)`: locks the payment
  (`findByIdForUpdate`), requires `CONFIRMED` status, computes `remaining = payment.amount -
  sumByPaymentId(refunds so far)`, defaults a null `amount` to full `remaining`, rejects
  non-positive or over-remaining amounts. For `PHYSICAL` payments, locks the currently open
  cash shift and records a `CASH_OUT` `CashMovement` directly via `CashMovementRepository` (not
  `CashShiftService.recordMovement`, per the plan's explicit no-circular-dependency constraint)
  and publishes `CashMovementRecorded`; `DIGITAL` payments skip the shift entirely. Saves the
  `Refund` row, recomputes the participant's `BillSplit.status` via a new private
  `updateSplitStatus` helper (nets every `CONFIRMED` payment's amount minus its own refunds
  against the split's total to land on `UNPAID`/`PARTIALLY_PAID`/`PAID`), and broadcasts
  `SplitRefundedMessage`.
- `PaymentService.listPayments(billId)` / `toResponses(payments)` / `listRefunds(paymentId)`:
  read-side projections used by Task 3's `GET` endpoints and Task 4's shift detail — each
  `PaymentResponse` carries its own `refundedAmount`/`remaining`, each `RefundResponse` resolves
  `refundedBy` to a display name via `UserRepository.findAllById`.
- New DTOs: `PaymentResponse`, `RefundResponse` (records, no behavior), `BillVoidedMessage`/
  `SplitRefundedMessage` (WebSocket broadcast records with `of(...)` factories, matching the
  existing `SplitPaidMessage`/`DigitalPaymentInitiatedMessage` pattern in this file).

## Why It Changed?
Task 1 laid the data layer; this task is where the two core business rules actually live: a bill
can only be voided before money changes hands (guarded by both its own `OPEN` check and the
confirmed-payment existence check), and a refund can never exceed what's actually left to give
back (guarded by the `remaining` computation, which nets out prior partial refunds so a payment
can be refunded in more than one step). The cash-out-on-the-currently-open-shift design (rather
than reopening/rewriting the shift that recorded the original payment) is the plan's explicit
global constraint: a `CLOSED` shift's stored totals are never touched retroactively. Building the
`CashMovement` directly instead of calling `CashShiftService.recordMovement` is likewise
deliberate — `CashShiftService` will depend on `PaymentService` in Task 4 (to list a shift's
payments), so `PaymentService` must not depend back on `CashShiftService` or Spring's bean graph
would have a cycle.

## TDD Evidence

**RED (BillingService.voidBill)** — `cd backend && ./mvnw test -Dtest=BillingServiceTest`, run
after adding the 5 `voidBill_*` tests but before any `voidBill` implementation:
```
[ERROR] .../BillingServiceTest.java:[373,37] cannot find symbol
  symbol:   method voidBill(long,java.lang.String,java.lang.String)
[ERROR] .../BillingServiceTest.java:[392,52] cannot find symbol
  symbol:   class BillVoidedMessage
[INFO] 7 errors
[INFO] BUILD FAILURE
```

**GREEN (BillingService.voidBill)** — `cd backend && ./mvnw test -Dtest=BillingServiceTest`, run
after implementing `voidBill` and `BillVoidedMessage`:
```
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**RED (PaymentService.refundPayment)** — `cd backend && ./mvnw test -Dtest=PaymentServiceTest`,
run after adding the 8 `refundPayment_*` tests but before any `refundPayment` implementation:
```
[ERROR] .../PaymentServiceTest.java:[501,39] cannot find symbol
  symbol:   method refundPayment(long,<nulltype>,java.lang.String,java.lang.String)
[ERROR] 8 errors
[INFO] BUILD FAILURE
```

**GREEN (PaymentService.refundPayment)** — `cd backend && ./mvnw test -Dtest=PaymentServiceTest`,
run after implementing `refundPayment` and its DTOs:
```
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Full suite** — `cd backend && ./mvnw test`:
```
[INFO] Tests run: 667, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
(654 after Task 1 + 5 `voidBill` + 8 `refundPayment` = 667.)

## Self-Review
- **Completeness:** All 12 steps implemented as specified — `voidBill`, `refundPayment`,
  `updateSplitStatus`, `listPayments`/`toResponses`/`listRefunds`, and all 4 new DTOs.
- **Quality:** `refundPayment`'s remaining-balance math and `updateSplitStatus`'s net-paid
  computation both operate on `BigDecimal` throughout with no premature rounding, matching the
  rest of the billing module's convention.
- **Discipline:** No circular service dependency introduced — `PaymentService` reaches
  `CashMovementRepository` directly rather than through `CashShiftService`, exactly per the
  plan's explicit constraint, even though it duplicates a few lines of
  `CashShiftService.recordMovement`'s body.
- **Testing:** Both new test blocks assert on captured arguments (`ArgumentCaptor`) rather than
  just return values, and cover the negative paths (no open shift, non-confirmed payment,
  over-remaining amount, not-found) alongside the happy paths (full refund, partial refund, cash
  movement recorded, digital skips the shift).
