# Report 137 — Task EMB-RV-01: Refund/Void Data Layer

## Identification
- **Report:** 137
- **Task ID:** EMB-RV-01
- **Predecessor Task:** tweak-waiter-tables-caja-overlay-style (report 136)

## Objective
Lay the data-layer foundation for the Refunds & Voids feature: a new `Refund` entity
(append-only, never mutates `Payment`), rename `BillSplit.paid` (boolean) to
`BillSplit.status` (`BillSplitStatus`) so a partial refund is representable, add a terminal
`BillStatus.VOIDED` plus void-attribution columns on `Bill`, the `V8` Flyway migration, and
the new/extended repository methods Tasks 2-5 build on.

## Modified Files
- Created: `backend/src/main/java/com/vanter/ember/billing/model/BillSplitStatus.java`
- Created: `backend/src/main/java/com/vanter/ember/billing/model/Refund.java`
- Created: `backend/src/main/java/com/vanter/ember/billing/repository/RefundRepository.java`
- Created: `backend/src/main/java/com/vanter/ember/billing/repository/RefundDailyAmount.java`
- Modified: `backend/src/main/java/com/vanter/ember/billing/model/BillSplit.java`
- Modified: `backend/src/main/java/com/vanter/ember/billing/model/Bill.java`
- Modified: `backend/src/main/java/com/vanter/ember/billing/model/BillStatus.java`
- Modified: `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`
- Modified: `backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java`
- Modified: `backend/src/main/java/com/vanter/ember/billing/service/BillingService.java`
- Modified: `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- Created: `backend/src/main/resources/db/migration/V8__refunds_and_voids.sql`
- Created: `backend/src/test/java/com/vanter/ember/billing/repository/RefundRepositoryTenantIsolationTest.java`
- Modified: `backend/src/test/java/com/vanter/ember/billing/repository/BillSplitRepositoryTenantIsolationTest.java`
- Modified: `backend/src/test/java/com/vanter/ember/billing/repository/BillSplitRepositoryTest.java`
- Modified: `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`
- Modified: `backend/src/test/java/com/vanter/ember/billing/service/BillingServiceTest.java`
- Modified: `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`
- Modified: `backend/src/test/java/com/vanter/ember/billing/listener/BillingEventListenerTest.java`

## What Changed?
- New `BillSplitStatus` enum (`UNPAID`/`PARTIALLY_PAID`/`PAID`) replaces `BillSplit.paid`
  (boolean) with `BillSplit.status` (`@Enumerated(EnumType.STRING)`).
- New `BillStatus.VOIDED` terminal value; `Bill` gains `voidedBy`/`voidedAt`/`voidReason`.
- New `Refund` entity (`@TenantId`-scoped, `Refund -> Payment` `@ManyToOne`, fields
  `amount`/`reason`/`refundedBy`/`createdAt`) plus `RefundRepository`
  (`findByPaymentId`, `sumByPaymentId`, `sumRefundsInWindow`, `findRefundsByDay`) and the
  `RefundDailyAmount` projection record (mirrors `PaymentDailyRevenue`'s shape).
- `BillRepository.findBySessionIdAndStatusNot` and `PaymentRepository.findByCashShiftId` /
  `findByIdForUpdate` (pessimistic write lock) / `existsByBillIdAndStatus` added for Task 2's
  service logic.
- Mechanical rename in `BillingService` (`.paid(false)` -> `.status(BillSplitStatus.UNPAID)`)
  and `PaymentService` (`split.setPaid(true)` -> `split.setStatus(BillSplitStatus.PAID)`).
  Also updated `PaymentService`'s `allSplits.stream().allMatch(BillSplit::isPaid)` (two call
  sites, `registerPhysicalPayment`/`confirmDigitalPayment`) to
  `allMatch(s -> s.getStatus() == BillSplitStatus.PAID)` — `isPaid()` no longer exists once
  `paid` stops being a boolean field, so this follow-on fix was mechanically required for the
  brief's own rename to compile; behavior is unchanged (still "every split fully paid").
- `V8__refunds_and_voids.sql`: creates `refunds`; adds `bills.voided_by`/`voided_at`/
  `void_reason`; replaces `uk_bills_tenant_session` with a partial unique index
  `uk_bills_tenant_session_active` (`WHERE status <> 'VOIDED'`) so a voided bill frees its
  session for re-billing; migrates `bill_splits.paid` -> `bill_splits.status` with a backfill;
  **and** (deviation from the brief's literal SQL, see "Why It Changed?") drops/recreates
  `bills_status_check` to allow `VOIDED`.
- Updated 6 existing test files' `.paid(...)`/`isPaid()` usages to the new
  `.status(BillSplitStatus...)` /`getStatus()` shape, per the brief's Step 10 list.

## Why It Changed?
This is the foundation Tasks 2-5 build on: `Refund` rows are the audit trail for money given
back without ever touching `Payment.status` (global constraint: no `PaymentStatus.REFUNDED`),
and `BillSplitStatus.PARTIALLY_PAID` is what makes a partial refund representable at all (a
boolean can't).

**Deviation from the brief's literal migration text:** before applying, I checked the live dev
Postgres (`docker exec ember-postgres-1 psql ... \d bills`), per the brief's own Step 9
instruction to verify the constraint being dropped matches reality. That surfaced an
auto-generated `bills_status_check` CHECK constraint (`status IN ('OPEN','PAID')`) that Hibernate's
`ddl-auto: update` had created for the entity's original two-value enum, baselined into the
schema before Flyway took over (this repo's `V3__restaurant_columns_backfill.sql` documents the
exact same failure mode for `restaurants.status` and fixes it the same way: drop + recreate).
`ddl-auto: update` never edits an existing constraint when an enum gains a new literal, so
without an explicit fix, inserting a `VOIDED` bill against any real (non-H2-test) Postgres would
violate this constraint and 500. I added the drop/recreate pair (`bills_status_check` now allows
`'OPEN','PAID','VOIDED'`) and validated the full migration end-to-end by applying it directly to
the local dev Postgres container (`ember-postgres-1`) — it ran cleanly and `\d bills` afterward
confirmed the new three-value check constraint and the partial unique index. `bill_splits.status`
needed no equivalent fix since it's a column this same migration creates fresh (no pre-existing
constraint to conflict with).

## TDD Evidence

**RED** — `cd backend && ./mvnw test -Dtest=RefundRepositoryTenantIsolationTest`, run before
any production code existed:
```
[ERROR] .../RefundRepositoryTenantIsolationTest.java:[6,38] cannot find symbol
  symbol:   class BillSplitStatus
