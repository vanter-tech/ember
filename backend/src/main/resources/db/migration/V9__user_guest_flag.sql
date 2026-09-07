-- Walk-in diners can join a table without an account (see the guest-join spec). A guest is a
-- real users row flagged here; it never earns loyalty points or visits.
--
-- IF NOT EXISTS: prod Flyway is not yet baselined and this migration may meet a schema where the
-- column was already added by hand. Idempotent DDL turns that into a no-op instead of the hard
-- failure that took prod down on 2026-09-06 when V7 was pre-applied. Postgres 9.6+.
ALTER TABLE users ADD COLUMN IF NOT EXISTS guest boolean NOT NULL DEFAULT false;
