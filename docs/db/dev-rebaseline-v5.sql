-- ============================================================================
-- One-time realignment of the LOCAL DEV database's Flyway history
-- ============================================================================
--
-- Context (see PROGRESS.md "BRANCH EVENT 6" / reports 254, 259, 263):
--
-- The migration set was renumbered into a consolidated scheme:
--     V1__baseline_consolidated.sql   -- full schema as of 2026-08-24 (old V2..V15 folded in)
--     V2__hub_activations.sql
--     V3__printer_config_windows_queue.sql
--     V4__printer_config_render_mode.sql
--     V5__cash_shift_expiry.sql
--
-- A genuinely empty database (every fresh Ember Hub install) runs V1..V5 in order and is fine.
--
-- The real dev database still carries a `flyway_schema_history` baselined at the OLD **V15**
-- (set by hand during the 2026-08-24 flyway:clean recovery). Flyway only runs versions > 15,
-- so the renumbered V2..V5 are treated as "below baseline" and skipped on every boot. Their
-- schema changes have been applied to dev by hand instead (render_mode in report 254,
-- cash_shift_expiry in report 259).
--
-- This script discards that stale history and re-baselines dev at **V5** — truthful, because
-- the dev schema already physically contains everything through V5. After running it, Flyway's
-- notion of "current version" is 5 and future migrations (V6+) apply normally with no manual step.
--
-- This is a DEV-ONLY operation. It ships NOTHING to production or to a Hub install (those never
-- had the stale V15 row). It lives in docs/, NOT in db/migration/ — Flyway must never run it.
--
-- ----------------------------------------------------------------------------
-- RUN IT
-- ----------------------------------------------------------------------------
-- 1. Stop any running backend (nothing should be migrating while you do this).
--
-- 2. Back up first (adjust host/port/db/user to your .env):
--      pg_dump -h 127.0.0.1 -U ember -d ember -Fc -f ember-predebaseline-$(date +%Y%m%d).dump
--
-- 3. Apply this script:
--      psql -h 127.0.0.1 -U ember -d ember -v ON_ERROR_STOP=1 -f docs/db/dev-rebaseline-v5.sql
--
-- 4. Start the backend. Boot logs should show Flyway reporting "Successfully validated 5
--    migrations" / "Schema ... is up to date. No migration necessary." and NOT attempting to
--    run V2..V5.
-- ============================================================================

BEGIN;

-- Guard: refuse to run unless the stale single-row V15 baseline is exactly what we expect.
-- (If your dev DB is in a different state, stop and reassess rather than forcing this.)
DO $$
DECLARE
    row_count integer;
    max_version text;
BEGIN
    SELECT count(*), max(version) INTO row_count, max_version FROM flyway_schema_history;

    IF row_count IS NULL THEN
        RAISE EXCEPTION 'flyway_schema_history does not exist — this is not the expected dev DB state.';
    END IF;

    IF NOT (row_count = 1 AND max_version = '15') THEN
        RAISE EXCEPTION 'Expected exactly one history row baselined at version 15, found % row(s), max version %. Aborting.',
            row_count, max_version;
    END IF;
END $$;

-- Discard the old-scheme history and re-baseline at V5.
DELETE FROM flyway_schema_history;

INSERT INTO flyway_schema_history
    (installed_rank, version, description, type, script, checksum,
     installed_by, installed_on, execution_time, success)
VALUES
    (1, '5', 'realigned to consolidated V1-V5 scheme (docs/db/dev-rebaseline-v5.sql)',
     'BASELINE', 'realigned to consolidated V1-V5 scheme', NULL,
     CURRENT_USER, now(), 0, true);

-- Sanity check inside the transaction.
DO $$
DECLARE
    v text;
BEGIN
    SELECT version INTO v FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
    IF v <> '5' THEN
        RAISE EXCEPTION 'Post-insert check failed: latest version is %, expected 5.', v;
    END IF;
END $$;

COMMIT;

-- Show the result.
SELECT installed_rank, version, description, type, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