[ERROR] .../RefundRepositoryTenantIsolationTest.java:[11,38] cannot find symbol
  symbol:   class Refund
[ERROR] .../RefundRepositoryTenantIsolationTest.java:[25,16] cannot find symbol
  symbol:   class RefundRepository
[ERROR] 4 errors
[INFO] BUILD FAILURE
```
Expected and confirmed: the test references types that don't exist yet.

**GREEN** — `cd backend && ./mvnw test`, run after all Task 1 production/test code was in place:
```
[INFO] Tests run: 654, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
All 654 tests green, including the new `RefundRepositoryTenantIsolationTest` (6 tests) and every
renamed test in the 6 modified test files.

## Self-Review
- **Completeness:** All 12 steps of Task 1 implemented. The `isPaid()` compile break in
  `PaymentService`'s two `allMatch` call sites (not explicitly named in the brief's Step 8 text,
  which only mentioned the `setPaid`/`setStatus` swap) was caught and fixed — required for the
  brief's own rename to compile.
- **Quality:** Migration validated against a real Postgres instance, not just H2 test-mode
  create-drop, catching the `bills_status_check` gap the brief's SQL didn't cover.
- **Discipline:** No scope creep beyond Task 1's Files list; the CHECK-constraint fix is inside
  the same migration file the task already owns, following the exact precedent
  (`V3__restaurant_columns_backfill.sql`) already established in this codebase for this failure
  mode.
- **Testing:** New repository test asserts real behavior (sums, tenant isolation, day-bucketing)
  against H2, not mocks. Renamed tests preserve their original assertions' intent.
