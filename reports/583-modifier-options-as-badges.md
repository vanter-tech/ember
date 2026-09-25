# Report 583 — MODIFIER-OPTIONS-AS-BADGES

## 1. Identification
- **Report number:** 583
- **Task ID:** MODIFIER-OPTIONS-AS-BADGES
- **Predecessor:** report 582 (KITCHEN-FOCUSED-CARD-STATUS-COLUMNS)

## 2. Objective
Modifier options in the "add dish" pickers were radio/checkbox lists; show them as badges like the modifier-group badges of the dish form.

## 3. Modified Files
- `frontend/src/components/ModifierOptionBadges.tsx` (new)
- `frontend/src/pages/waiter/components/AddItemModal.tsx`
- `frontend/src/pages/customer/components/SelectModifiersModal.tsx`
- `frontend/src/pages/waiter/components/AddItemModal.test.tsx`

## 4. What Changed?
New shared `ModifierOptionBadges`: rounded pill per option with the group color dot, `+$x.xx` extra, brand-red border/tint and check when selected. Single-required groups render as `radiogroup`/`radio`, others as `aria-pressed` buttons. Selection logic (single replace, multi with max) unchanged. Both the waiter and customer modals use it; the waiter test clicks the option by text.

## 5. Why It Changed?
Visual consistency with the admin group badges and easier touch targets. Build clean, lint 0 errors, waiter+customer tests 55/56 (pre-existing `MenuJoin` failure). Not visually verified.
