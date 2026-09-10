-- Print-agent installer (2026-09-08 design spec): short-code pairing + printer discovery.
-- Idempotent (IF NOT EXISTS / ADD COLUMN IF NOT EXISTS) — prod Flyway is not baselined and
-- runs this on the next tagged backend release; local dev DB is baselined at v15 and skips it.

CREATE TABLE IF NOT EXISTS pairing_codes (
    code               varchar(12)  PRIMARY KEY,
    print_agent_id     uuid         NOT NULL REFERENCES print_agents (id) ON DELETE CASCADE,
    api_key_plaintext  varchar(255) NOT NULL,
    backend_base_url   varchar(255) NOT NULL,
    expires_at         timestamp(6) NOT NULL,
    consumed_at        timestamp(6),
    created_at         timestamp(6) NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pairing_codes_agent ON pairing_codes (print_agent_id);

ALTER TABLE print_agents ADD COLUMN IF NOT EXISTS paired_at          timestamp(6);
ALTER TABLE print_agents ADD COLUMN IF NOT EXISTS discovered_printers jsonb;
