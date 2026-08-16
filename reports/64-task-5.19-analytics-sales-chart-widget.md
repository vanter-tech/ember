# Report 64 — task-5.19: Temporal Sales Chart widget

## 1. Identification
- **Report:** 64
- **Task ID:** task-5.19
- **Predecessor Task:** task-5.18 (report 63)

## 2. Objective
Add a Daily/Temporal Sales chart widget to `/admin/analytics`, consuming the existing `GET /admin/analytics/sales` endpoint (task-5.14), with a day/week/month/year granularity toggle.

## 3. Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/pages/admin/analytics/Analytics.tsx`
- `frontend/src/pages/admin/analytics/components/SalesChart.tsx` (new)

## 4. What Changed?
- `api.ts`: added hand-written `SalesGranularity`, `SalesBucket`, `AnalyticsSalesResponse` types (analytics DTOs still predate the `backend-types.ts` regen, same pattern as `AnalyticsSummaryResponse`) and `analyticsService.getSales(granularity?, from?, to?)` calling `GET /admin/analytics/sales`.
- `SalesChart.tsx`: new widget with a granularity toggle (Día/Semana/Mes/Año, backed by `Button` variant swap, no new UI dependency), a `useQuery` keyed on `['analyticsSales', granularity]`, and a plain CSS bar chart (flex row of proportionally-heighted divs) rendering `data.buckets` — no charting library was added since none exists in `package.json` and this keeps the bundle unchanged. Loading/error/empty states mirror `SummaryCards.tsx`.
- `Analytics.tsx`: renders `<SalesChart />` under `<SummaryCards />`.

## 5. Why It Changed?
task-5.19 is the next queued frontend gap-analysis item: the temporal sales endpoint (task-5.14) has had no UI consumer since it shipped. No custom date-range picker was built yet — `/admin/analytics` has no date-range UI anywhere, so scoping this to the granularity toggle keeps the change surgical; a shared range picker (also needed by task-5.20/5.21) is left for a future task rather than introduced ad hoc here.

## Verification
- `pnpm run build` (frontend): PASSING, 0 TypeScript errors. Pre-existing >500kB chunk-size warning unrelated to this change. Backend untouched, no `mvnw test` run this task. UI not visually rendered (no browser tool available this session).
