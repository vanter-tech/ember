# 59 — task-5.14: temporal sales endpoint

## 1. Identification
- **Report number:** 59
- **Task ID:** task-5.14 — Backend: temporal sales endpoint (`GET /admin/analytics/sales`)
- **Predecessor task:** task-5.13 — analytics summary-cards endpoint (report 58)

## 2. Objective
Expose the revenue/orders series behind the admin dashboard's sales chart: `GET /admin/analytics/sales?granularity=day|week|month|year&from&to`, bucketed over time and reusing the metric semantics fixed by task-5.13.

## 3. Modified Files
**Created**
- `backend/src/main/java/com/vanter/ember/billing/repository/PaymentDailyRevenue.java`
- `backend/src/main/java/com/vanter/ember/billing/repository/BillDailyOrders.java`
- `backend/src/main/java/com/vanter/ember/analytics/dto/SalesGranularity.java`
- `backend/src/main/java/com/vanter/ember/analytics/dto/SalesBucket.java`
- `backend/src/main/java/com/vanter/ember/analytics/dto/AnalyticsSalesResponse.java`

**Modified**
- `backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java`
- `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`
- `backend/src/main/java/com/vanter/ember/analytics/service/AnalyticsService.java`
- `backend/src/main/java/com/vanter/ember/analytics/controller/AnalyticsController.java`
- `backend/src/test/java/com/vanter/ember/analytics/service/AnalyticsServiceTest.java`
- `backend/src/test/java/com/vanter/ember/analytics/controller/AnalyticsControllerTest.java`
- `backend/src/test/java/com/vanter/ember/billing/repository/PaymentRepositoryTenantIsolationTest.java`
- `backend/src/test/java/com/vanter/ember/billing/repository/BillRepositoryTenantIsolationTest.java`
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`

## 4. What Changed?
**Repositories.** `PaymentRepository.findConfirmedRevenueByDay` and `BillRepository.findPaidBillsByDay` aggregate the same rows their task-5.13 counterparts sum, but grouped by `year(createdAt), month(createdAt), day(createdAt)` and ordered oldest-first. Each returns a JPQL constructor-expression record in the owning module's repo package (`PaymentDailyRevenue`, `BillDailyOrders`), carrying the date as three components plus a `date()` accessor. Both keep the deliberate redundant `tenantId` predicate the other analytics queries use.

**DTOs.** `SalesGranularity` (DAY/WEEK/MONTH/YEAR) owns the calendar arithmetic — `from(String)` (case-insensitive, defaults to DAY, rejects anything else), `bucketStart`, `bucketEnd`, `next`. `SalesBucket` is one series point (inclusive `bucketStart`/`bucketEnd`, `revenue`, `paidBillCount`); `AnalyticsSalesResponse` wraps the granularity, the echoed window, the series totals and the buckets.

**Service.** `AnalyticsService.getSales` resolves the window through a new shared `resolveWindow` helper (extracted from `getSummary`, unchanged behaviour), rolls the per-day rows into buckets in a `Map<LocalDate, …>`, then walks the series from `seriesStart` to the bucket containing `to`, emitting a zero-filled bucket for every step. `seriesStart` is the client's `from` when supplied, otherwise the earliest bucket that saw activity. Null aggregates in a day row normalise to `0.00`/`0`; bucket revenue and the total are scaled to 2dp HALF_UP.

**Controller.** `GET /sales` under the existing class-level `@PreAuthorize("hasRole('ADMIN')")`, tenant from `TenantContextHolder.requireTenantId()`, `granularity` taken as a raw `String` and parsed in the service.

**Tests.** 11 service cases (day/week/month/year bucketing, zero-filling, explicit-`from` lead-in, empty history, default granularity, unknown granularity, inverted window, null aggregates), 6 controller cases (param passing, defaults, client-supplied `restaurantId` ignored, non-ADMIN 403, unbound tenant 409, unauthenticated 401), 4 repository tenant-isolation cases over the two new queries, and `/api/admin/analytics/sales` added to `SecurityAuditTest`'s 401 matrix.

## 5. Why It Changed?
- **Grouping by `year/month/day` rather than `date_trunc`:** `date_trunc` takes different argument forms on H2 (tests) and PostgreSQL (production), and would have forced a native query outside Hibernate's `@TenantId` filter. `year()`/`month()`/`day()` are plain JPQL and translate on both.
- **Week/month/year rolled up in Java:** week numbering is vendor-specific; computing ISO-8601 Monday-start weeks in `SalesGranularity` makes the boundaries deterministic and keeps the database to a single, finest-grain query per measure.
- **Series start defaults to first activity, not `EPOCH_FLOOR`:** the summary's epoch floor is harmless for one scalar sum, but a daily series from 1970 would emit ~20 000 empty buckets. The queried window still uses the epoch floor, so nothing is missed — only the emitted series is bounded.
- **Gap-free series:** a chart cannot infer a quiet Tuesday from a missing key; emitting explicit zeros lets task-5.19 plot the payload directly.
- **Revenue and paid-bill counts side by side:** they are the same two measures the summary cards use (confirmed payments vs. `PAID` bills), so the chart's totals reconcile with the cards for the same window instead of quietly disagreeing.
- **Unknown granularity rejected:** falling back to DAY would answer a different question than the one asked. It raises `IllegalArgumentException`, which `GlobalExceptionHandler` maps to 409 — the same status task-5.13 already returns for an inverted window, so the module's bad-input behaviour stays uniform.

## 6. Verification
`./mvnw test` → **488/488 passing, BUILD SUCCESS** (466 before this task; +22 new).
