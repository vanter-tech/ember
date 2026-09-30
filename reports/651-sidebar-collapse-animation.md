# Report 651 — SIDEBAR-COLLAPSE-ANIMATION

## 1. Identification
- Report: 651
- Task ID: SIDEBAR-COLLAPSE-ANIMATION
- Predecessor: 650 (ADMIN-MENU-ITEMS-CARD-POLISH)

## 2. Objective
Animate the admin sidebars when collapsing/expanding instead of disappearing abruptly.

## 3. Modified Files
- `frontend/src/pages/admin/Settings.tsx`
- `frontend/src/pages/admin/inventoryHub/InventoryHub.tsx`
- `frontend/src/pages/admin/cashRegister/CashRegister.tsx`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/pages/admin/inventoryHub/components/InventoryHubBar.tsx`
- `frontend/src/pages/admin/cashRegister/components/CashRegisterBar.tsx`

## 4. What Changed?
Wrapper width `md:w-fit` → fixed `md:w-10` (collapsed) / `md:w-64` with `md:transition-[width] duration-300 ease-in-out md:overflow-hidden motion-reduce:transition-none`. Each `<nav>` is now `w-full`, keyed on `collapsed`, with `animate-in fade-in duration-300` so the swapped content fades in.

## 5. Why It Changed?
`w-fit` cannot be transitioned by CSS and the content swap (labels list vs icon column) was instant, so the sidebar seemed to vanish. A fixed collapsed width (matches the `size-10` icon buttons) makes the width animatable. Label/icon content still switches via fade, not a continuous morph.

Verification: `pnpm run build` clean, lint 0 errors, related tests 6/6. Not checked visually in a browser.
