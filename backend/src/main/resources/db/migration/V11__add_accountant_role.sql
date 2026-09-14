-- New ACCOUNTANT role: audits cash shifts (apertura/cierre de caja, arqueo) so the waiter no longer
-- self-certifies their own cash count. Role.java gained the enum constant; the users table stores
-- role as a plain string but is fenced by a CHECK constraint that must whitelist it too.
--
-- DROP...IF EXISTS + re-ADD is idempotent even if this constraint was already widened by hand
-- before Flyway ran it (see V9's note on prod not being baselined).
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'WAITER'::character varying,
        'KITCHEN'::character varying, 'ADMIN'::character varying, 'ACCOUNTANT'::character varying])::text[])));
