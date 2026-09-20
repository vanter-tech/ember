-- On-premise Hub: a receipt can be routed to the print agent of the caja that asked for it.
-- Plain reference (no FK), like print_jobs.source_id - a deleted agent just falls back to
-- "all printers of the role". Idempotent: prod Flyway is not baselined; the local dev DB is
-- baselined at v15 and skips it, so add the column by hand there.
ALTER TABLE print_jobs ADD COLUMN IF NOT EXISTS target_agent_id uuid;
