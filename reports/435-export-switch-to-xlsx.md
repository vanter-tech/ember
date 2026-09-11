# Report 435

**Task ID:** EMB-EXPORT follow-up — switch to a real styled .xlsx (Apache POI), fix UTF-8 mojibake
**Predecessor Task:** report 434 — CSV business header, column labels, tab order, double-frame fix

## Objective
User tested the shipped CSV export and reported (1) mojibake in accented characters ("TelÃ©fono", "DirecciÃ³n") and (2) that a CSV genuinely can't look "nice" — no bold, no color, no real structure. Asked directly whether a dependency could help. Presented the tradeoff (real .xlsx via Apache POI vs. staying with CSV) — user chose Apache POI.

## Modified Files
- `backend/pom.xml`
- `backend/src/main/java/com/vanter/ember/export/service/ExportService.java`
- `backend/src/test/java/com/vanter/ember/export/service/ExportServiceTest.java`
- `backend/src/main/java/com/vanter/ember/export/controller/ExportController.java`
- `backend/src/test/java/com/vanter/ember/export/controller/ExportControllerTest.java`
- `frontend/src/pages/admin/components/settings/ExportSettings.tsx`
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`
- Deleted: `backend/src/main/java/com/vanter/ember/export/util/CsvWriter.java`, `backend/src/test/java/com/vanter/ember/export/util/CsvWriterTest.java`

## What Changed?
- **New dependency**: `org.apache.poi:poi` + `poi-ooxml` 5.3.0 — this reverses the earlier "no new dependency" design decision (report 428's spec choice), explicitly by the user's request, since real cell styling is not something a CSV can ever provide regardless of formatting effort.
- **`ExportService.buildTenantExportZip` → `buildTenantExportWorkbook`**: no longer builds a zip of two CSVs — builds a single `.xlsx` `XSSFWorkbook` with two sheets, **Ventas** and **Productos**. Each sheet: the same business header block as before (Negocio/Nombre legal/RUC/Teléfono/Dirección/Rango exportado — bold labels, no fill) at rows 0-5, a blank row 6, then a **bold white-on-brand-red (`#8c1717`)** column-header row 7, then data from row 8. Numbers are real numeric cells (formatted `#,##0.00` for money, `#,##0` for counts) so Excel can sum/sort them — not text strings like the CSV had. The "Fecha" column is a real Excel date cell (`yyyy-mm-dd hh:mm` format), not an ISO text string. All columns are auto-sized. `CsvWriter` is now dead code — deleted along with its test.
- **`ExportController`**: calls the renamed service method; `Content-Type` → `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`; filename → `ember-export-<date>.xlsx`.
- **Frontend**: download filename extension `.zip` → `.xlsx`; the Settings-tab description copy now says "Excel (.xlsx)" instead of ".zip con archivos CSV" / ".zip of CSV files".
- The UTF-8 mojibake bug is moot now — POI's `.xlsx` format stores strings as proper UTF-8-encoded XML internally (via the OOXML shared-strings table), so there's no BOM/encoding negotiation with Excel to get wrong the way a raw CSV has.

All `ExportServiceTest`/`ExportControllerTest` cases were rewritten test-first for the new workbook shape (reading cells back via `XSSFWorkbook`/`Row`/`Cell` instead of unzip+string-split), confirmed to fail against the pre-change implementation (missing `buildTenantExportWorkbook` symbol), then passed once the implementation was rewritten.

## Why It Changed?
Direct user feedback: the shipped CSV had a real encoding bug, and no amount of CSV restructuring can satisfy "que dé gusto ver ... con color" — that requires an actual spreadsheet file format. The user explicitly asked for a dependency recommendation and chose Apache POI (the standard JVM library for Excel generation) over staying with plain CSV.

## Verification
`cd backend && ./mvnw test -Dtest=ExportServiceTest,ExportControllerTest` — 12/12 passed.
`cd backend && ./mvnw test` — full suite, 1230/1230 passed (1236 minus the 6 deleted `CsvWriterTest` cases).
`cd frontend && pnpm run build` — clean.
`cd frontend && pnpm run test:run` — 124/124 passed, no regressions.
Not yet manually re-verified in a live browser/Excel — recommend the user re-download and open the file to confirm the styling and accented characters render correctly.
