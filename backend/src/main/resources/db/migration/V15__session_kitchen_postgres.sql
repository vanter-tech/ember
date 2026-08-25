-- Postgres unification (ember-postgress-migration, 2026-08-24): moves session/kitchen
-- off MongoDB onto Postgres so the whole backend runs on one persistence engine.
-- No data to carry over (dev-only, no production tenants) -- this is a fresh schema,
-- not a data migration.
--
-- participants/items/activity_log are native `jsonb`, unlike RestaurantSettings.payload
-- (a single JSON-mapped OBJECT field, compatible with a plain `text` column). These are
-- JSON-mapped LIST fields, and Hibernate's PostgreSQLDialect resolves @JdbcTypeCode(SqlTypes.JSON)
-- on a List to native `jsonb` -- confirmed empirically: booting with `text` here made
-- ddl-auto=update try `alter column ... set data type jsonb`, which Postgres refuses
-- (no implicit text->jsonb cast). Declaring jsonb here matches what Hibernate already
-- expects, so ddl-auto=update sees no mismatch. This is Postgres-only SQL; the H2 test
-- schema is generated straight from the entity mapping (Flyway is disabled in tests), so
-- it's unaffected either way -- no need to keep this in sync with H2's type support.
-- id is varchar(36), a UUID string generated in code (@PrePersist), replacing Mongo's
-- ObjectId hex string -- nothing else in the app parses the id format, so this is a safe
-- swap.

CREATE TABLE IF NOT EXISTS sessions (
    id                varchar(36) PRIMARY KEY,
    version           bigint NOT NULL DEFAULT 0,
    tenant_id         uuid NOT NULL,
    table_id          uuid NOT NULL,
    waiter_id         varchar(255),
    status            varchar(20) NOT NULL,
    max_participants  int NOT NULL,
    participants      jsonb NOT NULL DEFAULT '[]',
    items             jsonb NOT NULL DEFAULT '[]',
    activity_log      jsonb NOT NULL DEFAULT '[]',
    join_code         varchar(10),
    created_at        timestamp
);

CREATE INDEX IF NOT EXISTS idx_sessions_tenant_status ON sessions (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_sessions_tenant_table_status ON sessions (tenant_id, table_id, status);
CREATE INDEX IF NOT EXISTS idx_sessions_join_code_status ON sessions (join_code, status);

CREATE TABLE IF NOT EXISTS kitchen_orders (
    id            varchar(36) PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    session_id    varchar(36) NOT NULL,
    table_number  int NOT NULL,
    created_at    timestamp,
    items         jsonb NOT NULL DEFAULT '[]',
    active        boolean NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS idx_kitchen_orders_tenant ON kitchen_orders (tenant_id);
CREATE INDEX IF NOT EXISTS idx_kitchen_orders_tenant_active ON kitchen_orders (tenant_id, active);
CREATE INDEX IF NOT EXISTS idx_kitchen_orders_tenant_session ON kitchen_orders (tenant_id, session_id);
