-- Subscription info shown in Settings > Plan and set by the platform operator. All nullable: a
-- tenant with no dates yet simply shows none. Idempotent (prod Flyway is not baselined, see V9/V11)
-- and free of CHECK constraints on purpose (see the CHECK-constraint trap in PROGRESS.md).
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS plan_started_at timestamp(6) with time zone;
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS billing_period varchar(20);
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS plan_period_end timestamp(6) with time zone;
