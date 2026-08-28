# Report 264 — cleanup script for pre-existing stuck-OPEN dev bills

## 1. Identification
- **Report number:** 264
- **Task ID:** fix-stuck-bills-cleanup
- **Predecessor Task:** report 263 (fix-flyway-baseline)

## 2. Objective
Report 161 fixed the bill lifecycle so a settled table's `Bill` reliably reaches `PAID`, but it
did not backfill the three bills (`ids 1, 2, 10`) that were already stuck at `status = 'OPEN'`
with closed sessions. Those rows block their sessions from being re-billed (the
`(tenant_id, session_id) WHERE status <> 'VOIDED'` partial unique index) and read as
never-settled in the admin views. Last minor-gap item of Fase 1.

## 3. Modified Files
- `docs/db/dev-void-stuck-bills.sql` (new)

## 4. What Changed?
Added a one-time, DEV-ONLY SQL script. It:
- prints the current state of bills `1, 2, 10` and any `payments` / `bill_splits` attached to
  them (so the operator sees the before-picture — including the case where the 2026-08-24
  `ddl-auto=create` recovery already wiped them);
- `UPDATE`s only the rows that are **present and still `OPEN`**, setting `status = 'VOIDED'`,
  `voided_at = now()`, `void_reason = 'backfill: pre-existing stuck-OPEN dev bill (reports 161, 264)'`;
- prints the after-state.

It is idempotent and safe to run whether or not the bills exist. It lives in `docs/`, **not**
`db/migration/` — it is not a schema migration and Flyway must never execute it. Runbook
(`pg_dump` backup → `psql -f`) is in the file header.

## 5. Why It Changed?
`VOIDED` is the exact terminal state the app's re-billing guard
(`BillRepository.findBySessionIdAndStatusNot(sessionId, VOIDED)`, EMB-RV) is designed around, so
voiding these frees their sessions with no code change. It is also the honest label — the bills
were never paid, so marking them `PAID` would misreport them (and skew the PAID-bill AOV
metric). Deleting the rows was rejected: voiding keeps them inspectable and matches how the
application itself retires an abandoned bill. This is dev-data hygiene only; nothing ships to
production or to a Hub install (those never had these rows).

## Verification
- No application code changed — no build/test run applies (the file is a `docs/` SQL script).
- The script is applied and its before/after output confirmed against the live dev DB by the
  maintainer (same manual-step pattern as reports 254, 263).
