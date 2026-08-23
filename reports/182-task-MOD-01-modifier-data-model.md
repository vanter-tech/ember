# Report 182 — Task MOD-01: Product modifier data model & migration

**Predecessor:** Report 181 (EMB-i18N-08, validation/toast copy)

## Objective

Lay the Postgres/JPA data model for EMB-MOD (product modifiers): reusable modifier
groups (e.g. "Término de cocción", "Extras") with a fixed selection type
(`SINGLE_REQUIRED`/`MULTI_OPTIONAL`/`MULTI_LIMITED`), their options (each with a
non-negative price delta), and an explicit join entity attaching groups to menu
items with a display order. This is the first of six tasks (MOD-01..06) from the
approved spec `docs/superpowers/specs/2026-08-22-emb-mod-design.md` and plan
`docs/superpowers/plans/2026-08-22-emb-mod.md`.

## Modified Files

- `backend/src/main/resources/db/migration/V13__modifiers.sql` (new)
- `backend/src/main/java/com/vanter/ember/catalog/model/SelectionType.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/model/ModifierGroup.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/model/ModifierOption.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/model/MenuItemModifierGroup.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/repository/ModifierGroupRepository.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/repository/ModifierOptionRepository.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/repository/MenuItemModifierGroupRepository.java` (new)
- `backend/src/test/java/com/vanter/ember/catalog/repository/ModifierGroupRepositoryTenantIsolationTest.java` (new)

## What Changed?

Added `V13__modifiers.sql` (V12 was the last applied migration, `printing`),
creating three tables: `modifier_groups` (tenant-scoped, `selection_type` +
`min_selections`/`max_selections` columns), `modifier_options` (FK to
`modifier_groups`, `price_delta numeric(10,2) DEFAULT 0`, `active`,
`display_order`), and `menu_item_modifier_groups` (plain `menu_item_id`/`group_id`
columns with a real FK to each, unique constraint on the pair, `display_order`).

`ModifierGroup` carries `@TenantId` exactly like `Category`/`MenuItem`.
`ModifierOption` uses a real `@ManyToOne` to `ModifierGroup` (no lazy-loading
hazard, same reasoning as `PrinterConfig.agent`). `MenuItemModifierGroup` uses
plain `Long` columns for both sides rather than JPA associations, matching
`PrintJob.sourceId`'s "plain reference" convention — neither side needs the
object graph, just IDs plus `displayOrder`.

Added a 3-test `ModifierGroupRepositoryTenantIsolationTest` extending the
existing `AbstractTenantIsolationTest` (same pattern as
`CategoryRepositoryTenantIsolationTest`): save stamps the bound tenant,
`findAll` is tenant-scoped, `findById` doesn't leak across tenants.

## Why It Changed?

This is Task 1 of the approved EMB-MOD implementation plan — the SaaS
feature-gap backlog item for product modifiers (size, doneness, extras), needed
before menu items can carry per-order customization with a price adjustment.
No frontend or session/cart/KDS changes are in scope for this task; those are
MOD-02 through MOD-06.

## Verification

- `./mvnw test -Dtest=ModifierGroupRepositoryTenantIsolationTest` — 3/3 pass.
- `./mvnw test` (full backend suite) — all green, no regressions from the new
  entities/migration.
