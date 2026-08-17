# Report 117 — Task EMB-CR-01: Cash Shift Data Layer

**Predecessor Task:** waiter-dashboard-table-orders-panel (report 115)

## Objective

Add the Postgres/JPA data layer for the Cash Register & Daily Shift Management module: `CashShift`/`CashMovement` entities, their enums, their repositories, and the `Payment` entity's new staff-attribution columns (`cashShiftId`, `processedBy`), per `docs/superpowers/plans/2026-08-16-cash-register-shift-management.md` Task 1.

## Modified Files

- `backend/src/main/java/com/vanter/ember/cashregister/package-info.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/model/CashShiftStatus.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/model/CashMovementType.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/model/CashShift.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/model/CashMovement.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/repository/CashShiftRepository.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/repository/CashMovementRepository.java` (new)
- `backend/src/main/java/com/vanter/ember/billing/model/Payment.java` (modified — 2 new columns)
- `backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java` (modified — 2 new queries)
- `backend/src/main/resources/db/migration/V7__cash_shifts.sql` (new)
- `backend/src/test/java/com/vanter/ember/cashregister/repository/CashShiftRepositoryTenantIsolationTest.java` (new)

## What Changed

- `CashShift`: two-state lifecycle entity (`OPEN`/`CLOSED`), tenant-scoped via `@TenantId`, holding opening float, opened/closed-by (`users.id` as plain `String`, no JPA relationship), and financial close fields (`expectedCash`/`countedCash`/`variance`/per-method totals) that stay `null` until close.
- `CashMovement`: manual cash in/out entry, `cashShiftId` as a plain `Long` column (no `@ManyToOne`).
- `CashShiftRepository`: `findByTenantIdAndStatus`, `findByIdForUpdate`/`findOpenForUpdate` (both `@Lock(PESSIMISTIC_WRITE)`, mirroring `BillRepository.findByIdForUpdate`), `findMaxShiftNumber`, and two history/report queries.
- `CashMovementRepository`: `findByCashShiftIdOrderByCreatedAtAsc`, `sumCashIn`, `sumCashOut`.
- `Payment` gains `cashShiftId`/`processedBy` (plain columns, closing the codebase's prior gap of no staff-attribution field on any money record).
- `PaymentRepository` gains `sumConfirmedPhysicalForShift` and `sumConfirmedDigitalInWindow`, both feeding the shift-close expected-cash calculation in Task 2.
- `V7__cash_shifts.sql`: creates `cash_shifts` (with the partial unique index `uk_cash_shifts_tenant_open` enforcing the single-shared-till rule) and `cash_movements`, and extends `payments` with the two new columns.
- New repository tenant-isolation test (`CashShiftRepositoryTenantIsolationTest`, 6 tests) following the `AbstractTenantIsolationTest`/`PaymentRepositoryTenantIsolationTest` pattern.

## Why It Changed

Establishes the persistence foundation the rest of the module (service, controller, payment-integration tasks) builds on. Deliberately avoids `@ManyToOne` associations to `User` anywhere in this module — `User#restaurantId` is a `LAZY` association and `open-in-view` is `false`, so embedding a `User` risks `LazyInitializationException` on serialization for zero benefit, since nothing here needs to navigate to the full `User` row in Java (only resolve a display name via an explicit lookup in the service layer, added in Task 2).

## Verification

- **Pre-migration check** (per the plan's explicit instruction): confirmed against the running `ember-postgres-1` container — `users.id` is `character varying(255)`, matching the migration's `varchar(255)` FK-shaped columns exactly. No changes to the migration were needed.
- `cd backend && ./mvnw test -Dtest=CashShiftRepositoryTenantIsolationTest` — RED (compile failure, types didn't exist) → created entities/repositories/migration → GREEN: `Tests run: 6, Failures: 0, Errors: 0`.
- Flyway is disabled in the H2 test profile (`spring.flyway.enabled=false`), so this run exercises the entity mappings via `ddl-auto=create-drop`, not the migration SQL itself — the migration's correctness against real Postgres is established by the manual column-type check above, not by this test run.
