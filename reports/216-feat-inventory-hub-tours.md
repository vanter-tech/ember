# Report 216: Tours for Categorías/Modificadores/Inventario

**Predecessor:** report 215

## Objective
First content rollout on top of the new `SectionTour` system: replayable tutorials for the 3
sections of the "Catálogo e Inventario" hub built in report 214.

## Modified Files
- `frontend/src/pages/admin/inventoryHub/components/InventoryHubBar.tsx`
- `frontend/src/pages/admin/Category.tsx`
- `frontend/src/pages/admin/ModifierGroups.tsx`
- `frontend/src/pages/admin/Inventory.tsx`
- `frontend/src/locales/es/admin.ts`, `frontend/src/locales/en/admin.ts`

## What Changed?
Added stable `id`s: `inventory-hub-sidebar` (the hub's sidebar `<nav>`), `category-tour-grid`,
`modifiers-tour-grid`, `inventory-tour-grid` (each section's card grid), and
`topnav-create-button` (TopNav's own "+" button, reused by all 3 tours' final step instead of each
page inventing its own). Each of the 3 section pages now renders a `<SectionTour>`:
`admin-inventory-categories` (2 steps: grid, create), `admin-inventory-modifiers` (2 steps: grid,
create), `admin-inventory-stock` (3 steps: sidebar overview, grid, create) — stock's extra first
step explains the hub's own sidebar since `/admin/inventory` (the index route) is most new admins'
first landing point in the hub.

## Why It Changed?
Direct continuation of the user's tutorial-rollout request, first section group.

## Verification
`cd frontend && pnpm run test:run` — 31/31 PASS.
`cd frontend && pnpm run build` — PASS.
