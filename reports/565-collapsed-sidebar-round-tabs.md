# Report 565 — COLLAPSED-SIDEBAR-ROUND-TABS

## 1. Identification
- **Report number:** 565
- **Task ID:** COLLAPSED-SIDEBAR-ROUND-TABS
- **Predecessor:** report 564 (HIDE-EDGE-NATIVE-REVEAL)

## 2. Objective
When a sidebar is collapsed, the current tab's icon button must be a circle, not an oval.

## 3. Modified Files
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/pages/admin/cashRegister/components/CashRegisterBar.tsx`
- `frontend/src/pages/admin/inventoryHub/components/InventoryHubBar.tsx`

## 4. What Changed?
- Collapsed tab buttons use a fixed `size-10 shrink-0 justify-center rounded-full p-0` instead of `px-2`.

## 5. Why It Changed?
Width came from padding + icon (16px icon in `SettingsBar` → 32px wide vs 40px tall = oval), so shape depended on the icon size. A fixed square with `rounded-full` is always a circle. Expanded mode and the floating collapse toggle are unchanged.

Verification: `pnpm run build` clean, `lint` 0 errors, `test:run` 217/218 (the failure is the pre-existing `MenuJoin.test.tsx`, see r550). Not checked visually in a browser.
