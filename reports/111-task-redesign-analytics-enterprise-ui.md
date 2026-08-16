# Report 111 — redesign-analytics-enterprise-ui

## 1. Identification
- **Report number:** 111
- **Task ID:** redesign-analytics-enterprise-ui (ad hoc, not in backlog)
- **Predecessor Task:** feature-session-activity-log (report 110)

## 2. Objective
Restyle `/admin/analytics` (`Analytics.tsx` + its 4 sub-components) into a minimalist "enterprise" look using Shadcn UI and Recharts, with zero changes to hooks, queries, or state.

## 3. Modified Files
- `frontend/src/pages/admin/analytics/Analytics.tsx`
- `frontend/src/pages/admin/analytics/components/SummaryCards.tsx`
- `frontend/src/pages/admin/analytics/components/SalesChart.tsx`
- `frontend/src/pages/admin/analytics/components/ProductPerformance.tsx`
- `frontend/src/pages/admin/analytics/components/TableAnalytics.tsx`
- `frontend/src/components/ui/table.tsx` (new)
- `frontend/package.json` / `frontend/pnpm-lock.yaml` (added `recharts`)

## 4. What Changed?
- **Analytics.tsx:** enterprise header (`text-3xl font-bold tracking-tight`); dropped its own `p-6` (redundant — `AdminLayout` already wraps the route in `bg-zinc-50/50 p-6`, so no separate `bg-slate-50` layer was added on top of it).
- **SummaryCards.tsx:** `Card`s got `border-border/40` + `shadow-sm`; KPI values bumped to `text-3xl font-semibold tracking-tight tabular-nums`; loading/error states centered with `text-muted-foreground`/`text-destructive`. Existing `useQuery`/`analyticsService.getSummary()` call and the 3-card data array untouched.
- **SalesChart.tsx:** the plain-CSS bar chart was replaced with a Recharts `AreaChart` (`type="monotone"`, gridlines `vertical={false}` and faint, no `YAxis`, brand-red gradient fill via `<linearGradient>`, `Tooltip` showing `$revenue`). The granularity buttons became a segmented pill (`rounded-full bg-muted/60` wrapper, active = `variant="default"` in brand red, inactive = `variant="ghost"`). `useState<SalesGranularity>`, the `useQuery` call, and `GRANULARITY_OPTIONS`/`BUCKET_LABEL_FORMAT` are unchanged; only the derived `chartData` array feeding the chart is new (same `bucket.revenue`/label logic, reshaped for Recharts' `dataKey` API).
- **ProductPerformance.tsx:** Pareto product list restyled (thinner rounded progress bars, `text-muted-foreground` secondary text, more vertical gap). "Por categoría" now renders through the new `Table`/`TableBody`/`TableRow`/`TableCell` primitives instead of a manual flex list. Same `data?.products`/`data?.categories` reads.
- **TableAnalytics.tsx:** the 3 top-line stats (`activeTableCount`/`averageTurnoverRate`/`averageSessionDurationMinutes`) now sit in one row split by `divide-x` (stacked with `divide-y` on mobile) instead of a plain 3-col grid; per-table rows separated with `divide-y`. Same data fields, same `maxRevenue` calc.
- **New `components/ui/table.tsx`:** standard dependency-free Shadcn `Table` primitive (`Table`/`TableHeader`/`TableBody`/`TableRow`/`TableHead`/`TableCell`), following this repo's existing `data-slot` + `cn()` pattern from `card.tsx`/`button.tsx`.
- **`recharts` added** (`pnpm add recharts`, now `^3.10.1`) — first charting library in `frontend/package.json`.
- Fixed a `tsc -b` error in `SalesChart.tsx`'s `Tooltip formatter` (recharts v3's `value` param can be `undefined`/non-number) by coercing with `Number(value ?? 0)`.

## 5. Why It Changed?
User-requested visual redesign of the Analytics page in two passes: first a general Shadcn/"enterprise" polish (Cards, icons, muted-foreground states, KPI grid), then an explicit follow-up asking specifically for Recharts-based charts with a minimalist gradient area chart, `text-3xl font-semibold` KPI numbers, and `border-border/40` everywhere — both passes were plan-approved before editing per this repo's mandatory workflow. No backend contract, hook, or query changed; `analyticsService`'s shape and every `data?.field ?? default` null-guard from the pre-existing `strictNullChecks`-sensitive code were preserved as-is.

## Verification
- `pnpm run build` (`tsc -b && vite build`): **PASS** (one pre-existing warning: `ConsoleApp`/main chunk >500kB, unrelated to this task).
- `pnpm exec eslint` scoped to the 6 touched files: **PASS**, 0 errors/warnings.
