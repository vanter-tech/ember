-- The `restaurants` table predates task-2.10 (name/slug/plan/status/timezone/currency) and
-- task-4.4 (created_at). `ddl-auto: update` cannot add a NOT NULL column to a table that already
-- has rows without a default, so every one of these seven ALTER TABLEs has been silently failing
-- (WARN, not fatal) on every boot since — leaving any query against Restaurant, including
-- jwtAuthFilter's per-request tenant lookup, broken. Same add-nullable -> backfill ->
-- set-NOT-NULL pattern as V2.

ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS name varchar(255);
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS slug varchar(255);
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS plan varchar(255);
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS status varchar(255);
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS timezone varchar(255);
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS currency varchar(255);
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS created_at timestamp(6) with time zone;

-- Backfill deterministically from `id` (unique by construction), so this is safe to run against
-- any number of pre-existing rows, not just the single-tenant dev case this database has today.
UPDATE restaurants
SET name       = COALESCE(name, 'Restaurant ' || substr(id::text, 1, 8)),
    slug       = COALESCE(slug, 'restaurant-' || substr(id::text, 1, 8)),
    plan       = COALESCE(plan, 'FREE'),
    status     = COALESCE(status, 'ACTIVE'),
    timezone   = COALESCE(timezone, 'UTC'),
    currency   = COALESCE(currency, 'USD'),
    created_at = COALESCE(created_at, now())
WHERE name IS NULL OR slug IS NULL OR plan IS NULL OR status IS NULL
   OR timezone IS NULL OR currency IS NULL OR created_at IS NULL;

ALTER TABLE restaurants ALTER COLUMN name       SET NOT NULL;
ALTER TABLE restaurants ALTER COLUMN slug       SET NOT NULL;
ALTER TABLE restaurants ALTER COLUMN plan       SET NOT NULL;
ALTER TABLE restaurants ALTER COLUMN status     SET NOT NULL;
ALTER TABLE restaurants ALTER COLUMN timezone   SET NOT NULL;
ALTER TABLE restaurants ALTER COLUMN currency   SET NOT NULL;
ALTER TABLE restaurants ALTER COLUMN created_at SET NOT NULL;

-- Mirrors the CHECK/UNIQUE constraints Hibernate's ddl-auto has been trying (and failing) to add
-- alongside these columns every boot.
ALTER TABLE restaurants DROP CONSTRAINT IF EXISTS restaurants_plan_check;
ALTER TABLE restaurants ADD CONSTRAINT restaurants_plan_check
    CHECK (plan IN ('FREE', 'STARTER', 'PRO', 'ENTERPRISE'));

ALTER TABLE restaurants DROP CONSTRAINT IF EXISTS restaurants_status_check;
ALTER TABLE restaurants ADD CONSTRAINT restaurants_status_check
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'));

ALTER TABLE restaurants DROP CONSTRAINT IF EXISTS uk_restaurants_slug;
ALTER TABLE restaurants ADD CONSTRAINT uk_restaurants_slug UNIQUE (slug);
