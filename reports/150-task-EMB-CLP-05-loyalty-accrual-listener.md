# Report 150

**Task ID:** EMB-CLP-05
**Predecessor Task:** EMB-CLP-04 (report 149)

## Objective

Add `LoyaltyAccrualListener` on the existing `PaymentCompleted` event so a settled bill credits loyalty points to each paying participant, off their own `BillSplit.amount` — never once per table.

## Modified Files

- `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListener.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java`
- `backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListenerTest.java` (new)

## What Changed?

- `LoyaltyAccrualListener` (`loyalty.listener`, `@EventListener` on `PaymentCompleted`): resolves the tenant via `TenantContextHolder.requireTenantId()`, loads `SettingsPayload.LoyaltySettings` via `SettingService.getSettings`, and no-ops if `enabled` is false. Otherwise it loads the bill's `BillSplit`s (`BillSplitRepository.findByBillId`) and the session's participants (`SessionService.findById`), resolves each split's `participantName` against `Participant.name` to get the `userId`, computes points via the existing `LoyaltyService.computeAccrualPoints`, and credits.
- `LoyaltyAccountService` gained `credit(LoyaltyAccount, points, reason, billId)` (`@Transactional`, new `LoyaltyTransactionRepository` dependency): bumps `totalPoints`, saves the account, and appends one `LoyaltyTransaction` ledger row in the same call — the two writes are kept together so they can't drift out of sync.
- `LoyaltyAccrualListenerTest`: disabled-loyalty no-op (verifies zero downstream interactions), multi-participant crediting off each participant's own split with no double-counting, and an unresolved-participant split is silently skipped (no crash, no credit call).

## Why It Changed?

Implements EMB-CLP-05 per `docs/superpowers/specs/2026-08-17-customer-loyalty-program-design.md` §3.3. `PaymentCompleted` already fires exactly once, only after every `BillSplit` on the bill is `PAID` (confirmed by reading both firing sites in `PaymentService`), so no split-status filtering was needed in the listener — by the time the event fires, every split is settled. `findOrCreate` (from EMB-CLP-04) is reused as the spec's documented safety net rather than assuming the join-time account always exists. Crediting and ledger-writing were placed together on `LoyaltyAccountService`, which already owns `LoyaltyAccount` persistence, rather than split across the listener and a repository call, so no future caller can bump points without leaving a matching audit row.

## Verification

`cd backend && ./mvnw test` — 712/712 green (709 prior + 3 new `LoyaltyAccrualListenerTest` cases).
