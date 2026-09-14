# Report 477

## 1. Identification
- **Report Number:** 477
- **Task ID:** PLAN-GATING-PHASE1 Task 4 — gate: analytics period filters (STARTER+)
- **Predecessor Task:** report 476 (cash register open gate)

## 2. Objective
Gate non-day sales granularity (week/month/year) behind STARTER+, per the approved plan (`docs/superpowers/plans/2026-09-14-plan-gating-phase1.md`, Task 4). FREE stays on the default `DAY` granularity, which is never gated.

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/analytics/controller/AnalyticsController.java`
- Modify: `backend/src/test/java/com/vanter/ember/analytics/controller/AnalyticsControllerTest.java`
- Modify: `frontend/src/pages/admin/analytics/components/SalesChart.tsx`

## 4. What Changed?
`AnalyticsController.getSales` resolves the tenant id once, and — only when `SalesGranularity.from(granularity)` resolves to something other than `DAY` — calls `planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.STARTER, "periodfilters")` before delegating to `analyticsService.getSales`. `DAY` (the default when the param is omitted) is never gated, so a FREE tenant's dashboard keeps working exactly as before.

`SalesChart.tsx` (a `useQuery`, not a mutation — the only gate in this phase without an `onError`) now also destructures `error` and derives `planGate = isError ? extractPlanGateError(error) : null`; the existing error block renders the shared "requires plan X" copy instead of the generic `loadingSalesError` text when the block is a plan gate.

## 5. Why It Changed?
Fourth task of PLAN-GATING-PHASE1.

## 6. Verification
- TDD: `sales_blockedWhenNonDayGranularityAndPlanBelowStarter` written first, confirmed RED (200 instead of 402 — the only failure among 31 tests), GREEN after wiring; `sales_defaultDayGranularityIsNeverGated` added alongside to lock in that DAY is never gated.
- `./mvnw test` (full backend suite) — **1288/1288** (+2 new).
- `pnpm run build` — clean. `pnpm run lint` — 0 errors. `pnpm vitest run` — **139/139** (no existing test asserts on `SalesChart.tsx`'s error copy).
- No live browser check.
