# Report 568 — MENU-ITEM-MODIFIER-BADGES

## 1. Identification
- **Report number:** 568
- **Task ID:** MENU-ITEM-MODIFIER-BADGES
- **Predecessor:** report 567 (MODIFIER-GROUP-BADGE-COLORS)

## 2. Objective
Show the modifier groups assigned to a dish on its card in the admin dish list.

## 3. Modified Files
- `frontend/src/lib/modifierGroupColors.ts` (new, extracted)
- `frontend/src/pages/admin/components/ModifierGroupAssignmentField.tsx`
- `frontend/src/pages/admin/ListMenuItem.tsx`
- `frontend/src/pages/admin/ListMenuItem.test.tsx`

## 4. What Changed?
- The color palette and `colorForGroup` moved from the picker to `lib/modifierGroupColors.ts` so the picker and the list share one color per group.
- `ListMenuItem` renders, under the description, a badge per assigned group in that group's color, with the selection type as its `title`; nothing renders when the dish has no groups. The list endpoint already returns `modifierGroups`, and the create/edit modals already invalidate `['menuItems']`, so no backend or modal change was needed.
- Two tests: badges appear when groups exist; none when the array is empty.

## 5. Why It Changed?
After assigning modifier groups there was no visible sign of them on the dish; the admin had to reopen the edit modal to check.

Verification: `pnpm run build` clean, `lint` 0 errors, `test:run` 224/225 (+2 new pass); the 1 failure is the pre-existing `MenuJoin.test.tsx` (see r550). Not checked visually in a browser.
