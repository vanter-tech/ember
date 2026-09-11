# Report 428

**Task ID:** EMB-EXPORT Task 1 — `CsvWriter`
**Predecessor Task:** report 427 — Downloads page print-agent cloud badge (unrelated; this is the first task of a new plan)

## Objective
Add a dependency-free RFC 4180 CSV row writer, the first building block of the tenant data CSV export feature (`docs/superpowers/plans/2026-09-11-tenant-data-csv-export.md`, Task 1).

## Modified Files
- `backend/src/main/java/com/vanter/ember/export/util/CsvWriter.java` (new)
- `backend/src/test/java/com/vanter/ember/export/util/CsvWriterTest.java` (new)

## What Changed?
New `CsvWriter.writeRow(List<String> fields) -> String`: comma-joins fields, CRLF-terminated. A field is quoted only when it contains a comma, double quote, or newline; embedded quotes are doubled (standard RFC 4180 escaping). A `null` field becomes an empty string. No CSV library dependency — this app has none today, and the export's only need is two fixed-column files.

## Why It Changed?
Per the approved design spec (`docs/superpowers/specs/2026-09-11-tenant-data-csv-export-design.md`, decision #9): "No new dependency — CSV written by a small hand-rolled RFC-4180 helper." This is Task 1 of the 6-task implementation plan, executed via TDD (failing test written and confirmed to fail on a missing `CsvWriter` symbol, then the minimal implementation added).

## Verification
`cd backend && ./mvnw test -Dtest=CsvWriterTest` — 6/6 passed (plain fields, comma, double-quote, newline, null field, empty list).
