# Report 49 — Add "Horario" Settings Tab

**Report Number:** 49
**Task ID:** task-5.7
**Predecessor Task:** task-5.6 (report 48)

## Objective
Add a new "Horario" `SettingsBar` tab + weekly-schedule editor for `SettingsPayload.businessHours` (`BusinessHoursSettings.schedule`, per-`DayOfWeek`), distinct from `BrandingSettings`'s single daily opening/closing time.

## Modified Files
- `frontend/src/store/uiStore.ts`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/pages/admin/components/settings/BusinessHoursSettings.tsx` (new)
- `frontend/src/pages/admin/Settings.tsx`

## What Changed?
- `uiStore.ts`'s `SettingsType` union extended with `'HORARIO'`.
- `SettingsBar.tsx` gained a "Horario" nav entry (`Clock` icon) wired to `openSettings('HORARIO')`.
- Added `BusinessHoursSettings.tsx`, following the established `SettingsService`/react-query draft pattern. Renders all 7 `DayOfWeek` rows (Lunes–Domingo) regardless of what's persisted — missing days are backfilled client-side via `defaultDaySchedule` (`closed:false`, `09:00`–`18:00`) so the editor is always a complete week. Each row has an open/closed `Switch` and `openTime`/`closeTime` time inputs (disabled when closed). Saving writes the full 7-entry array to `SettingsPayload.businessHours.schedule`.
- `Settings.tsx`'s `HORARIO` case now renders `<BusinessHoursSettings />`.

## Why It Changed?
`SettingsPayload.businessHours` (task-3.8) had no frontend surface and is semantically distinct from `BrandingSettings.openingTime`/`closingTime` (a single daily fallback shown publicly) — it needs its own per-day editor, so it got its own tab rather than being folded into `BRANDING` or `BILLING`.

## Verification
`cd frontend && pnpm run build` — passed, 0 TypeScript errors.
