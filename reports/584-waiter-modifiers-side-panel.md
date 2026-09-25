# Report 584 — WAITER-MODIFIERS-SIDE-PANEL

## 1. Identification
- **Report number:** 584
- **Task ID:** WAITER-MODIFIERS-SIDE-PANEL
- **Predecessor:** report 583 (MODIFIER-OPTIONS-AS-BADGES)

## 2. Objective
In the waiter "add dish" modal, pick modifiers in a panel to the right of the dish card instead of replacing the whole modal content.

## 3. Modified Files
- `frontend/src/pages/waiter/components/AddItemModal.tsx`
- `frontend/src/pages/waiter/components/AddItemModal.test.tsx`

## 4. What Changed?
Each product card is a Radix `Popover` anchor; tapping "+" on an item with modifier groups opens a `PopoverContent` (side right, height = card height via `--radix-popover-trigger-height`, inner scroll) with the groups as `ModifierOptionBadges` plus Cancelar/Agregar. The `pendingItem ? … : …` full-modal swap was removed; selection logic is unchanged. Radix flips the panel to the left when there is no room (right-column cards). The test now asserts the panel appears.

## 5. Why It Changed?
The user found the in-place picker unconvincing; a side panel keeps the menu visible. Build clean, lint 0 errors, waiter tests 39/39. Not visually verified (panel height/flip behavior needs a look).
