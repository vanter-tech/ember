# Report 652 — ADMIN-MENU-ITEM-PRICE-BOLD

## 1. Identification
- Report: 652
- Task ID: ADMIN-MENU-ITEM-PRICE-BOLD
- Predecessor: 651 (SIDEBAR-COLLAPSE-ANIMATION)

## 2. Objective
Make the dish price stand out more in the admin items cards.

## 3. Modified Files
- `frontend/src/pages/admin/ListMenuItem.tsx`

## 4. What Changed?
Added `font-bold` to the price `CardTitle` in the card footer.

## 5. Why It Changed?
Visual emphasis: the price is the key figure on each card.

Verification: `pnpm run build` clean, lint 0 errors, `ListMenuItem.test.tsx` 4/4.
