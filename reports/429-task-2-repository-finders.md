# Report 429

**Task ID:** EMB-EXPORT Task 2 — Repository finders
**Predecessor Task:** report 428 — EMB-EXPORT Task 1 (`CsvWriter`)

## Objective
Add the two tenant-scoped repository finders the CSV export's `ventas.csv` needs, per Task 2 of `docs/superpowers/plans/2026-09-11-tenant-data-csv-export.md`.

## Modified Files
- `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`
- `backend/src/test/java/com/vanter/ember/billing/repository/BillRepositoryTest.java`
- `backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java`
- `backend/src/test/java/com/vanter/ember/billing/repository/PaymentRepositoryTest.java`

## What Changed?
- `BillRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(UUID, LocalDateTime, LocalDateTime, Collection<BillStatus>) -> List<Bill>` — the tenant's bills in a date range whose status is one of the given set, oldest first. Same deliberate explicit `tenantId` predicate convention every other analytics-style query in this repository already uses.
- `PaymentRepository.findByBillIdIn(Collection<Long>) -> List<Payment>` — batch form of the existing `findByBillId`, avoiding an N+1 query when looking up payments across many bills.

Both were added test-first: each new test failed to compile against the not-yet-existing method, then the method was added and the test (and its whole test class) passed.

## Why It Changed?
Per the design spec, `ventas.csv` needs one row per `PAID`/`VOIDED` bill in a date range, with its payment methods and participant count — and no existing repository method lists raw bills by tenant+date-range+status, or batch-loads payments by bill id. These are the two gaps identified during the spec's codebase survey.

## Verification
`cd backend && ./mvnw test -Dtest=BillRepositoryTest` — 4/4 passed.
`cd backend && ./mvnw test -Dtest=PaymentRepositoryTest` — 4/4 passed.
`cd backend && ./mvnw test` — full suite, 1224/1224 passed (1216 + 6 from Task 1 + 2 from this task).
