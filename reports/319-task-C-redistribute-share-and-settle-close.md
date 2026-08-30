# Report 319 — Task C: redistribute an abandoning diner's share + waiter "settle & close"

## 1. Identification
- **Report number:** 319
- **Current Task:** Task C (payment-flow bug cluster) — when a diner abandons a table
  mid-payment, spread their unpaid share across the participants still present and
  announce it; give the waiter a way to close a partially-paid session (RC2).
- **Predecessor Task:** report 318 (Task B — block removing items sent to the kitchen)
- **Branch:** `feat/hpd-14-monitoring`

## 2. Objective
RC2 of report 317: there was no settle/close path for a partially-paid session, so a
waiter whose remaining diners had left could not close the table without deleting
delivered items. Task C adds two waiter actions: **redistribute** a departing diner's
still-unpaid split across the participants still present (bill total unchanged), and
**settle & close** the session. Per the user's decisions: redistribution is a real
waiter endpoint + button now (reused by Task D's `leave` flow); settle & close is
blocked with 409 while any split is still unpaid.

## 3. Modified Files
### Backend
- `billing/dto/SplitsRedistributedMessage.java` — **new** STOMP payload
- `billing/dto/RedistributeSplitRequest.java` — **new** request body
- `billing/service/PaymentService.java` — `redistributeSplit`, `settleAndClose`
- `billing/controller/BillingController.java` — `POST /billing/bills/{id}/splits/redistribute`, `POST /billing/bills/{id}/settle`
- `billing/service/PaymentServiceTest.java` — 10 new tests
- `billing/controller/BillingControllerTest.java` — 4 new tests
### Frontend
- `lib/api.ts` — `billingService.redistributeSplit`, `billingService.settleAndClose`
- `store/websocket.ts` — `SPLITS_REDISTRIBUTED` handler (customer + waiter subs) + `lastBillRedistribution` / `clearBillRedistribution`
- `store/sessionStore.tsx` — `replaceSplits` action
- `layouts/CustomerLayout.tsx` — announcement toast to diners still at the table
- `pages/waiter/TableInformation.tsx` — per-split "remove diner" control, "settle & close" button, announcement toast
- `locales/{es,en}/waiter.ts` — 7 keys
- `locales/{es,en}/customer.ts` — `billSplitRedistributedToast`

## 4. What Changed?
- **`PaymentService.redistributeSplit(billId, departingParticipantName)`**
  (`@Transactional`): bill must be `OPEN`; the departing split must be `UNPAID` (a
  partially-/fully-paid split has money attached → 409, refund first). Recipients =
  every other split not `PAID`; ≥1 required else 409. The departing split's `amount`
  is spread equally (`divide(n, 2, FLOOR)`, remainder onto the last recipient —
  mirrors `BillingService.splitEqually`) and **added** to each recipient's existing
  amount; the departing split is deleted. Bill `total` is untouched. Broadcasts
  `SplitsRedistributedMessage(type, billId, departedParticipantName, splits)` with
  the full post-redistribution split list on `/topic/session/{sessionId}`.
- **`PaymentService.settleAndClose(billId)`** (`@Transactional`): bill must be `OPEN`
  and have splits; if any split is not `PAID` → `IllegalStateException` (→ 409 via
  `GlobalExceptionHandler`). Otherwise bill → `PAID` and a `PaymentCompleted` event
  is published, reusing the existing `PaymentCompletedListener` that closes the
  session and broadcasts `SESSION_CLOSED`.
- **`BillingController`**: both endpoints `@PreAuthorize("hasRole('WAITER')")`.
- **`websocket.ts`**: on `SPLITS_REDISTRIBUTED` the customer sub calls
  `replaceSplits(eventData.splits)` and the waiter sub replaces `['bill', id]`
  `splits`; both set `lastBillRedistribution` (mirrors the existing
  `lastLowStockAlert` pattern).
- **`CustomerLayout.tsx` / `TableInformation.tsx`**: a `useEffect` on
  `lastBillRedistribution` fires a toast — "{name} left the table. Their share was
  split among those present." — then clears it. `CustomerLayout` is the always-
  mounted customer shell, so every diner still in the session sees it.
- **`TableInformation.tsx`**: each `UNPAID` split row gets a ghost `UserMinus` button
  (`redistributeSplitMutation`); the bill footer gets a "Settle and close table"
  button (`settleAndCloseMutation`, generic error toast covers the 409).

## 5. Why It Changed?
A diner leaving mid-payment previously left their share stranded: nobody could pay it
(the split was bound to a name no longer present) and the waiter could not close the
table because `settleAndClose` did not exist and the only close paths were "all
splits paid" (auto) or "void the whole bill" (loses confirmed payments) or "delete
the items" (Task B now forbids this for delivered food). Redistribution moves the
orphaned amount onto the people actually at the table so they can settle it, and the
announcement makes the change visible rather than silent. `settleAndClose` gives the
waiter the explicit close action RC2 said was missing, while the 409 guard keeps it
honest — the table only closes once every remaining share is actually paid.

## Verification
- `cd backend && ./mvnw test` — **exit 0**, aggregated surefire: **925 tests, 0
  failures, 0 errors, 0 skipped** (911 after Task B + 14 new: 10 `PaymentServiceTest`,
  4 `BillingControllerTest`).
- `cd frontend && pnpm run build` — green (`tsc -b` + `vite build`, ~3.5s).
- `cd frontend && pnpm run lint` — **0 errors**, 17 warnings, all pre-existing (the
  `TableInformation.tsx:79` `exhaustive-deps` warning is on the untouched CLOSED-status
  effect; the new redistribution effect includes `t`).
- `cd frontend && pnpm run test:run` — **41/41 pass, 12 files** (first attempt hit a
  vitest worker-startup timeout while the backend suite ran in parallel — an
  environment flake, not a test failure; clean on a serial re-run).
- Not exercised in a live browser this session (no `claude-in-chrome`).
