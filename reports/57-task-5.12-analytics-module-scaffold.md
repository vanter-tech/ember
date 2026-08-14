# Report 57 — task-5.12: scaffold the backend `analytics` module

## 1. Identification
- **Report number:** 57
- **Task ID:** task-5.12
- **Predecessor task:** bugfix-customer-tenant-on-join (report 56)

## 2. Objective
Create the backend `analytics` module (`AnalyticsController` / `AnalyticsService`, `ADMIN`-gated,
tenant-scoped via `TenantContextHolder`) under `/admin/analytics`, so task-5.13–5.16 have a home for
the summary / temporal-sales / product-performance / table-analytics endpoints. No analytics backend
existed before this: `DashboardController`'s live `/dashboard/status` was the only reporting surface.

## 3. Modified files
Created:
- `backend/src/main/java/com/vanter/ember/analytics/package-info.java`
- `backend/src/main/java/com/vanter/ember/analytics/controller/AnalyticsController.java`
- `backend/src/main/java/com/vanter/ember/analytics/service/AnalyticsService.java`
- `backend/src/main/java/com/vanter/ember/analytics/dto/AnalyticsRangeResponse.java`
- `backend/src/main/java/com/vanter/ember/billing/repository/BillActivityWindow.java`
- `backend/src/test/java/com/vanter/ember/analytics/controller/AnalyticsControllerTest.java`

Modified:
- `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`
- `backend/src/test/java/com/vanter/ember/billing/repository/BillRepositoryTenantIsolationTest.java`
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`
- `PROGRESS.md`

## 4. What changed?
- **New module `com.vanter.ember.analytics`** with the conventional `controller` / `service` / `dto`
  split. `package-info.java` records that the module owns no persistence of its own — it only
  aggregates over `billing`, `session` and `catalog`.
- **`AnalyticsController`** maps `/admin/analytics` with a **class-level**
  `@PreAuthorize("hasRole('ADMIN')")`, so every endpoint task-5.13–5.16 adds inherits the ADMIN gate
  rather than each handler having to remember it. The tenant comes from
  `TenantContextHolder.requireTenantId()`, never from a request parameter.
- **Seed endpoint `GET /admin/analytics/range`** returns
  `AnalyticsRangeResponse{firstBillAt, lastBillAt, billCount}` — the window of billing activity the
  tenant actually has data for. `firstBillAt`/`lastBillAt` are null when the tenant has never been
  billed; `billCount` is then `0`.
- **`AnalyticsService.getRange(restaurantId)`** is `@Transactional(readOnly = true)` and normalises
  the projection (null aggregate row / null count → `0`).
- **`BillRepository.findActivityWindow(tenantId)`** — a JPQL constructor expression into the new
  `BillActivityWindow` record (`min(createdAt)`, `max(createdAt)`, `count`). It carries an explicit
  `where b.tenantId = :tenantId` predicate on top of Hibernate's automatic `@TenantId` filter.
- **Tests:** `AnalyticsControllerTest` (ADMIN 200, WAITER 403, anonymous 401, tenant read from
  context, client-supplied `restaurantId` ignored); two new cases in
  `BillRepositoryTenantIsolationTest` proving the aggregate does not cross tenants and returns an
  empty window for a tenant with no bills; `GET /api/admin/analytics/range` added to
  `SecurityAuditTest`'s unauthenticated-401 matrix.

## 5. Why it changed?
- **A scaffold with zero handlers is dead code.** A controller carrying only a class-level mapping
  cannot be tested and cannot fail closed if the ADMIN gate or tenant binding regresses. `/range` is
  a real endpoint that is deliberately disjoint from task-5.13–5.16's four endpoints, so it proves
  the wiring today without pre-empting or duplicating them — and it is the value task-5.19's
  day/week/month/custom-range picker needs to bound its date inputs instead of offering ranges that
  can only come back empty.
- **Class-level `@PreAuthorize` over per-method.** Analytics exposes tenant-wide revenue; the
  failure mode of forgetting the annotation on one future handler is a WAITER or CUSTOMER reading
  the whole restaurant's numbers. Defaulting the whole controller to ADMIN makes the safe case the
  one you get by doing nothing.
- **The redundant `tenantId` predicate is intentional.** `@TenantId` already filters `Bill`, so the
  `where` clause is belt-and-braces. It is kept because analytics is precisely the place where a
  silent tenant-context slip would not throw — it would return another restaurant's aggregates as
  plausible-looking numbers. Naming the tenant in the query keeps the scope auditable in the SQL and
  in the isolation test, matching the "every finder is tenant-first" rule already applied to Mongo.
- **A record projection, not an interface projection.** JPQL constructor expressions resolve at
  bootstrap and fail loudly on a mismatch, where alias-based interface projections fail at runtime.
  The record lives in `billing.repository` so the billing module keeps ownership of its own read
  shapes and `analytics` does not leak DTO types into another module's repository.

## 6. Verification
`cd backend && ./mvnw test` → **BUILD SUCCESS, Tests run: 446, Failures: 0, Errors: 0, Skipped: 0**
(438 before this task; +8 new). Frontend untouched.
