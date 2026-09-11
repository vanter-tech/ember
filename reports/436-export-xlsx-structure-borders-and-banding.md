# Report 436

**Task ID:** EMB-EXPORT follow-up — add real spreadsheet structure to the .xlsx (borders, title bar, row banding)
**Predecessor Task:** report 435 — switch export from CSV/zip to a styled .xlsx via Apache POI

## Objective
User re-tested the `.xlsx` from report 435 and reported it still "se ve todo pegado sin espacio entre celdas" (looks stuck together, no space between cells), pointing at a reference image (`excel_template.png` in repo root) showing a title bar, bordered/gridlined table, and banded rows. The actual defect was that no cell had a border or fill differentiation, so nothing visually separated one cell from the next when opened in Excel.

## Modified Files
- `backend/src/main/java/com/vanter/ember/export/service/ExportService.java`
- `backend/src/test/java/com/vanter/ember/export/service/ExportServiceTest.java`

## What Changed?
- **Thin borders on every cell** the service writes — business header labels/values, column header row, and every data cell — via a shared `applyThinBorder(CellStyle)` helper called from each style factory. This is the main fix for "todo pegado."
- **Merged title bar** (row 0) per sheet — "Reporte de Ventas" / "Reporte de Productos", bold white text on brand red (`#8c1717`), centered, merged across the sheet's used columns (`CellRangeAddress`) — echoing the reference image's dark title band. The business header block, blank spacer row, column header row, and data all shifted down by one row (block now rows 1-6, blank row 7, column headers row 8, data from row 9) to make room.
- **Zebra striping** on data rows only: a `RowStyles` record bundles the four styled cell kinds (text/date/money/count) for one fill band; two bands are built once per workbook (`bandA` = no fill, `bandB` = light gray `#F2F2F2`) and alternated per data row index, independently for the Ventas and Productos sheets.
- **Column width padding**: after `sheet.autoSizeColumn(col)`, added 640 width units (~2.5 characters) so text doesn't touch the cell edges.
- Introduced a `plainStyle`/`textCell` pair for non-bold bordered cells (header-block values, Estado/Métodos de pago/Producto/Categoría), since those previously had no `CellStyle` at all.

Row-index shift required updating every existing row-position assertion in `ExportServiceTest` (business header block, column header row, first data row) plus three new tests: column-header cells have thin borders on all sides, the title row is bold/merged/correctly spans each sheet's columns, and two consecutive Ventas data rows have alternating fill (`NO_FILL` then `SOLID_FOREGROUND`).

## Why It Changed?
Direct user feedback pointing at a reference screenshot: a spreadsheet without borders, a title, or banding reads as unstructured raw data even once the CSV encoding/color issues were already fixed in report 435. Apache POI was already the chosen dependency (report 435), so this is a styling-only follow-up — no new dependency, no data/column changes.

## Verification
`cd backend && ./mvnw test -Dtest=ExportServiceTest,ExportControllerTest` — 15/15 passed (10 service + 5 controller).
`cd backend && ./mvnw test` — full suite, 1233/1233 passed.
Not yet manually re-verified in a live browser/Excel — recommend the user re-download and open the file to confirm borders, title bar, and banding render as expected.
