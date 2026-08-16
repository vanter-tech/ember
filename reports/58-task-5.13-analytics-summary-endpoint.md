# Report 58 — task-5.13: analytics summary-cards endpoint

## 1. Identification
- **Report number:** 58
- **Task ID:** task-5.13
- **Predecessor task:** task-5.12 (report 57)

## 2. Objective
Add `GET /admin/analytics/summary` to the `analytics` module scaffolded in task-5.12 — the three
dashboard summary cards (Total Revenue, Active Sessions, Average Order Value) aggregated over
`Payment`, `Bill` and `Session` for the tenant bound to the request. This is the backend task-5.18's
Summary Cards widget consumes.

## 3. Modified files
Created:
- `backend/src/main/java/com/vanter/ember/analytics/dto/AnalyticsSummaryResponse.java`
- `backend/src/main/java/com/vanter/ember/billing/repository/BillSalesTotals.java`
- `backend/src/test/java/com/vanter/ember/analytics/service/AnalyticsServiceTest.java`

Modified:
- `backend/src/main/java/com/vanter/ember/analytics/controller/AnalyticsController.java`
- `backend/src/main/java/com/vanter/ember/analytics/service/AnalyticsService.java`
- `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`
- `backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java`
- `backend/src/main/java/com/vanter/ember/session/repository/SessionRepository.java`
- `backend/src/test/java/com/vanter/ember/analytics/controller/AnalyticsControllerTest.java`
- `backend/src/test/java/com/vanter/ember/billing/repository/BillRepositoryTenantIsolationTest.java`
- `backend/src/test/java/com/vanter/ember/billing/repository/PaymentRepositoryTenantIsolationTest.java`
- `backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTenantIsolationTest.java`
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`
- `PROGRESS.md`

## 4. What changed?
- **`GET /admin/analytics/summary?from&to`** on `AnalyticsController`, inheriting the class-level
  `@PreAuthorize("hasRole('ADMIN')")` and taking its tenant from `TenantContextHolder.requireTenantId()`.
  `from`/`to` are optional inclusive ISO date-times (`@DateTimeFormat(iso = DATE_TIME)`); the
  controller does no defaulting of its own, it forwards nulls so the service owns one definition of
  the window.
- **`AnalyticsSummaryResponse{totalRevenue, activeSessions, averageOrderValue, paidBillCount, from, to}`**.
  `from`/`to` echo the window actually applied, including substituted defaults, so the frontend can
  label the cards with the range it really got.
- **`AnalyticsService.getSummary(restaurantId, from, to)`** (`@Transactional(readOnly = true)`):
  - defaults `from` to `EPOCH_FLOOR` (1970-01-01) and `to` to `now()`;
  - rejects an inverted window with `IllegalArgumentException` (409 via `GlobalExceptionHandler`);
  - `totalRevenue` = `PaymentRepository.sumConfirmedRevenue(...)`, `averageOrderValue` =
    `salesTotal / paidBillCount` from `BillRepository.findSalesTotals(...)` (2dp, `HALF_UP`),
    `activeSessions` = `SessionRepository.countByTenantIdAndStatus(tenantId, OPEN)`;
  - normalises every null aggregate (empty window, null sum, null projection row) to `0`/`0.00`.
- **`PaymentRepository.sumConfirmedRevenue(tenantId, from, to)`** — `sum(p.amount)` over
  `CONFIRMED` payments in the window, with the explicit `p.tenantId = :tenantId` predicate on top of
  the `@TenantId` filter, matching task-5.12's convention.
- **`BillRepository.findSalesTotals(tenantId, from, to)`** — JPQL constructor expression into the new
  `BillSalesTotals(billCount, salesTotal)` record over `PAID` bills, same explicit tenant predicate.
  The record lives in the owning module's repository package, not in `analytics.dto`.
- **`SessionRepository.countByTenantIdAndStatus`** — derived Mongo count; tenant-first like every
  other finder in that repository.
- **Tests (+20, 446 → 466):** six new `AnalyticsControllerTest` cases (parsed window forwarded,
  no-params forwards nulls, client `restaurantId` ignored, WAITER 403, no tenant bound 409,
  anonymous 401); a new `AnalyticsServiceTest` covering the AOV division and rounding, empty/null
  aggregates, default window, live session count, and the inverted-window rejection; aggregate
  cross-tenant cases in the `Bill`/`Payment`/`Session` isolation tests; and
  `GET /api/admin/analytics/summary` added to `SecurityAuditTest`'s unauthenticated-401 matrix.

## 5. Why it changed?
- **Revenue is confirmed payments, not bill totals.** A `PENDING` digital payment is an unfinished
  gateway round-trip; counting it would let a failed checkout inflate the revenue card. An `OPEN`
  bill is a table still eating, not a sale, so the average-order-value denominator only counts
  `PAID` bills.
- **Revenue and average order value are deliberately different measures** and can disagree: a
  partially-paid bill moves collected revenue but not the average. Both are documented on the DTO so
  the frontend does not "reconcile" them by dividing one by the other.
- **Active sessions ignore the date range on purpose.** "How full is the restaurant right now" has no
  meaning inside a past window; scoping it to the picker would make the card read `0` whenever an
  admin inspected last month.
- **An inverted window fails loudly.** Silently returning empty aggregates would render as a
  legitimate-looking "no sales" dashboard, which is exactly the class of failure task-5.12's
  redundant tenant predicates exist to prevent.
- **`EPOCH_FLOOR` instead of `LocalDateTime.MIN`.** `MIN` is year -999999999 and no SQL `timestamp`
  column can hold it, so the query would fail rather than mean "all history".
- **`countByTenantIdAndStatus` on the repository rather than counting in the service.** Mongo has no
  `@TenantId` equivalent — isolation there is only as good as the finder signature, so the count has
  to carry the tenant itself instead of filtering a fetched list.
