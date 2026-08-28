-- ============================================================================
-- One-time cleanup of pre-existing stuck-OPEN bills in the LOCAL DEV database
-- ============================================================================
--
-- Context (see report 161, reports 264):
--
-- Three dev bills (ids 1, 2, 10) predate the OPEN->PAID fix from report 161 and were left in
-- `status = 'OPEN'` with their table sessions already closed. report 161 only stopped NEW bills
-- from getting stuck; it did not backfill these three. A stuck-OPEN bill blocks its session from
-- being re-billed (the `(tenant_id, session_id) WHERE status <> 'VOIDED'` partial unique index)
-- and shows up as an eternally-unsettled bill in the admin views.
--
-- Fix = VOID them. VOIDED is the terminal state the app's own re-billing guard
-- (`findBySessionIdAndStatusNot(sessionId, VOIDED)`) is built around, and it is the honest
-- label: these bills were never actually paid. The rows stay for inspection.
--
-- DEV-ONLY. Ships nothing to production or to a Hub install. Lives in docs/, NOT db/migration/.
-- Safe to run whether or not the bills still exist (the 2026-08-24 ddl-auto=create recovery may
-- have wiped them): it only touches rows that are present AND still OPEN.
--
-- ----------------------------------------------------------------------------
-- RUN IT
-- ----------------------------------------------------------------------------
--   pg_dump -h 127.0.0.1 -U ember -d ember -Fc -f ember-prestuckbills-$(date +%Y%m%d).dump
--   psql -h 127.0.0.1 -U ember -d ember -v ON_ERROR_STOP=1 -f docs/db/dev-void-stuck-bills.sql
-- ============================================================================

-- --- BEFORE: what (if anything) is there ---
\echo 'Target bills before:'
SELECT id, tenant_id, session_id, status, total, created_at
FROM bills
WHERE id IN (1, 2, 10)
ORDER BY id;

\echo 'Payments / splits attached to them:'
SELECT b.id AS bill_id,
       (SELECT count(*) FROM payments     p  WHERE p.bill_id  = b.id) AS payment_count,
       (SELECT count(*) FROM bill_splits  bs WHERE bs.bill_id = b.id) AS split_count
FROM bills b
WHERE b.id IN (1, 2, 10)
ORDER BY b.id;

BEGIN;

UPDATE bills
SET status      = 'VOIDED',
    voided_at   = now(),
    void_reason = 'backfill: pre-existing stuck-OPEN dev bill (reports 161, 264)'
WHERE id IN (1, 2, 10)
  AND status = 'OPEN';

\echo 'Rows voided by this run: (see UPDATE count above)'

COMMIT;

-- --- AFTER ---
\echo 'Target bills after:'
SELECT id, status, voided_at, void_reason
FROM bills
WHERE id IN (1, 2, 10)
ORDER BY id;
