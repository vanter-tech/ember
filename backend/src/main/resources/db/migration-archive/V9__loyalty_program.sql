-- Customer Loyalty Program engine (2026-08-17 design spec) — three new tables: an append-only
-- points ledger (loyalty_transactions) backing a per-(tenant, customer) balance (loyalty_accounts),
-- plus an admin-defined tier-gated reward catalog (loyalty_rewards). Tier is deliberately NOT a
-- column anywhere here — it's computed on read from loyalty_accounts.total_points against the
-- tenant's RestaurantSettings thresholds, so an admin changing a threshold never leaves a stale
-- tier stamped on an existing row.
--
-- user_id stores users.id directly (varchar(255)), same no-@ManyToOne-to-User convention as
-- payments.processed_by / cash_shifts.opened_by (see V7__cash_shifts.sql) — avoids the LAZY
-- restaurantId association hazard.

CREATE TABLE IF NOT EXISTS loyalty_accounts (
    id            bigserial PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    user_id       varchar(255) NOT NULL,
    total_points  integer NOT NULL DEFAULT 0,
    created_at    timestamp NOT NULL,
    CONSTRAINT uk_loyalty_accounts_tenant_user UNIQUE (tenant_id, user_id)
);

CREATE TABLE IF NOT EXISTS loyalty_transactions (
    id                  bigserial PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    loyalty_account_id  bigint NOT NULL REFERENCES loyalty_accounts(id),
    points              integer NOT NULL,
    reason              varchar(255) NOT NULL,
    bill_id             bigint NOT NULL,
    created_at          timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_loyalty_transactions_account ON loyalty_transactions (loyalty_account_id);

CREATE TABLE IF NOT EXISTS loyalty_rewards (
    id             bigserial PRIMARY KEY,
    tenant_id      uuid NOT NULL,
    name           varchar(255) NOT NULL,
    description    varchar(255),
    required_tier  varchar(10) NOT NULL,
    active         boolean NOT NULL DEFAULT true,
    created_at     timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_loyalty_rewards_tenant ON loyalty_rewards (tenant_id);
