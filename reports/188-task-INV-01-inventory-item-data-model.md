# Report 188

## 1. Identification
- **Report:** 188
- **Task ID:** INV-01
- **Predecessor Task:** MOD-06 (report 187)

## 2. Objective
Add the foundational `InventoryItem` data model for the EMB-INV (basic inventory) backlog: an opt-in, per-`MenuItem` stock counter (current stock, unit, low-stock threshold) with a single atomic, tenant-scoped write path that every later INV task (decrement on kitchen confirmation, admin restock/create) will reuse to change stock.

## 3. Modified Files
- `backend/src/main/resources/db/migration/V14__inventory.sql`
- `backend/src/main/java/com/vanter/ember/inventory/model/InventoryItem.java`
- `backend/src/main/java/com/vanter/ember/inventory/repository/InventoryItemRepository.java`
- `backend/src/test/java/com/vanter/ember/inventory/repository/InventoryItemRepositoryTenantIsolationTest.java`

## 4. What Changed?
- New `inventory_items` table (`V14__inventory.sql`, next sequential migration after `V13__modifiers.sql`): `tenant_id`, `menu_item_id` (unique, plain reference column — no FK to `menu_items(id)`, same convention as `menu_item_modifier_groups.menu_item_id`), `unit`, `current_stock`/`low_stock_threshold` (`numeric(10,3)` to support fractional units like kg/L), `updated_at`.
- New `InventoryItem` JPA entity, `@TenantId`-scoped (Hibernate discriminator filter) same as `Category`/`MenuItem`/`ModifierGroup`.
- New `InventoryItemRepository` with `findByMenuItemId` and one atomic write method, `applyClampedDelta(Long id, BigDecimal delta, LocalDateTime now)` — a single `@Modifying` JPQL `UPDATE` that clamps `current_stock` at zero via a SQL `CASE WHEN ... ELSE 0 END` expression, so stock can never go negative and no `@Version`/optimistic locking is needed under concurrent writers.
- New `InventoryItemRepositoryTenantIsolationTest` (4 tests): tenant stamping on save, no cross-tenant leak on `findByMenuItemId`, clamping never goes negative, and — added in a review fix round — an explicit cross-tenant protection test for `applyClampedDelta` itself.

## 5. Why It Changed?
This is Task 1 of `docs/superpowers/plans/2026-08-22-emb-inv.md`, implementing `docs/superpowers/specs/2026-08-22-emb-inv-design.md` §3.1. It lands only the data model and its one write primitive — no service, listener, or API yet — so later tasks (INV-02's kitchen-confirmation decrement, INV-04's restock/create endpoints) have a single, already-verified-safe place to change stock rather than each reimplementing clamping/tenant-scoping.

During task review, the reviewer flagged that this repo has zero prior `@Modifying` bulk-update methods, so whether Hibernate's `@TenantId` filter actually restricts a bulk JPQL `UPDATE` (vs. only `SELECT`/single-entity CRUD) was an unverified assumption — a real risk given every later task's stock-changing write goes through this one method. Rather than trust Hibernate's documented behavior, a fix round added an empirical test (`applyClampedDelta_isTenantScoped`): tenant B attempts the update against tenant A's row id, and tenant A's stock is asserted unchanged afterward. The captured Hibernate SQL log confirms the generated statement includes `and ii1_0.tenant_id = ?` on the bulk update — cross-tenant protection is real, not assumed.

## Verification
- `cd backend && ./mvnw test -Dtest=InventoryItemRepositoryTenantIsolationTest` → 4/4 passed, pristine output.
- `cd backend && ./mvnw test` (full suite) → 775/775 passed (up from 771; +4 for this task's new test class).
