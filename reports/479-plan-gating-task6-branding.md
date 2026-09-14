# Report 479

## 1. Identification
- **Report Number:** 479
- **Task ID:** PLAN-GATING-PHASE1 Task 6 — gate: custom branding (STARTER+)
- **Predecessor Task:** report 478 (export gate)

## 2. Objective
Gate branding changes (business name, legal name, RUC, phone, address, hours, wifi name, theme color) behind STARTER+, per the approved plan (`docs/superpowers/plans/2026-09-14-plan-gating-phase1.md`, Task 6). Non-branding settings changes (tables, billing, hardware, etc.) must go through untouched even on FREE.

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/settings/service/SettingService.java`
- Modify: `backend/src/test/java/com/vanter/ember/settings/service/SettingServiceTest.java`
- Modify: `frontend/src/pages/admin/components/settings/BrandingSettings.tsx`

## 4. What Changed?
`SettingService.updateSettings` (already carrying `PlanGateService` since Task 2's table-count gate) now fetches the tenant's current settings first, compares `payload.getBranding()` against `currentSettings.getPayload().getBranding()` using `SettingsPayload.BrandingSettings`'s Lombok-generated `equals()`, and only calls `planGateService.requirePlanAtLeast(restaurantId, RestaurantPlan.STARTER, "branding")` when they differ — a save that leaves branding untouched (e.g. just bumping the table count) skips the check entirely. `BrandingSettings.tsx`'s save mutation shows the shared plan-gate toast on 402.

## 5. Why It Changed?
Sixth task of PLAN-GATING-PHASE1. This is the second (and last) gate living inside `SettingService.updateSettings`, alongside the tables gate from Task 2 — same method, same `PlanGateService` dependency, no new wiring needed on that front.

## 6. Verification
- TDD: `updateSettings_blockedWhenBrandingChangedAndPlanBelowStarter` written first, confirmed RED ("Expecting code to raise a throwable" — nothing compared branding yet), GREEN after wiring; `updateSettings_allowedWhenBrandingUnchanged` added alongside to lock in that an unrelated settings save never touches the gate.
- `./mvnw test` (full backend suite) — **1291/1291** (+2 new). `SettingsControllerTest` re-run standalone — 9/9, unaffected (fully mocks `SettingService`).
- `pnpm run build` — clean. `pnpm run lint` — 0 errors. `pnpm vitest run` — **142/142**.
- No live browser check.
