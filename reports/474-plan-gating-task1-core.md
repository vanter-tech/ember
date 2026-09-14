# Report 474

## 1. Identification
- **Report Number:** 474
- **Task ID:** PLAN-GATING-PHASE1 Task 1 — `PlanGateService` core
- **Predecessor Task:** report 473 (`ACCOUNTANT` role + cash-shift permissions)

## 2. Objective
First task of the plan-gating phase: build the shared infrastructure every one of the 6 upcoming gates will call — a `PlanGateService`, a `PlanLimitExceededException` mapped to HTTP 402, and the frontend helper that recognizes that error shape. No gate is wired to anything yet; this is pure, independently-tested plumbing per the implementation plan (`docs/superpowers/plans/2026-09-14-plan-gating-phase1.md`, Task 1).

## 3. Modified Files
- Create: `backend/src/main/java/com/vanter/ember/restaurant/exception/PlanLimitExceededException.java`
- Create: `backend/src/main/java/com/vanter/ember/restaurant/service/PlanGateService.java`
- Create: `backend/src/test/java/com/vanter/ember/restaurant/service/PlanGateServiceTest.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`
- Create: `frontend/src/lib/planGate.ts`
- Create: `frontend/src/lib/planGate.test.ts`
- Modify: `frontend/src/locales/es/common.ts`, `frontend/src/locales/en/common.ts`

## 4. What Changed?
`PlanGateService` exposes `currentPlan(tenantId)`, `requirePlanAtLeast(tenantId, minimum, feature)` (tier check via `RestaurantPlan.compareTo`, since the enum is declared `FREE < STARTER < PRO < ENTERPRISE`), and `requireTableCapacity(tenantId, requestedCount)` (a `{FREE:1, STARTER:10, PRO/ENTERPRISE:∞}` map). Both throw `PlanLimitExceededException`, which carries `feature`/`requiredPlan`(nullable)/`currentPlan`/`limit`(nullable) — two constructors for the two gate shapes (tier vs. count).

`GlobalExceptionHandler` gained `handlePlanLimitExceeded`, following this file's exact existing convention (fully-qualified exception type inline, `problem(status, message, path)` helper, `problem.setProperty("code", ...)`): maps to `402 Payment Required` with `code: "PLAN_LIMIT_EXCEEDED"`, `feature`, `requiredPlan` (when set), `currentPlan`.

Frontend `extractPlanGateError(error)` mirrors the existing inline pattern already used in `TableInformation.tsx` for `CASH_SHIFT_OVERDUE` (`axios.isAxiosError(error) && error.response?.data?.code`), factored into a shared helper since this phase has 6 call sites instead of 1. Added one shared i18n key, `planGateUpgradeToast`, to the `common` locale (both `es`/`en`) for the "upgrade your plan" toast copy every gate's frontend wiring will use.

## 5. Why It Changed?
Per the approved spec (`docs/superpowers/specs/2026-09-14-plan-gating-design.md`) and plan — this is Task 1 of 10, infrastructure only, no behavior change yet (nothing calls `PlanGateService` until Task 2).

## 6. Verification
- TDD: `PlanGateServiceTest` (6 cases: below/at/above minimum, at/over/unlimited table capacity) written first, confirmed failing on compile (class didn't exist), then green. Same RED→GREEN for `planGate.test.ts` (3 cases).
- `./mvnw test` (full backend suite) — **1283/1283** (+6 new).
- `pnpm vitest run` (full frontend suite) — **139/139** (+3 new).
- `pnpm run build` — clean. `pnpm run lint` — 0 errors (16 pre-existing warnings, none in touched files).
- No live browser check — nothing user-facing changed yet (no gate calls this service until Task 2).
