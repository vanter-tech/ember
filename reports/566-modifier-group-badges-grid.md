# Report 566 — MODIFIER-GROUP-BADGES-GRID

## 1. Identification
- **Report number:** 566
- **Task ID:** MODIFIER-GROUP-BADGES-GRID
- **Predecessor:** report 565 (COLLAPSED-SIDEBAR-ROUND-TABS)

## 2. Objective
The modifier-group picker in the create/edit menu-item modals was a long unstructured checkbox list; show it as a badge grid instead.

## 3. Modified Files
- `frontend/src/pages/admin/components/ModifierGroupAssignmentField.tsx`
- `frontend/src/pages/admin/components/ModifierGroupAssignmentField.test.tsx` (new)
- `frontend/src/locales/{es,en}/admin.ts`

## 4. What Changed?
- Groups render as tappable badges in a 2/3-column grid (selected: `#8c1717` fill + check; unselected: neutral), each showing the selection type and option count (reusing the existing `selectionType*` labels).
- A name filter appears only when there are more than 8 active groups.
- The selected groups are shown as numbered pills (`1. Name`) with up/down buttons (disabled at the ends, translated aria-labels) instead of a second text list.
- New i18n keys: `searchModifierGroupsPlaceholder`, `modifierGroupOptionsCount`, `moveModifierGroupUpAria`, `moveModifierGroupDownAria`. Same props/contract, so `NewMenuModal`/`EditMenuModal` are untouched.
- Deliberately not done (discussed): per-category tagging of groups — needs a many-to-many model + Flyway migration; revisit if the list is still long.

## 5. Why It Changed?
A vertical checkbox list doesn't scale past a handful of groups; badges with a search box are scannable and cost no backend change.

Verification: `pnpm run build` clean, `lint` 0 errors, `test:run` 221/222 (+4 new, all pass); the 1 failure is the pre-existing `MenuJoin.test.tsx` (see r550). Not checked visually in a browser.
