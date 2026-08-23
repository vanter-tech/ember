-- Product modifiers (EMB-MOD, 2026-08-22 design spec): reusable modifier groups
-- (size, doneness, extras) attached to menu items via an explicit join table that
-- carries display_order. Options only ever add to price (price_delta >= 0). Rows
-- are soft-deactivated (active=false), never deleted, so past orders' snapshots
-- in OrderItem/KitchenItem are never affected by a later catalog edit.

CREATE TABLE IF NOT EXISTS modifier_groups (
    id               bigserial PRIMARY KEY,
    tenant_id        uuid NOT NULL,
    name             varchar(100) NOT NULL,
    selection_type   varchar(20) NOT NULL,
    min_selections   integer NOT NULL,
    max_selections   integer,
    active           boolean NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS idx_modifier_groups_tenant ON modifier_groups (tenant_id);

CREATE TABLE IF NOT EXISTS modifier_options (
    id             bigserial PRIMARY KEY,
    group_id       bigint NOT NULL REFERENCES modifier_groups(id),
    name           varchar(100) NOT NULL,
    price_delta    numeric(10,2) NOT NULL DEFAULT 0,
    active         boolean NOT NULL DEFAULT true,
    display_order  integer NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_modifier_options_group ON modifier_options (group_id);

CREATE TABLE IF NOT EXISTS menu_item_modifier_groups (
    id             bigserial PRIMARY KEY,
    menu_item_id   bigint NOT NULL REFERENCES menu_items(id),
    group_id       bigint NOT NULL REFERENCES modifier_groups(id),
    display_order  integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_menu_item_modifier_groups UNIQUE (menu_item_id, group_id)
);

CREATE INDEX IF NOT EXISTS idx_menu_item_modifier_groups_item ON menu_item_modifier_groups (menu_item_id);
