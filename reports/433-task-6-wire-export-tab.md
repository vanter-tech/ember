# Report 433

**Task ID:** EMB-EXPORT Task 6 — Wire the "Exportar datos" Settings tab (final task)
**Predecessor Task:** report 432 — EMB-EXPORT Task 5 (`ExportSettings` component)

## Objective
Make `ExportSettings.tsx` reachable from the admin Settings sidebar, per Task 6 (the last task) of `docs/superpowers/plans/2026-09-11-tenant-data-csv-export.md`.

## Modified Files
- `frontend/src/store/uiStore.ts`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/components/GlobalSearchResults.tsx`
- `frontend/src/pages/admin/Settings.tsx`

## What Changed?
Pure mechanical wiring into the 4 places every flat Settings tab (e.g. `INFO`) already goes through: `SettingsType` gains `'EXPORT'`; `SettingsBar.tsx`'s `LEAF` record and `buildSettingsNav()` gain the new leaf (`Download` icon, right after `INFO`); `GlobalSearchResults.tsx`'s `SETTINGS_TAB_LABEL_KEYS` gains the matching entry so global search doesn't drift from the sidebar; `Settings.tsx` renders `<ExportSettings />` for the new case.

No dedicated test for this task — no test in this codebase asserts on `SettingsBar`'s per-leaf wiring beyond the Hub-build visibility test (untouched here). Verification is the build (`SettingsBar.tsx`'s `LEAF` and `GlobalSearchResults.tsx`'s `SETTINGS_TAB_LABEL_KEYS` are both non-partial `Record`s, so a missed entry would fail to compile) plus the full test suite.

## Why It Changed?
Closes out EMB-EXPORT — this was the last of the 6 planned tasks. The tenant data CSV export feature (spec `docs/superpowers/specs/2026-09-11-tenant-data-csv-export-design.md`) is now code-complete end to end: `GET /admin/export` → `ExportSettings.tsx`, reachable from Settings.

## Verification
`cd frontend && pnpm run build` — clean.
`cd frontend && pnpm run test:run` — 124/124 passed (121 pre-existing + 3 `ExportSettings` from Task 5; this task added no new tests).
Not manually smoke-tested in a running browser this session — the plan's final manual-verification bullet (log in as ADMIN, click through "Exportar datos", confirm the zip downloads with both CSVs) is still open.
