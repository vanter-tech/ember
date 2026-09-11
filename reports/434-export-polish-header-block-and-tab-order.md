# Report 434

**Task ID:** EMB-EXPORT follow-up — CSV business header, column labels, tab order, double-frame fix
**Predecessor Task:** report 433 — EMB-EXPORT Task 6 (wire the Settings tab, plan complete)

## Objective
Address user feedback after testing the shipped export: the CSVs looked bare, the "Exportar datos" tab should sit above "Información", and there was a visible nested-border line on the Export tab.

## Modified Files
- `backend/src/main/java/com/vanter/ember/export/service/ExportService.java`
- `backend/src/test/java/com/vanter/ember/export/service/ExportServiceTest.java`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/pages/admin/components/settings/ExportSettings.tsx`

## What Changed?
1. **Business header block in both CSVs.** `ExportService` now takes a `SettingService` dependency and fetches the tenant's `BrandingSettings` (the same "Marca y negocio" data the admin already fills in) once per export. Both `ventas.csv` and `productos.csv` now start with a label/value block — Negocio, Nombre legal, RUC, Teléfono, Dirección, Rango exportado — followed by a blank line, before the actual column-header row. This makes either file self-describing if opened on its own. CSV tolerates the resulting ragged row lengths; every spreadsheet app just shows fewer values on the short header rows.
2. **Friendlier column headers.** `ventas.csv`: `bill_id,mesa,fecha,total,estado,metodos_pago,participantes` → `ID Cuenta,Mesa,Fecha,Total,Estado,Métodos de pago,Participantes`. `productos.csv`: `nombre,categoria,unidades_vendidas,ingresos,porcentaje_ingresos` → `Producto,Categoría,Unidades vendidas,Ingresos,% Ingresos`. Data columns/values are unchanged — only the header row text.
3. **Tab order.** `SettingsBar.tsx`'s `buildSettingsNav()`: `EXPORT` now comes before `INFO` (was after).
4. **Double-frame fix.** `ExportSettings.tsx` no longer wraps its content in a `<Card>`. `Settings.tsx`'s `#settings-tour-content` container is already a bordered, shadowed panel — nesting a second `Card` inside it drew a visible redundant border (the exact "double frame" `InfoSettings.tsx` was rebuilt to remove back in report 413). `ExportSettings.tsx` now uses the same flat `<div className="h-full flex flex-col gap-8 p-6 md:p-10">` structure `InfoSettings.tsx` uses, keeping the icon-circle + title/description header as plain markup instead of `CardHeader`/`CardTitle`.

All 4 `ExportServiceTest` cases exercising `buildTenantExportZip` were updated test-first (a new `sampleSettings()`/`expectedHeaderBlock()` fixture, and per-row assertions switched from fixed indices to `lines[lines.length - 2]`/`lines[lines.length - 1]` so they don't hardcode the header block's line count) — confirmed to fail against the pre-change implementation (unnecessary-stubbing + wrong-content failures) before the implementation was updated to match.

## Why It Changed?
Direct user feedback after manually testing the shipped feature: "ese csv esta sin estilo, hay que darle un estructura que de gusto ver, inyectarle datos ... traer la info de la sección Marca y negocio y ponerla como header"; "el sub-tab debe de estar arriba del sub-tab informacion"; "tiene una linea que lo separa de la otra card" (the nested-Card border).

## Verification
`cd backend && ./mvnw test -Dtest=ExportServiceTest` — 7/7 passed (1 new test: `ventasCsv_startsWithTheBusinessHeaderBlock`).
`cd backend && ./mvnw test` — full suite, 1236/1236 passed.
`cd frontend && pnpm run build` — clean.
`cd frontend && pnpm run test:run` — 124/124 passed, no regressions.
