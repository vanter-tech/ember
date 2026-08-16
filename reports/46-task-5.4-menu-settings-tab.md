# Report 46 — Wire MENU Settings Tab

**Report Number:** 46
**Task ID:** task-5.4
**Predecessor Task:** task-5.3 (report 43, reverted report 44)

## Objective
Wire the `MENU` tab in `Settings.tsx` (previously a `<div>Menu Settings</div>` placeholder) to `SettingsPayload.menu` (`showOutOfStockItems`, `enableItemSearch`).

## Modified Files
- `frontend/src/pages/admin/components/settings/MenuSettings.tsx` (new)
- `frontend/src/pages/admin/Settings.tsx`

## What Changed?
- Added `MenuSettings.tsx`, following the existing `SpaceSettings.tsx` pattern: `useQuery`/`useMutation` via `SettingsService.getSettings`/`updateSettings`, local draft state with undo/save, and two `Switch` toggles (`showOutOfStockItems`, `enableItemSearch`) bound to `SettingsPayload.menu`.
- `Settings.tsx`'s `MENU` case now renders `<MenuSettings />` instead of the placeholder `<div>`.

## Why It Changed?
`SettingsPayload.menu` has existed on the backend since task-3.8 but had no frontend surface — the `MENU` tab was still a stub. This closes that gap using the same `SettingsService`/react-query pattern already established for `BRANDING`/`SPACE`, keeping consistency across settings tabs.

## Verification
`cd frontend && pnpm run build` — passed, 0 TypeScript errors.
