# Report 431

**Task ID:** EMB-EXPORT Task 4 — `ExportController`
**Predecessor Task:** report 430 — EMB-EXPORT Task 3 (`ExportService`)

## Objective
Expose the tenant CSV export over HTTP, per Task 4 of `docs/superpowers/plans/2026-09-11-tenant-data-csv-export.md`.

## Modified Files
- `backend/src/main/java/com/vanter/ember/export/controller/ExportController.java` (new)
- `backend/src/test/java/com/vanter/ember/export/controller/ExportControllerTest.java` (new)

## What Changed?
New `GET /admin/export?from=&to=` (`@PreAuthorize("hasRole('ADMIN')")`, same class-level pattern as `AnalyticsController`): calls `ExportService.buildTenantExportZip(TenantContextHolder.requireTenantId(), from, to)` and returns the bytes with `Content-Type: application/zip` and `Content-Disposition: attachment; filename="ember-export-<today>.zip"`. `from`/`to` use the same optional `@DateTimeFormat(ISO.DATE_TIME)` convention every other analytics endpoint uses.

Written test-first: all 5 tests failed to compile against the not-yet-existing `ExportController` class, then the implementation was added and every test passed on the first run — happy path with attachment headers, missing params passed through as null, 403 for non-ADMIN, 409 with no tenant bound, 401 unauthenticated.

## Why It Changed?
This is the HTTP surface Task 5's frontend `exportService.downloadTenantData` calls — the last backend piece of the export feature. Completes all 4 backend tasks of the plan.

## Verification
`cd backend && ./mvnw test -Dtest=ExportControllerTest` — 5/5 passed.
`cd backend && ./mvnw test` — full suite, 1235/1235 passed (all 4 backend export tasks: 6 + 2 + 6 + 5 = 19 new tests over the pre-existing 1216).
