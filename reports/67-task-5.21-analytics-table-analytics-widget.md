# Report 67 — task-5.21: Table Analytics widget

## 1. Identification
- **Report:** 67
- **Task ID:** task-5.21
- **Predecessor Task:** refactor-analytics-types-schema (report 66)

## 2. Objective
Add a Table Analytics widget to `/admin/analytics`, consuming the existing `GET /admin/analytics/tables` endpoint (task-5.16) to surface session duration, turnover and revenue per table. This closes out the task-5.19–5.21 widget backlog.

## 3. Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/pages/admin/analytics/Analytics.tsx`
- `frontend/src/pages/admin/analytics/components/TableAnalytics.tsx` (new)

## 4. What Changed?
- `api.ts`: added `AnalyticsTablesResponse`/`TablePerformance` as `components['schemas']` aliases (following report 66's schema-derived pattern, not hand-written) and `analyticsService.getTables(from?, to?)` calling `GET /admin/analytics/tables`.
- `TableAnalytics.tsx`: new widget — a 3-stat header row (active tables, average turnover rate, average session duration, all live/window-independent per task-5.16's contract) plus a per-table ranked list (table number, revenue, turnover count, average session duration) with a plain-CSS revenue bar, matching `ProductPerformance.tsx`'s style. Every optional schema field defaults via `?? 0`/`?? []`/`?? '—'`, per the `strictNullChecks` gotcha surfaced in report 66.
- `Analytics.tsx`: renders `<TableAnalytics />` under `<ProductPerformance />`.

## 5. Why It Changed?
task-5.21 is the last queued widget in the analytics gap-analysis backlog (task-5.19–5.21): the table-performance endpoint (task-5.16) has had no UI consumer since it shipped. Following report 66, the new types went straight to `components['schemas']` aliases instead of hand-writing them, avoiding the churn that refactor had to undo for the three earlier widgets.

## Verification
- `pnpm run build` (frontend): PASSING, 0 TypeScript errors on the first attempt (optional-field defaults applied up front this time). Backend untouched, no `mvnw test` run this task. UI not visually rendered (no browser tool available this session).
