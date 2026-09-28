# Report 629 — CUSTOMER-MENU-DESKTOP-GRID-LAST-ROW-FILL

## 1. Identification
- Report: 629
- Task ID: CUSTOMER-MENU-DESKTOP-GRID-LAST-ROW-FILL
- Predecessor: CUSTOMER-MENU-MOBILE-CATEGORY-DESCRIPTION (report 628)

## 2. Objective
The desktop 4-column category grid (below the hero) can leave a trailing row with only 3 of 4 columns filled, leaving one card sitting alone next to empty space — flagged with the current 7-category dev data, where the last row has 3 cards.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
- `CategoryCard` gains an optional `className` prop, merged via `cn` (tailwind-merge) onto its base classes.
- In the grid, `restCategories.length % 4 === 3` detects the "last row has exactly one empty slot" case; when true, the last card in the array gets `lg:col-span-2`, stretching it to fill that trailing gap instead of leaving it visually empty. Doesn't attempt to handle every possible remainder (1 or 2 empty slots) — just the concrete case reported.

## 5. Why It Changed?
Direct visual request: with the current 7 non-hero categories, the last row (Pollo, Postres, Sopas) left one card floating next to empty space; stretching the last one closes that gap cleanly.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- **Live-verified with `claude-in-chrome`**: "Sopas" now spans the full remaining width of the last row, no empty gap.
