# Report 390 — reverse loyalty points when a payment is refunded

## 1. Identification
- **Report:** 390
- **Task ID:** Q1b — loyalty points not reversed on refund
- **Predecessor:** report 389 — fix(billing): reject a digital payment intent made stale by a redistribution (Q1a, PR #88)

## 2. Objective
When a participant's payment is refunded, claw back the loyalty points that were credited to
them for that bill — proportionally to the refunded amount — instead of leaving the points on
the account forever.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/billing/event/PaymentRefunded.java` (new)
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/main/java/com/vanter/ember/loyalty/repository/LoyaltyTransactionRepository.java`
- `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyReversalListener.java` (new)
- `backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyReversalListenerTest.java` (new)
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`

## 4. What Changed?
- **`PaymentRefunded`** — new domain event `(sessionId, billId, participantName, refundAmount)`,
  published by `PaymentService.refundPayment` right after the `Refund` row is saved and the
  `SPLIT_REFUNDED` WS frame is sent. `refundAmount` is this one refund, not the running total.
- **`LoyaltyTransactionRepository.findByLoyaltyAccountIdAndBillId`** — new derived query, returns
  the `BILL_SETTLED` accrual row plus any `BILL_REFUNDED` reversal rows for one account+bill.
- **`LoyaltyReversalListener`** — `@EventListener` on `PaymentRefunded`:
  1. resolve the participant's `userId` from the session (by name); bail if unresolved.
  2. load their `LoyaltyAccount`; bail if none.
  3. find the `BILL_SETTLED` ledger row for this account+bill; bail if none (bill refunded
     before it ever settled, or settled while loyalty was off — nothing was accrued).
  4. `reversal = round(accruedPoints * refundAmount / accruedBaseAmount, HALF_UP)`, then capped
     at `accruedPoints − alreadyReversed` so repeated partial refunds never claw back more than
     was earned.
  5. `loyaltyAccountService.credit(account, -reversal, "BILL_REFUNDED", billId, -refundAmount)`
     — reuses the one balance-mutating path, which writes the paired negative ledger row so the
     account total and the ledger stay in lockstep.
- Tests: `LoyaltyReversalListenerTest` (full / proportional partial / cap on repeats / no
  accrual / unresolved participant / no account) and
  `PaymentServiceTest.refundPayment_publishesPaymentRefundedEvent`. Full backend suite **1107/1107**.

## 5. Why It Changed?
`LoyaltyAccrualListener` credits points once per participant on `PaymentCompleted` (fired when
every split on the bill is `PAID`), off `BillSplit.amount`. `refundPayment` published **no**
domain event and `LoyaltyAccountService` had only `credit(...)` — so a refund left the accrued
points untouched, exactly the "los puntos se cargaron en el pago pero se mantuvieron en la
cuenta aun después del reembolso" report from live testing.

The reversal is gated on the accrual ledger row existing rather than on the current
`loyalty.enabled` flag, so toggling the feature off between settle and refund can't strand
points. Tier is always recomputed from `totalPoints` on read, so no tier bookkeeping is needed.
`LoyaltyTransaction.points` was already documented as signed for exactly this kind of negative
entry — no schema change.

Semantic choice: a partial refund reverses a proportional slice of the accrued points for both
accrual modes (`BY_AMOUNT_SPENT` and `BY_VISIT`). For `BY_VISIT` this means a partial refund
shaves a fraction of the flat visit points rather than all-or-nothing — chosen for uniformity
and because the cap already prevents over-reversal across multiple refunds.

The synchronous listener runs inside `refundPayment`'s transaction (same as
`LoyaltyAccrualListener` runs inside the payment transaction), so a reversal failure rolls the
refund back — deliberately in-band, not best-effort.
