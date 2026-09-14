# Report 475

## 1. Identification
- **Report Number:** 475
- **Task ID:** PLAN-GATING-PHASE1 Task 2 — gate: dining table count
- **Predecessor Task:** report 474 (`PlanGateService` core)

## 2. Objective
First real gate: cap the number of dining tables a tenant can configure to its plan's limit (FREE=1, STARTER=10, PRO/ENTERPRISE=unlimited), per the approved plan (`docs/superpowers/plans/2026-09-14-plan-gating-phase1.md`, Task 2).

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/settings/service/SettingService.java`
- Create: `backend/src/test/java/com/vanter/ember/settings/service/SettingServiceTest.java` (no such file existed before)
- Modify: `frontend/src/pages/admin/components/settings/SpaceSettings.tsx`

## 4. What Changed?
`SettingService` now takes a `PlanGateService` and calls `requireTableCapacity(restaurantId, payload.getSpace().getTotalTables())` as the very first line of `updateSettings`, before anything is fetched or persisted — a blocked request never touches the database (settings row, dining tables) at all. `SpaceSettings.tsx`'s save mutation now checks `extractPlanGateError` in `onError` and shows the shared "requires plan X" toast instead of the generic save-error toast when the block is a plan gate.

## 5. Why It Changed?
Second task of PLAN-GATING-PHASE1 — the simplest of the 6 gates (single insertion point, reuses the table-count check already built in Task 1's `PlanGateService`).

## 6. Verification
- TDD: `SettingServiceTest` (new file — no prior test existed for `SettingService`) written first; the "blocked" case confirmed RED (NPE further down the method, since the gate wasn't wired yet — proved the test actually exercises the path), then GREEN after wiring.
- `./mvnw test` (full backend suite) — **1285/1285** (+2 new). `SettingsControllerTest` re-run standalone to confirm no regression (it fully mocks `SettingService`, unaffected by the new constructor param) — 9/9.
- `pnpm run build` — clean. `pnpm run lint` — 0 errors (16 pre-existing warnings, none in touched files). `pnpm vitest run` — **139/139**.
- No live browser check — worth confirming next time a FREE-plan tenant is exercised live that saving more than 1 table shows the toast instead of a silent failure.
