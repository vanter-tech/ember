# Report 50 — Wire HARDWARE Settings Tab

**Report Number:** 50
**Task ID:** task-5.8
**Predecessor Task:** task-5.7 (report 49)

## Objective
Wire the `HARDWARE` tab in `Settings.tsx` (previously a `<div>Hardware Settings</div>` placeholder) to `SettingsPayload.hardware` (`autoPrintTickets`, `printCustomerReceipt`).

## Modified Files
- `frontend/src/pages/admin/components/settings/HardwareSettings.tsx` (new)
- `frontend/src/pages/admin/Settings.tsx`

## What Changed?
- Added `HardwareSettings.tsx`, following the existing `MenuSettings.tsx` pattern: `useQuery`/`useMutation` via `SettingsService.getSettings`/`updateSettings`, local draft state with undo/save, and two `Switch` toggles (`autoPrintTickets`, `printCustomerReceipt`) bound to `SettingsPayload.hardware`.
- `Settings.tsx`'s `HARDWARE` case now renders `<HardwareSettings />` instead of the placeholder `<div>`.

## Why It Changed?
`SettingsPayload.hardware` has existed on the backend since task-3.8 but had no frontend surface — the `HARDWARE` tab was still a stub. This closes that gap using the same `SettingsService`/react-query pattern already established for `MENU`/`BILLING`/`HORARIO`, keeping consistency across settings tabs.

## Verification
`cd frontend && pnpm run build` — passed, 0 TypeScript errors.
