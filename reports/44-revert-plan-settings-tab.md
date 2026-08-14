# Report 44 — revert task-5.3's UI (not a backlog task)

**Predecessor Task:** task-5.3 (report 43)

## Objective
Remove the "Plan y Estado" settings tab task-5.3 added — plan/subscription self-service belongs on a separate customer/billing surface (TBD, not the operational restaurant-settings tabs), and that surface's location (landing site login vs. elsewhere) is intentionally undecided for now.

## Modified Files
- `frontend/src/pages/admin/Settings.tsx`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/store/uiStore.ts`
- `frontend/src/pages/admin/components/settings/PlanSettings.tsx` (deleted)

## What Changed?
Removed the `'PLAN'` tab: the `SettingsBar` button, the `Settings.tsx` case, `'PLAN'` from `uiStore.ts`'s `SettingsType`, and deleted `PlanSettings.tsx` entirely. `frontend/src/lib/api.ts`'s `restaurantAdminService` (`getPlan`/`updatePlan`) was deliberately left in place — it's a thin, correct wrapper over the real `GET`/`PATCH /admin/restaurant/plan` endpoints from task-4.4, and can be reused once the actual destination for plan management is designed.

## Why It Changed?
Product decision: subscription/billing management is a different concern from restaurant operational settings and shouldn't be a tab alongside branding/menu/tax config. Where it *should* live (a separate customer account portal, possibly reachable from a not-yet-built Ember marketing/landing site) is an open question, paused deliberately rather than guessed at.

## Verification
`pnpm run build` — PASSING (0 TS errors); output bundle size matches the pre-task-5.3 build exactly (677.48 kB), confirming a clean removal with no leftover references.
