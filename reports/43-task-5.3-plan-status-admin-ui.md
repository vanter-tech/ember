# Report 43 — task-5.3

**Predecessor Task:** task-5.2 (plus ad-hoc backend fixes, reports 41–42)

## Objective
Give the tenant's own ADMIN a UI for task-4.4's plan self-service (`GET`/`PATCH /admin/restaurant/plan`), which had zero frontend surface until now.

## Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/store/uiStore.ts`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/pages/admin/Settings.tsx`
- `frontend/src/pages/admin/components/settings/PlanSettings.tsx` (new)

## What Changed?
- `api.ts`: added `restaurantAdminService` (`getPlan` → `GET /admin/restaurant`, `updatePlan` → `PATCH /admin/restaurant/plan`), typed from the regenerated `Restaurant`/`UpdateRestaurantPlanRequest`.
- `uiStore.ts`: added `'PLAN'` to `SettingsType`.
- `SettingsBar.tsx`: new "Plan y Estado" tab.
- `PlanSettings.tsx` (new): shows account status as a read-only `Badge` (no self-service status endpoint exists, by design — see `RestaurantService.updateStatus`'s javadoc) and a plan `<select>` (`FREE`/`STARTER`/`PRO`/`ENTERPRISE`) + save/undo, following `SpaceSettings.tsx`'s query/mutation/toast pattern. Used a native `<select>` styled to match `Input`'s classes, since no shadcn `Select` wrapper exists in `components/ui/` yet.
- `Settings.tsx`: wired the new `'PLAN'` case.

## Why It Changed?
task-4.4 wired `RestaurantAdminController` on the backend but nothing consumed it, so a tenant's ADMIN had no way to see or change their plan.

## Verification
- `pnpm run build` — PASSING (0 TS errors).
- `curl http://localhost:8080/v1/admin/restaurant` → `401` (confirms the route is live and gated).
- **Not done:** a full logged-in-as-ADMIN browser walkthrough of the new tab. Build + a live 401 check are the only verification here.
