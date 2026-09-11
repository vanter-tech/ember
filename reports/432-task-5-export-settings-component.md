# Report 432

**Task ID:** EMB-EXPORT Task 5 — Frontend `exportService` + `ExportSettings` component
**Predecessor Task:** report 431 — EMB-EXPORT Task 4 (`ExportController`)

## Objective
Add the frontend piece that calls the export endpoint and triggers the browser download, per Task 5 of `docs/superpowers/plans/2026-09-11-tenant-data-csv-export.md`.

## Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/pages/admin/components/settings/ExportSettings.tsx` (new)
- `frontend/src/pages/admin/components/settings/ExportSettings.test.tsx` (new)
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`

## What Changed?
- `exportService.downloadTenantData(from?: string, to?: string) -> Promise<Blob>` in `api.ts` — `GET /admin/export` with `responseType: 'blob'`.
- New `ExportSettings.tsx`: a Card (same icon-circle + title/description layout `HardwareSettings.tsx` uses) with two native `<input type="date">` fields (this app's first date-range picker — no existing calendar component to reuse, confirmed during the design spec's codebase survey) and a "Descargar" button. On click, converts the picked dates to full-day ISO bounds (`T00:00:00`/`T23:59:59`, blank → `undefined`), downloads the blob, and saves it via a temporary `<a download>` anchor. Success/error toasts via `react-hot-toast`.
- +10 i18n keys/locale (`export*`).

Written test-first: the 3-test file failed to resolve `./ExportSettings` (didn't exist), then the component was added and all 3 passed on the first run — no-dates sends `undefined`/`undefined`, a picked range sends full-day ISO strings, a rejected download doesn't throw out of the component.

## Why It Changed?
This is the UI half of the export feature — the piece an admin actually interacts with. Not yet reachable from the Settings sidebar (that's Task 6).

## Verification
`cd frontend && pnpm vitest run ExportSettings.test.tsx` — 3/3 passed.
