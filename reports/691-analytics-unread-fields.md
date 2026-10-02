# Report 691 — ANALYTICS-UNREAD-FIELDS

## 1. Identification
- **Report number:** 691
- **Task ID:** FRONTEND-FIELD-GAPS · C (analytics)
- **Predecessor task:** report 690 — SHIFT-AUDIT-WHO-AND-WHEN (branch `feat/analytics-missing-fields`, stacked on r687–r690, none merged)

## 2. Objective
The analytics endpoints returned `paidBillCount` (summary, sales, per bucket), `totalTurnovers`, `productCount`, `totalQuantity` and a whole `/range` endpoint that no screen read.

## 3. Modified Files
`frontend/src/lib/api.ts`; `frontend/src/pages/admin/analytics/Analytics.tsx`; `.../components/{AnalyticsRange (new),SummaryCards,SalesChart,TableAnalytics,ProductPerformance,AnalyticsSkeletons}.tsx`; `.../Analytics.loading.test.tsx`; `frontend/src/locales/{es,en}/admin.ts`.

## 4. What Changed?
- Summary: fourth card "Cuentas pagadas" (`paidBillCount`); grid is 2 columns on tablet, 4 on desktop.
- Sales chart: "N cuentas pagadas en el período" under the title; the tooltip shows the bucket's bill count next to its date.
- Products: "N productos · M unidades vendidas" under "Top productos".
- Tables: fourth figure "Rotaciones totales" (`totalTurnovers`).
- Page header: new `AnalyticsRange` reads `GET /admin/analytics/range` (new `analyticsService.getRange`) and shows "Datos del dd/mm/aaaa al dd/mm/aaaa · N cuentas emitidas" (or "Aún no hay cuentas emitidas."). `billCount` there counts every bill issued, whatever its status, hence "emitidas" rather than "pagadas".
- Skeletons: summary 4 cards, table figures 4, a subtitle block under the sales and top-products titles (`CardTitleSkeleton withSubtitle`), and a one-line block for the range; all without real text.

## 5. Why It Changed?
The endpoints already computed these figures; the dashboard showed revenue and averages without the bill counts and volumes behind them.

## Verification
Frontend build exit 0, lint 0 errors, vitest 406/407 (known `MenuJoin` failure). No backend change. Not opened in a browser.
