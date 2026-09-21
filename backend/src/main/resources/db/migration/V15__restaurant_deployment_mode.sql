-- Restaurant deployment mode (design: docs/superpowers/specs/2026-09-20-restaurant-deployment-mode-design.md).
-- Idempotent: production Flyway is not baselined; the local dev DB is baselined past this version
-- and needs the column added by hand. The backfill below only runs when Flyway applies this
-- migration for the first time; never re-run it by hand on a live database (it would undo an
-- operator's mode change).
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS deployment_mode varchar(10) NOT NULL DEFAULT 'CLOUD';

ALTER TABLE restaurants DROP CONSTRAINT IF EXISTS restaurants_deployment_mode_check;
ALTER TABLE restaurants ADD CONSTRAINT restaurants_deployment_mode_check
    CHECK (deployment_mode IN ('CLOUD', 'HUB'));

-- A restaurant that already has a Hub activation, or for which a Hub license was issued (even if
-- not activated yet), is a Hub restaurant; everything else stays CLOUD.
UPDATE restaurants SET deployment_mode = 'HUB'
 WHERE id IN (SELECT restaurant_id FROM hub_activations)
    OR id IN (SELECT restaurant_id FROM platform_audit_log
               WHERE action = 'HUB_LICENSE_ISSUED' AND restaurant_id IS NOT NULL);
