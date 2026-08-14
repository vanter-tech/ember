# 60 — task-5.15: product performance endpoint

## 1. Identification
- **Report number:** 60
- **Task ID:** task-5.15 — Backend: product performance endpoint (`GET /admin/analytics/products`)
- **Predecessor task:** task-5.14 — temporal sales endpoint (report 59)

## 2. Objective
Expose the Pareto / top-selling view behind the admin dashboard: `GET /admin/analytics/products?from&to&limit`, reporting each menu item's and each category's share of the tenant's settled sales, derived from order line items joined to `MenuItem`/`Category`.

## 3. Modified Files
**Created**
- `backend/src/main/java/com/vanter/ember/analytics/dto/ProductPerformance.java`
- `backend/src/main/java/com/vanter/ember/analytics/dto/CategoryPerformance.java`
- `backend/src/main/java/com/vanter/ember/analytics/dto/AnalyticsProductsResponse.java`
- `reports/60-task-5.15-analytics-product-performance-endpoint.md`

**Modified**
- `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`
- `backend/src/main/java/com/vanter/ember/session/repository/SessionRepository.java`
- `backend/src/main/java/com/vanter/ember/catalog/repository/MenuItemRepository.java`
- `backend/src/main/java/com/vanter/ember/analytics/service/AnalyticsService.java`
- `backend/src/main/java/com/vanter/ember/analytics/controller/AnalyticsController.java`
- `backend/src/test/java/com/vanter/ember/analytics/service/AnalyticsServiceTest.java`
- `backend/src/test/java/com/vanter/ember/analytics/controller/AnalyticsControllerTest.java`
- `backend/src/test/java/com/vanter/ember/billing/repository/BillRepositoryTenantIsolationTest.java`
- `backend/src/test/java/com/vanter/ember/catalog/repository/MenuItemRepositoryTenantIsolationTest.java`
- `backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTenantIsolationTest.java`
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`
- `PROGRESS.md`

## 4. What Changed?
**Repositories.** Three tenant-first reads, one per store the aggregation crosses:
- `BillRepository.findPaidSessionIds(tenantId, from, to)` — the `sessionId`s of the `PAID` bills inside the window, i.e. the same row set `findSalesTotals` sums, keeping the deliberate redundant `tenantId` predicate the other analytics queries carry.
- `SessionRepository.findByTenantIdAndIdIn(tenantId, ids)` — bulk Mongo fetch of those sessions and their embedded `items`.
- `MenuItemRepository.findByTenantIdAndIdInWithCategory(tenantId, ids)` — the catalogue rows for the ordered items, `join fetch`ing `category` so the roll-up happens outside the persistence context; explicit `tenantId` predicate on top of the `@TenantId` filter, since a context slip here would relabel one tenant's dishes with another's.

**DTOs.** `ProductPerformance` (itemId, name, categoryId/categoryName, quantitySold, revenue, `revenueShare`, `cumulativeShare`), `CategoryPerformance` (same minus the Pareto column), and `AnalyticsProductsResponse` (echoed window, `totalRevenue`, `totalQuantity`, `productCount`, `products`, `categories`).

**Service.** `AnalyticsService.getProducts` reuses `resolveWindow` and joins the two stores in Java: paid session ids → sessions → non-`DRAFT` line items tallied into a `Map<ProductKey, Tally>` (one line item is one unit — `OrderItem` has no quantity), then enriched from the catalogue. `ProductKey` groups by `itemId`, falling back to the sold name for items that carry none. Products and categories come back sorted by revenue (quantity, then name, break ties for a stable ranking); `revenueShare`/`cumulativeShare` are computed from unrounded money over the *full* product set, with money scaled 2dp HALF_UP. `limit` truncates only the returned product list; a non-positive `limit` and an inverted window both throw `IllegalArgumentException` (409) before any query runs.

**Controller.** `GET /products` under the existing class-level `@PreAuthorize("hasRole('ADMIN')")`, tenant from `TenantContextHolder.requireTenantId()`, `from`/`to`/`limit` all optional and defaulted in the service.

**Tests.** 9 service cases (Pareto ranking and shares, category roll-up, `limit` truncation with full-set totals, `DRAFT` exclusion, sessions read match the settled bills, no-settled-bill short circuit that never touches Mongo, deleted-menu-item fallback, null items/price tolerance, non-positive `limit`, inverted window), 7 controller cases (param passing, defaults, client-supplied `restaurantId` ignored, non-ADMIN 403, unbound tenant 409, unauthenticated 401), 5 repository tenant-isolation cases over the three new queries, and `/api/admin/analytics/products` added to `SecurityAuditTest`'s 401 matrix.

## 5. Why It Changed?
- **A sale is still a settled bill.** Line items only count when their session's bill reached `PAID` inside the window, so an open table or an abandoned cart can never surface as a top seller. That keeps the definition task-5.13 fixed and task-5.14 reused intact across every analytics read.
- **The join has to happen in Java.** Line items live on the Mongo `Session`, the catalogue in Postgres, and there is no cross-store query — so the endpoint reads the settled session ids from Postgres, the items from Mongo, and the categories back from Postgres. This is why the module keeps owning no persistence of its own.
- **Item revenue ≠ payment revenue.** `totalRevenue` here sums what each item was sold for; taxes, tips and rounding live on the bill, so this figure is deliberately not expected to match `/summary`'s payment-derived revenue, and the DTO says so.
- **Shares over the full set.** Computing percentages before truncation means a top-10 view still reports true shares and a `cumulativeShare` that reaches 100% across the whole catalogue — the point of a Pareto chart.
- **Sold history is not rewritten.** Items whose menu item has since been deleted keep the name they were sold under (with a null category) instead of vanishing from the report; renamed items report their current catalogue name, which is what an admin recognises.

## 6. Verification
`cd backend && ./mvnw test` → **BUILD SUCCESS**, `Tests run: 509, Failures: 0, Errors: 0, Skipped: 0` (488 → 509, +21). Frontend untouched this task.
