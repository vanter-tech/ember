# Report 389 — reject a digital payment intent made stale by a redistribution

## 1. Identification
- **Report:** 389
- **Task ID:** Q1a — refund under-pays after a leaver's share was redistributed
- **Predecessor:** report 388 — fix(billing): make the billing-request handler atomic (PR #86)

## 2. Objective
Stop a `DIGITAL` payment intent that was created *before* a split was redistributed from
being confirmed *after* — which locked the `Payment` at the pre-redistribution amount and
capped every later refund at that stale figure, so the diner could never get back the share
they absorbed from the departed diner.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`

## 4. What Changed?
- **`confirmDigitalPayment`** — after loading the split, reject with `IllegalStateException`
  (→ HTTP 409) when `payment.getAmount() != split.getAmount()`. The split is not touched, no
  `Payment` is saved, no `SPLIT_PAID` is broadcast.
- **`redistributeSplit`** — after the split amounts are rewritten and the departing split
  deleted, delete every `DIGITAL` + `PENDING` `Payment` on the bill. Those intents were all
  created against split amounts that just changed; leaving them would (a) let a diner confirm
  at the wrong amount on any other race and (b) block `initiateDigitalPayment` (its
  `alreadyPending` guard) so the diner could never re-pay at the new amount.
- Tests: `confirmDigitalPayment_rejectsWhenSplitAmountChangedSinceIntent`,
  `redistributeSplit_deletesStalePendingDigitalIntentsOnTheBill`. Full backend suite
  **1102/1102**.

## 5. Why It Changed?
`initiateDigitalPayment` records a `PENDING` `Payment` for `split.getAmount()` as it stands
at "pay my share" time and does **not** change the split status. `redistributeSplit` (fired
by `ParticipantLeftListener` or the waiter's "repartir" button) raises the remaining diners'
`BillSplit.amount` to absorb the departed diner's share. `confirmDigitalPayment` then set the
split `PAID` and the `Payment` `CONFIRMED` **without re-checking the amount**.

Result when a diner tapped "pay my share" as the bill arrived, a table-mate then left, the
waiter redistributed, and the first diner's intent was confirmed afterwards:
`Payment.amount` stayed at the original items-only figure while `BillSplit.amount` was the
inflated one. `refundPayment` caps a refund at `payment.getAmount() - priorRefunds`, and the
refund modal seeds from `PaymentResponse.remaining` (= the same `payment.getAmount()`), so the
absorbed share was permanently unrefundable — exactly the "el modal solo mostraba el monto de
la persona al mostrar la comanda" symptom from live testing.

`registerPhysicalPayment` already re-checks the amount at call time, so the physical path only
needed the stale-intent cleanup, not the guard.

Out of scope (tracked as Q1b): loyalty points accrued on the inflated split at `BILL_SETTLED`
are still not reversed on refund — separate fix (`PaymentRefunded` event + reversal listener).
The customer "Esperando confirmación" button state does not clear live on
`SPLITS_REDISTRIBUTED`; it self-heals on reload — minor frontend follow-up.
