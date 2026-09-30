-- EMB-DRAWER: cash-drawer kick jobs (PrintJobSourceType.CASH_DRAWER_KICK) and the printer flag that
-- says which printer has the drawer wired to its RJ11 kick-out port.
--
-- DROP...IF EXISTS + re-ADD is idempotent (prod Flyway is not baselined, see V9/V11). Without this
-- widening the first kick job would fail with a CHECK violation (silent-looking 500).
ALTER TABLE print_jobs DROP CONSTRAINT IF EXISTS print_jobs_source_type_check;
ALTER TABLE print_jobs ADD CONSTRAINT print_jobs_source_type_check
    CHECK (((source_type)::text = ANY ((ARRAY['BILL_RECEIPT'::character varying,
        'KITCHEN_TICKET'::character varying, 'CASH_DRAWER_KICK'::character varying])::text[])));

ALTER TABLE printer_configs ADD COLUMN IF NOT EXISTS cash_drawer boolean;
UPDATE printer_configs SET cash_drawer = false WHERE cash_drawer IS NULL;
ALTER TABLE printer_configs ALTER COLUMN cash_drawer SET NOT NULL;
ALTER TABLE printer_configs ALTER COLUMN cash_drawer SET DEFAULT false;
