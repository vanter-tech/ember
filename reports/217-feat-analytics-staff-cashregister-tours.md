# Report 217: Tours for Analíticas, Personal, and Caja (admin + waiter)

**Predecessor:** report 216

## Objective
Second content rollout on `SectionTour`: Analíticas, Personal (Staff), and Caja — both the admin
and waiter versions of the cash register page.

## Modified Files
- `frontend/src/pages/admin/analytics/Analytics.tsx`
- `frontend/src/pages/admin/staff/Staff.tsx`
- `frontend/src/pages/admin/cashRegister/CashRegister.tsx`
- `frontend/src/pages/waiter/cashRegister/CashRegister.tsx`
- `frontend/src/locales/es/admin.ts`, `frontend/src/locales/en/admin.ts`
- `frontend/src/locales/es/waiter.ts`, `frontend/src/locales/en/waiter.ts`

## What Changed?
`admin-analytics` (4 steps): wraps `SummaryCards`/`SalesChart`/`ProductPerformance`/
`TableAnalytics` each in an `id`-carrying `<div>` rather than editing those 4 child components
directly (all always render unconditionally, no readiness gating needed). `admin-staff` (3 steps:
filters, grid, `#topnav-create-button`) gates on `ready={!isLoading && !isError}` since `StaffGrid`
only renders once the query settles — without that gate the tour could try to run before its 2nd
step's target exists. `admin-cash-register` (2 steps: sidebar, content area) wraps
`CashRegisterBar`'s container and the history/report content pane. `waiter-cash-register` is a
single-step overview (`#waiter-cashregister-tour-content` wraps the whole shift card area) rather
than multi-step, since its content is entirely conditional on shift state (no shift open vs. an
open shift's cards/tables) and no single sub-element is guaranteed present in both states.

## Why It Changed?
Continuation of the user's tutorial-rollout request, second section group.

## Verification
`cd frontend && pnpm run test:run` — 31/31 PASS.
`cd frontend && pnpm run build` — PASS.
