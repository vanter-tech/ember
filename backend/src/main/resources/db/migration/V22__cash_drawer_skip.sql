-- Accountant may dismiss a failed drawer opening (cash is already received) so a busy shift is not
-- held up. Idempotent (prod Flyway is not baselined, see V9/V11). Prod runs ddl-auto=validate.
ALTER TABLE cash_drawer_events ADD COLUMN IF NOT EXISTS drawer_skipped_at timestamp(6) without time zone;
ALTER TABLE cash_drawer_events ADD COLUMN IF NOT EXISTS drawer_skipped_by varchar(255);
