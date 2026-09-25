# Report 569 — MODIFIER-GROUPS-TAB-COLORS

## 1. Identification
- **Report number:** 569
- **Task ID:** MODIFIER-GROUPS-TAB-COLORS
- **Predecessor:** report 568 (MENU-ITEM-MODIFIER-BADGES)

## 2. Objective
Color the modifier-group cards in their own inventory sub-tab, consistent with the picker and the dish cards.

## 3. Modified Files
- `frontend/src/pages/admin/ModifierGroups.tsx`

## 4. What Changed?
- Each card uses the group's color (`colorForGroup`, hover styles stripped as the elements are static): pastel fill + border for the card, the type badge and the option chips.
- The type badge now shows the translated label (existing `selectionType*` keys) instead of the raw enum (`SINGLE_REQUIRED`).
- Options went from a comma-joined text to chips (inactive options hidden) with the price extra appended when > 0 (e.g. `Mediano +$1.5`).

## 5. Why It Changed?
Requested color parity with the rest of the modifier UI; the raw enum and the comma list also read poorly.

Verification: `pnpm run build` clean, `lint` 0 errors, `test:run` 224/225; the 1 failure is the pre-existing `MenuJoin.test.tsx` (see r550). Not checked visually in a browser; no new test (existing `ModifierGroups.test.tsx` still passes).
