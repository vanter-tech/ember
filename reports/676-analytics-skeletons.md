# Report 676 — ANALYTICS-SKELETONS

## 1. Identification
- Report: 676
- Task ID: ANALYTICS-SKELETONS
- Predecessor: 675 (ACCOUNTANT-SKELETONS)

## 2. Objective
Make the admin Analytics loading state mirror the real page — every card with its own frame — and with no real text (titles, labels and period buttons as grey placeholder blocks).

## 3. Modified Files
- New: `frontend/src/pages/admin/analytics/components/AnalyticsSkeletons.tsx`, `.../components/useAnalyticsSummary.ts`
- Modified: `frontend/src/pages/admin/analytics/Analytics.tsx`, `.../components/{SummaryCards,SalesChart,ProductPerformance,TableAnalytics}.tsx`, test `.../Analytics.loading.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- **Summary cards:** three cards (round icon, label and big figure as blocks) instead of three bare blocks.
- **Sales chart:** the whole card is a placeholder on first load — title, the four period buttons and the chart-sized block. Before, the title and the real buttons were visible while the chart loaded.
- **Top products / by category:** both cards (2/3 + 1/3 grid) with block titles; the by-category card used to show nothing at all while loading and now has its ranking rows.
- **Table analytics:** block title, the three headline figures and the ranking rows.
- **Page header:** title and subtitle are blocks while the summary query loads. `useAnalyticsSummary` is the one query shared by the page and `SummaryCards` (deduplicated, one request).
- **Period change:** only the *first* load replaces the card. After the first answer, switching day/week/month/year reloads just the chart and keeps the real buttons (otherwise the buttons would turn into blocks mid-click). Tracked with a `loadedOnce` state set during render.
- Loading announcements remain a screen-reader `role="status"` per block.

## 5. Why It Changed?
Same standards as the other views (frame of the real page, no real text, never look like an empty state). The period-change case is a regression guard so that rule does not break the control.

### Verification
- The new tests failed first (the four block frames and the page header). `pnpm exec vitest run src/pages/admin src/components/skeletons src/pages/loadingStates.test.tsx` 96/96; `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings).
- The period-change test is a guard: it was added with the others but passes against the old code too, so it did not fail first.

### NOT verified
- Not measured in the running app: the placeholder sizes are estimated from the code. If the card heights jump when data arrives, tell me which block and I will adjust it.
