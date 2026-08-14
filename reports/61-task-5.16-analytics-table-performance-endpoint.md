# Report 61 — task-5.16: Table Analytics Endpoint

## 1. Identification
- **Report:** 61
- **Task ID:** task-5.16
- **Predecessor Task:** task-5.15 (report 60)

## 2. Objective
Add `GET /admin/analytics/tables`, exposing per-table turnover, revenue, and average session
duration so the future task-5.21 frontend view has a backend to consume.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/billing/repository/PaidBillActivity.java` (new)
- `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`
- `backend/src/main/java/com/vanter/ember/settings/repository/DiningTableRepository.java`
- `backend/src/main/java/com/vanter/ember/analytics/dto/TablePerformance.java` (new)
- `backend/src/main/java/com/vanter/ember/analytics/dto/AnalyticsTablesResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/analytics/service/AnalyticsService.java`
- `backend/src/main/java/com/vanter/ember/analytics/controller/AnalyticsController.java`
- `backend/src/test/java/com/vanter/ember/analytics/service/AnalyticsServiceTest.java`
- `backend/src/test/java/com/vanter/ember/analytics/controller/AnalyticsControllerTest.java`
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`

## 4. What Changed?
- `BillRepository.findPaidBillActivity(tenantId, from, to)` returns `PaidBillActivity(sessionId,
  total, createdAt)` for every `PAID` bill in the window — the same tenant-explicit-predicate
  pattern as `findPaidSessionIds`, extended with the fields table analytics needs.
- `DiningTableRepository.findByRestaurantIdAndIdIn` bulk-resolves table numbers, active or not.
- `AnalyticsService.getTables(restaurantId, from, to)`: resolves the shared window, reads
  `findPaidBillActivity`, then bulk-fetches the matching Mongo `Session`s
  (`sessionRepository.findByTenantIdAndIdIn`) to recover each bill's `tableId` and the session's
  open time. Bills are rolled up per table into turnover count, revenue, and summed
  session-duration samples; tables are then resolved for their `tableNumber` and the list is
  sorted by revenue descending (ties broken by turnover count). `activeTableCount` is a live
  `countByRestaurantIdAndIsActiveTrue` read, independent of the window; `averageTurnoverRate` is
  `totalTurnovers / activeTableCount` (0 when there are no active tables); response- and
  table-level `averageSessionDurationMinutes` are the mean of `bill.createdAt - session.createdAt`
  in minutes (1dp), `null` when no sample was available. A bill whose session can't be resolved,
  or whose session has no `tableId`, is skipped from the aggregation (turnover/revenue) but not
  from `totalTurnovers`'s and `activeTableCount`'s independent live counts. An inverted window
  still throws `IllegalArgumentException` via the shared `resolveWindow`.
- `AnalyticsController.getTables` — `GET /admin/analytics/tables?from&to`, inherits the class-level
  `@PreAuthorize("hasRole('ADMIN')")`, tenant always via `TenantContextHolder.requireTenantId()`.
- `SecurityAuditTest`'s 401 matrix gained the new route.
- Tests: 9 new `AnalyticsServiceTest` cases (attribution/ordering, deleted-table tolerance,
  missing-`createdAt` tolerance, unresolvable-session skip, no-paid-bills path, zero-active-tables
  divide guard, inverted-window rejection) and 6 new `AnalyticsControllerTest` cases mirroring the
  existing `summary` endpoint's coverage (tenant-from-context, defaulting, client-supplied
  `restaurantId` ignored, 403 for non-ADMIN, 409 without a bound tenant, 401 unauthenticated).

## 5. Why It Changed?
`Bill` carries no `tableId` of its own, and `Session` (Mongo) carries no closed/settled timestamp
— so, like task-5.15's product analytics, the table/duration join has to happen in the service
rather than in a single query, spanning both stores. Session duration is approximated as
`bill.createdAt - session.createdAt` to stay consistent with the "a sale is a settled bill" rule
every other analytics read in this module already anchors to, rather than inventing a new
timestamp source. `activeTableCount`/`averageTurnoverRate` are deliberately live (ignoring the
window) for the same reason `/summary`'s `activeSessions` is: "how many tables exist right now" has
no meaning inside a past date range. A table that's since been deleted still keeps the revenue it
earned while active, mirroring how product analytics keeps a deleted menu item's sold name instead
of dropping the row.

**Verification:** `./mvnw test` — 523/523 passing (was 509; +14 across
`AnalyticsServiceTest`/`AnalyticsControllerTest`/`SecurityAuditTest`).
