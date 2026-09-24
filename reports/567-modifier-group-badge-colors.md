# Report 567 — MODIFIER-GROUP-BADGE-COLORS

## 1. Identification
- **Report number:** 567
- **Task ID:** MODIFIER-GROUP-BADGE-COLORS
- **Predecessor:** report 566 (MODIFIER-GROUP-BADGES-GRID)

## 2. Objective
Give each modifier-group badge its own color instead of one red for all.

## 3. Modified Files
- `frontend/src/pages/admin/components/ModifierGroupAssignmentField.tsx`
- `frontend/src/pages/admin/components/ModifierGroupAssignmentField.test.tsx`

## 4. What Changed?
- 8-entry palette of literal Tailwind classes (rose, orange, amber, lime, emerald, sky, indigo, fuchsia), picked by `group.id % 8` so a group keeps its color everywhere.
- Unselected badge: pastel fill + border + dark text; selected: solid `-600` fill with white text and the check. The numbered order pills reuse the group's color. Neutral/`#8c1717` styling removed.
- Test: two different groups don't share the same badge classes.

## 5. Why It Changed?
Requested: the grid needed color to be easier to scan.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/admin/components` 16/16. Not checked visually in a browser. With more than 8 groups, colors repeat (id modulo 8).
