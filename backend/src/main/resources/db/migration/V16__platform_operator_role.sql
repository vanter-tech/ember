-- F-14: /platform/** had no per-operator role, so every authenticated operator had full
-- access (create/delete/suspend tenants, change plans) with no way to grant a narrower,
-- read-only "support" account. platform_operators.role backs PlatformOperatorRole.java.
--
-- Backfill existing rows to SUPER_ADMIN: preserves the access every operator already had
-- before this migration ran (matches the operators-are-hand-inserted reality today).
--
-- DROP...IF EXISTS + re-ADD is idempotent, same pattern as V11/V13.
ALTER TABLE platform_operators ADD COLUMN IF NOT EXISTS role VARCHAR(255);
UPDATE platform_operators SET role = 'SUPER_ADMIN' WHERE role IS NULL;
ALTER TABLE platform_operators ALTER COLUMN role SET NOT NULL;

ALTER TABLE platform_operators DROP CONSTRAINT IF EXISTS platform_operators_role_check;
ALTER TABLE platform_operators ADD CONSTRAINT platform_operators_role_check
    CHECK (((role)::text = ANY ((ARRAY['SUPER_ADMIN'::character varying,
        'SUPPORT'::character varying])::text[])));
