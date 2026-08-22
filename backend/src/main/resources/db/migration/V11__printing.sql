-- Hardware Bridge (EMB-PRINT, 2026-08-22 design spec): real ESC/POS printing via a local
-- agent process. print_agents/printer_configs are admin-managed from the settings UI;
-- print_jobs is the outbox the dispatch service drains over the isolated /ws/print-agent
-- channel. api_key_hash is a BCrypt hash, same encoder as users.password — never plaintext.

CREATE TABLE IF NOT EXISTS print_agents (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    name          varchar(100) NOT NULL,
    api_key_hash  varchar(255) NOT NULL,
    status        varchar(20) NOT NULL DEFAULT 'ACTIVE',
    last_seen_at  timestamp,
    created_at    timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_print_agents_tenant ON print_agents (tenant_id);

CREATE TABLE IF NOT EXISTS printer_configs (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL,
    agent_id         uuid NOT NULL REFERENCES print_agents(id),
    role             varchar(20) NOT NULL,
    connection_type  varchar(20) NOT NULL,
    host             varchar(255),
    port             integer,
    com_port         varchar(20),
    label            varchar(100) NOT NULL,
    active           boolean NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS idx_printer_configs_tenant_role ON printer_configs (tenant_id, role);
CREATE INDEX IF NOT EXISTS idx_printer_configs_agent ON printer_configs (agent_id);

CREATE TABLE IF NOT EXISTS print_jobs (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL,
    role         varchar(20) NOT NULL,
    source_type  varchar(20) NOT NULL,
    source_id    varchar(64) NOT NULL,
    payload      text NOT NULL,
    status       varchar(20) NOT NULL DEFAULT 'PENDING',
    attempts     integer NOT NULL DEFAULT 0,
    last_error   varchar(255),
    created_at   timestamp NOT NULL,
    updated_at   timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_print_jobs_tenant_status ON print_jobs (tenant_id, status);
