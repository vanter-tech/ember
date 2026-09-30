# Report 649 — ADMIN-MENU-ITEMS-GRID-LAYOUT

## 1. Identification
- Report: 649
- Task ID: ADMIN-MENU-ITEMS-GRID-LAYOUT
- Predecessor: 648 (FIX-LANDING-NAV-CSS-FLASH)

## 2. Objective
Remove the large blank gap between dish title and action buttons in `admin/inventory/categories/:id/items`, matching the categories view.

## 3. Modified Files
- `frontend/src/pages/admin/ListMenuItem.tsx`

## 4. What Changed?
Dish cards went from full-width horizontal rows to vertical cards in a `grid-cols-1 md:grid-cols-2 lg:grid-cols-3` grid (the old `grid:cols-1` class was a typo and did nothing). Image + status badge on top; name with edit/delete buttons in the same row; description and modifier-group chips below; footer with price and availability switch. Disabled badge is now grey instead of green; images got `alt`.

## 5. Why It Changed?
Horizontal rows used `flex-1` for text and pushed price/actions to the far right edge, leaving a wide empty area on large screens. The layout now mirrors the categories grid for consistency.

Verification: `pnpm run build` clean, lint 0 errors (no warnings in this file), `ListMenuItem.test.tsx` 4/4.
