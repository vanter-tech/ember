# Report 430

**Task ID:** EMB-EXPORT Task 3 — `ExportService`
**Predecessor Task:** report 429 — EMB-EXPORT Task 2 (repository finders)

## Objective
Build the tenant business-data export zip (`ventas.csv` + `productos.csv`), per Task 3 of `docs/superpowers/plans/2026-09-11-tenant-data-csv-export.md`.

## Modified Files
- `backend/src/main/java/com/vanter/ember/export/service/ExportService.java` (new)
- `backend/src/test/java/com/vanter/ember/export/service/ExportServiceTest.java` (new)

## What Changed?
New `ExportService.buildTenantExportZip(UUID tenantId, LocalDateTime from, LocalDateTime to) -> byte[]`:
- Defaults a missing `from`/`to` to the tenant's whole history up to now (same `EPOCH_FLOOR` sentinel `AnalyticsService` uses), and throws `IllegalArgumentException` on an inverted window.
- `ventas.csv` — queries `BillRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn` for `PAID`/`VOIDED` bills, batch-loads their payments (`PaymentRepository.findByBillIdIn`), joins back to `Session` → `DiningTables` for the table number, and writes one row per bill: id, table number, date, total, status, distinct `CONFIRMED`-payment methods (`/`-joined), distinct confirmed participant count. `PENDING` payments are excluded from both the methods list and the participant count.
- `productos.csv` — delegates entirely to the existing `AnalyticsService.getProducts(tenantId, from, to, null)` (no top-N limit) and writes one row per `ProductPerformance`.
- Both CSVs are written via `CsvWriter.writeRow` (Task 1) and packed into a zip with the JDK's `java.util.zip.ZipOutputStream` — no new dependency.

Written test-first: all 6 tests failed to compile against the not-yet-existing `ExportService` class, then the implementation was added and every test passed on the first run.

## Why It Changed?
Implements design-spec decisions #1, #5, #7, #8, #9 (sales+catalog scope, one row per bill, `PAID`+`VOIDED` only, no reconstructed tax column, no new dependency) — this is the core of the export feature the CSV zip is built from.

## Verification
`cd backend && ./mvnw test -Dtest=ExportServiceTest` — 6/6 passed.
`cd backend && ./mvnw test` — full suite, 1230/1230 passed.
