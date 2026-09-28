# Report 623 — CUSTOMER-MENU-SPARSE-CATEGORY-GRID

## 1. Identification
- Report: 623
- Task ID: CUSTOMER-MENU-SPARSE-CATEGORY-GRID
- Predecessor: CUSTOMER-MENU-LANGUAGE-FAB-RIGHT-ALIGN-FIX (report 622)

## 2. Objective
The desktop category grid (report 621) looks sparse/empty with few categories — the dev tenant has only one ("Pollo"), which claimed a `md:col-span-2 md:row-span-2` hero slot in a 4-column grid, leaving most of the row as dead white space. Asked whether this was a real gap or just dev data; flagged it as a real one and got approval to fix it.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
The desktop category grid now branches on `menuItems.length`:
- **3+ categories** (fills a row): unchanged — the original `grid-cols-1 md:grid-cols-4 lg:grid-cols-4` with the first two cards getting the hero span, `min-h-75`, same as before.
- **1-2 categories**: `grid-cols-1 sm:grid-cols-2 max-w-3xl` (no hero spans), cards sized `min-h-56` instead — modest, human-sized cards instead of one oversized hero card floating next to a wall of empty grid space.

## 5. Why It Changed?
Direct follow-up to being asked for an honest assessment of the desktop menu; confirmed this specific case (sparse categories) was a genuine visual gap independent of it being a dev environment, and was asked to fix it.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- **Live-verified with `claude-in-chrome`**: the single "Pollo" category now renders as a modest, appropriately-sized card instead of a giant hero card with empty space beside it; click-through into its items still works.
