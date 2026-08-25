-- Basic inventory (EMB-INV, 2026-08-22 design spec): opt-in per-MenuItem stock
-- counter. No InventoryItem row for a menu item means it is untracked, never
-- decremented, never auto-86'd. current_stock/low_stock_threshold are
-- numeric(10,3) to support fractional units (kg, L), not just whole "unidades".
-- No FK to menu_items(id) — same plain-reference convention as
-- menu_item_modifier_groups.menu_item_id.

CREATE TABLE IF NOT EXISTS inventory_items (
    id                  bigserial PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    menu_item_id        bigint NOT NULL,
    unit                varchar(20) NOT NULL,
    current_stock       numeric(10,3) NOT NULL DEFAULT 0,
    low_stock_threshold numeric(10,3) NOT NULL DEFAULT 0,
    updated_at          timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uk_inventory_items_menu_item UNIQUE (menu_item_id)
);

CREATE INDEX IF NOT EXISTS idx_inventory_items_tenant ON inventory_items (tenant_id);
