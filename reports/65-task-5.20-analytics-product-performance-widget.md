# Report 65 — task-5.20: Product Performance / Pareto widget

## 1. Identification
- **Report:** 65
- **Task ID:** task-5.20
- **Predecessor Task:** task-5.19 (report 64)

## 2. Objective
Add a Product Performance / Pareto widget to `/admin/analytics`, consuming the existing `GET /admin/analytics/products` endpoint (task-5.15) to surface top-selling dishes and category breakdown.

## 3. Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/pages/admin/analytics/Analytics.tsx`
- `frontend/src/pages/admin/analytics/components/ProductPerformance.tsx` (new)

## 4. What Changed?
- `api.ts`: added hand-written `ProductPerformance`, `CategoryPerformance`, `AnalyticsProductsResponse` types (same pattern as the existing analytics DTOs — still predate the `backend-types.ts` regen) and `analyticsService.getProducts(from?, to?, limit?)` calling `GET /admin/analytics/products`.
- `ProductPerformance.tsx`: new widget, two-column layout — a ranked product list (top 10 by revenue, fixed via a `TOP_PRODUCTS_LIMIT` constant) each row showing name/category, revenue, quantity, a plain-CSS bar sized to `revenueShare`, and the running `cumulativeShare` text (the Pareto reading), plus a compact category-share side list. No new charting dependency, consistent with `SalesChart.tsx`'s plain-CSS-bar approach. Loading/error/empty states mirror the other widgets.
- `Analytics.tsx`: renders `<ProductPerformance />` under `<SalesChart />`.

## 5. Why It Changed?
task-5.20 is the next queued frontend gap-analysis item: the product-performance endpoint (task-5.15) has had no UI consumer since it shipped. A fixed top-10 limit was used rather than exposing a limit control, since no UI need for tuning it was requested and it keeps the change surgical; the shared date-range picker gap noted in task-5.19 remains open for a future task.

## Verification
- `pnpm run build` (frontend): PASSING, 0 TypeScript errors. Pre-existing >500kB chunk-size warning unrelated to this change. Backend untouched, no `mvnw test` run this task. UI not visually rendered (no browser tool available this session).
