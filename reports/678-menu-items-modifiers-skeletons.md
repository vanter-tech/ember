# Report 678 — MENU-ITEMS-MODIFIERS-SKELETONS

## 1. Identification
- Report: 678
- Task ID: MENU-ITEMS-MODIFIERS-SKELETONS
- Predecessor: 677 (INVENTORY-CATEGORIES-SKELETONS)

## 2. Objective
Make the menu items (dishes) and modifier groups loading states mirror their real cards.

## 3. Modified Files
- Modified: `frontend/src/pages/admin/components/AdminListSkeletons.tsx`, `frontend/src/pages/admin/ListMenuItem.tsx`, `frontend/src/pages/admin/ModifierGroups.tsx`, test `frontend/src/pages/admin/AdminViews.loading.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- **Menu items:** each placeholder card has the real structure: image area with the status chip, name with the edit and delete round buttons, a description line, two modifier-group chips, and in the footer the big price and the availability switch. Same 1/2/3-column grid, 6 cards.
- **Modifier groups:** each card has the name with its edit button, the selection-type chip and three option chips, in the real 1/2-column grid; 8 cards (before: 4 plain blocks) so the window is filled.
- No real text; the announcement stays a screen-reader `role="status"`.
- The generic "placeholder cards" test had no views left once all four admin list views got their own tests, so it was removed; the four structure tests replace it.

## 5. Why It Changed?
Same standards as the other views: the skeleton carries the real card's frame so nothing jumps when data arrives.

### Verification
- New tests failed first. `pnpm exec vitest run src/pages/admin` 79/79; `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings); full suite 369/370 (only the known `MenuJoin` failure).

### NOT verified
- Not measured in the running app: heights are estimated from the code (real cards vary with description length and number of chips).
