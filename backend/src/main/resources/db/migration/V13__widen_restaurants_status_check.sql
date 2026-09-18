-- RestaurantStatus.DELETED (soft-delete, added with V8's deleted_at/deleted_by columns) was never
-- added to this CHECK constraint, so every delete attempt has been hitting a live constraint
-- violation (a generic 500) despite passing every application-level guard. Same class of bug as
-- V11's users_role_check fix for ACCOUNTANT.
--
-- DROP...IF EXISTS + re-ADD is idempotent even if this constraint was already widened by hand
-- before Flyway ran it (see V9's note on prod not being baselined).
ALTER TABLE restaurants DROP CONSTRAINT IF EXISTS restaurants_status_check;
ALTER TABLE restaurants ADD CONSTRAINT restaurants_status_check
    CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying,
        'INACTIVE'::character varying, 'DELETED'::character varying])::text[])));
