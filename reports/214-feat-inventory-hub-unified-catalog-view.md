# Report 214: Unify Categorías/Modificadores/Inventario into one "Catálogo e Inventario" hub

**Predecessor:** report 213

## Objective
User request: merge the 3 separate admin pages (Categorías, Modificadores, Inventario) into one
view with a collapsible sidebar, matching the pattern already used by the Caja page
(`CashRegisterBar.tsx`). This precedes a separate follow-up initiative (per-section tutorials) the
user explicitly deferred until after this restructuring lands.

## Modified Files
- `frontend/src/pages/admin/inventoryHub/InventoryHub.tsx` (new)
- `frontend/src/pages/admin/inventoryHub/components/InventoryHubBar.tsx` (new)
- `frontend/src/App.tsx`
- `frontend/src/components/TopNav.tsx`
- `frontend/src/components/FloatingNav.tsx`
- `frontend/src/store/uiStore.ts`
- `frontend/src/pages/admin/Category.tsx`
- `frontend/src/pages/admin/ModifierGroups.tsx`
- `frontend/src/pages/admin/Inventory.tsx`
- `frontend/src/locales/es/admin.ts`, `frontend/src/locales/en/admin.ts`
- `frontend/src/locales/es/common.ts`, `frontend/src/locales/en/common.ts`

## What Changed?
Two architecture questions were resolved with the user up front (`AskUserQuestion`) rather than
guessed: (1) sections are real nested routes, not local `useState` like `CashRegisterBar` — deep
links and the browser back button work per-section; (2) `TopNav`'s "+" button reads the active
section from a shared store instead of `path.includes(...)`, since three routes now share one
`TopNav` mount.

New `InventoryHub.tsx` (parent route element) renders a shared header, `InventoryHubBar.tsx`
(sidebar, visually identical to `CashRegisterBar.tsx` — collapsible, `NavLink`-based, same
`PanelLeftClose`/`PanelLeftOpen` toggle), and `<Outlet/>`. It computes the active section via 3
`useMatch` calls and pushes it into `useUIStore`'s new `activeInventoryHubSection` field via a
`useEffect` (cleared on unmount).

Routes restructured in `App.tsx`: `/admin/categories`, `/admin/categories/:id/items`, and
`/admin/modifier-groups` are gone; their content now lives under `/admin/inventory` as
`InventoryHub`'s children — index (`Inventory`), `categories` (`Category`),
`categories/:id/items` (`ListMenuItem`, unchanged — `Category.tsx`'s existing relative `Link`
resolves correctly under the new parent), `modifiers` (`ModifierGroups`). `TopNav.tsx`'s
`isMenuItemRoute` match and its create-button branches (added in report 213) now key off
`activeInventoryHubSection` instead of `path.includes()` for these three cases.
`FloatingNav.tsx` collapses its 3 separate icons into 1 (`Warehouse`, linking to `/admin/inventory`
— `isActive` still highlights for every sub-route since it's a `pathname.includes()` check).
`Category.tsx`/`ModifierGroups.tsx`/`Inventory.tsx` each lost their own `p-6` wrapper padding and
(the latter two) their now-redundant local `<h1>` — matches `ShiftHistoryTable.tsx`/
`DailyZReportPanel.tsx`'s convention of leaving page-level chrome to the parent hub. Removed
`common.navCategories`/`navModifiers` (dead after the icon consolidation). Added
`admin.inventoryHubTitle`/`inventoryHubSubtitle`/`catalogLabel`/`categoriesTitle` (the last because
`Category.tsx` never had a page title of its own to reuse).

## Why It Changed?
Direct user request to reduce 3 separate admin nav destinations down to one, mirroring an existing,
already-approved UI pattern (Caja's sidebar) rather than inventing a new one.

## Verification
`cd frontend && pnpm run test:run` — 25/25 PASS (no route-dependent tests existed for the old
paths).
`cd frontend && pnpm run build` — PASS.
`pnpm run lint` still shows the ~15 pre-existing repo-wide issues (PROGRESS.md, unrelated to this
change) — none in the files this task touched.
