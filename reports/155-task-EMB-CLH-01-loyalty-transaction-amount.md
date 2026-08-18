# Report 155 — Task EMB-CLH-01

## Identification
- **Report number:** 155
- **Task ID:** EMB-CLH-01
- **Predecessor Task:** bugfix-loyalty-generated-types (report 154)

## Objective
Persist the amount a customer personally paid on each loyalty ledger row, so a later read endpoint (EMB-CLH-02) can report payment history per visit on the customer Home loyalty dashboard, per `docs/superpowers/specs/2026-08-17-customer-home-loyalty-dashboard-design.md` §3.1–3.3.

## Modified Files
- `backend/src/main/resources/db/migration/V10__loyalty_transaction_amount.sql` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/model/LoyaltyTransaction.java`
- `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java`
- `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListener.java`
- `backend/src/test/java/com/vanter/ember/loyalty/service/LoyaltyAccountServiceTest.java`
- `backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListenerTest.java`

## What Changed?
- New Flyway migration `V10` adds a nullable `amount numeric(10,2)` column to `loyalty_transactions`.
- `LoyaltyTransaction` gained a matching `BigDecimal amount` field (`precision = 10, scale = 2`).
- `LoyaltyAccountService.credit` grew a 5th parameter, `BigDecimal amount`, and now stores it on the `LoyaltyTransaction` it writes. Its only production caller, `LoyaltyAccrualListener.accrue`, now passes `split.getAmount()` through.
- Updated the two existing test suites that call `credit`/assert on it (`LoyaltyAccountServiceTest`, `LoyaltyAccrualListenerTest`) to the new 5-arg signature, and added a new `credit_persistsAmountOnTheLedgerRow` test asserting the amount round-trips onto the saved `LoyaltyTransaction`.

## Why It Changed?
`LoyaltyTransaction` previously stored `points`/`billId`/`reason` but not the dollar amount the customer paid — that value only existed transiently as `BillSplit.getAmount()` inside the accrual listener and was never persisted. A bill can be split across multiple named participants, so `billId` alone can't reliably reconstruct a single participant's paid amount after the fact. Capturing it at write time (rather than attempting a fragile reconstruction later) is what EMB-CLH-02's upcoming `GET /loyalty/accounts/me/visits` endpoint needs to show "what did I pay on each visit" on the customer Home dashboard.

## Verification
`cd backend && ./mvnw test` — 718/718 passing (up from the prior session's 717/717 baseline; the console summary line under-reported as 717, but summing `tests=` across all `target/surefire-reports/*.xml` confirms 718).
