# Report 146

## 1. Identification
- **Report Number:** 146
- **Task ID:** EMB-CLP-01
- **Predecessor Task:** bugfix-confirmDigitalPayment-voided-bill-guard

## 2. Objective
Stand up the data layer for the customer loyalty program engine (sub-project A of EMB-CLP): the
`loyalty` module's entities, migration, repositories, and a tenant-isolation regression test, per
`docs/superpowers/specs/2026-08-17-customer-loyalty-program-design.md` §3.2. No service logic,
API endpoints, or accrual wiring yet — those are EMB-CLP-02 through -05.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/loyalty/package-info.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/model/LoyaltyTier.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/model/LoyaltyAccount.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/model/LoyaltyTransaction.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/model/LoyaltyReward.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/repository/LoyaltyAccountRepository.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/repository/LoyaltyTransactionRepository.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/repository/LoyaltyRewardRepository.java` (new)
- `backend/src/main/resources/db/migration/V9__loyalty_program.sql` (new)
- `backend/src/test/java/com/vanter/ember/loyalty/repository/LoyaltyAccountRepositoryTenantIsolationTest.java` (new)
- `PROGRESS.md`

## 4. What Changed?
Added a new `loyalty` module mirroring the existing `billing`/`cashregister` module shape:

- `LoyaltyTier` — fixed `BRONCE/PLATA/ORO/PLATINO` enum, no persistence of its own.
- `LoyaltyAccount` — `@TenantId`-scoped entity, one row per `(tenant_id, user_id)` (full JPA
  `@UniqueConstraint`, same pattern as `Category`), `userId` as a plain `varchar` column (no
  `@ManyToOne` to `User`, avoiding its LAZY `restaurantId` association), `totalPoints` defaulting
  to 0.
- `LoyaltyTransaction` — append-only ledger row, real `@ManyToOne` to `LoyaltyAccount` (safe,
  since `LoyaltyAccount` carries no LAZY hazard), signed `points`, free-text `reason`, and a plain
  `billId` column linking to `Bill.id`.
- `LoyaltyReward` — admin-defined catalog entry, `@TenantId`-scoped, `requiredTier` enum column,
  `active` flag defaulting to true.
- `V9__loyalty_program.sql` — creates `loyalty_accounts` (with the `uk_loyalty_accounts_tenant_user`
  unique constraint inline, since this is a fresh table and not retrofitted onto existing rows),
  `loyalty_transactions` (FK + index on `loyalty_account_id`), and `loyalty_rewards` (index on
  `tenant_id`).
- Three `JpaRepository` interfaces with only the tenant-scoped finder methods needed so far
  (`findByTenantIdAndUserId`, `findByLoyaltyAccountIdOrderByCreatedAtDesc`, `findByTenantId`,
  `findByTenantIdAndActiveTrue`).
- `LoyaltyAccountRepositoryTenantIsolationTest` extends `AbstractTenantIsolationTest`, covering all
  three repositories: tenant stamping on save, and cross-tenant leak checks for each finder.

## 5. Why It Changed?
This is the foundation EMB-CLP-02 (settings) through EMB-CLP-08 (customer-facing surface) build on.
Following the spec's explicit conventions keeps it consistent with the rest of the codebase: tier is
deliberately never stored (computed on read in a later task, against `RestaurantSettings` thresholds,
so an admin changing a threshold never leaves stale tiers on existing accounts); `LoyaltyTransaction`
is append-only like `Refund`/`CashMovement` so accrual history is a durable audit trail rather than a
mutable counter; and `userId`/`billId` stay plain columns rather than JPA associations to avoid
re-introducing the `User.restaurantId` LAZY-association hazard already documented for
`Payment.processedBy` and `CashShift.openedBy`.

## Verification
- `cd backend && ./mvnw test -Dtest=LoyaltyAccountRepositoryTenantIsolationTest` — 5/5 passed.
- `cd backend && ./mvnw test` — 692/692 passed (687 pre-existing + 5 new), BUILD SUCCESS.
