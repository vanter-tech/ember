-- Staff Management (2026-08-15 design spec) — HR-flavored profile fields added directly to
-- `users` rather than a separate table: every WAITER/KITCHEN/ADMIN row gets exactly one of
-- these, so a second table would only add a join with no functional benefit.
--
-- `active`/`pending_hours` carry a literal DEFAULT, so Postgres backfills every pre-existing
-- row in the same statement — no separate runtime backfill job needed.

ALTER TABLE users ADD COLUMN IF NOT EXISTS active boolean NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS job_title varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS shift varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS contract_type varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS location varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS efficiency_percentage numeric(5,2);
ALTER TABLE users ADD COLUMN IF NOT EXISTS pending_hours numeric(6,2) NOT NULL DEFAULT 0;
