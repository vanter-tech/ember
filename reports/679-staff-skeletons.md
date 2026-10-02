# Report 679 — STAFF-SKELETONS

## 1. Identification
- Report: 679
- Task ID: STAFF-SKELETONS
- Predecessor: 678 (MENU-ITEMS-MODIFIERS-SKELETONS)

## 2. Objective
Make the admin Staff (employees) loading state mirror the whole page and show no real text: before, the header and the role filters were real text and only a grid of grey rectangles stood for the cards.

## 3. Modified Files
- New: `frontend/src/pages/admin/staff/components/StaffSkeleton.tsx`
- Modified: `frontend/src/pages/admin/staff/Staff.tsx`, test `frontend/src/pages/loadingStates.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- `StaffSkeleton` draws the page: header (title + subtitle blocks), the 5 filter pills, 8 member cards (avatar, role chip, name and email, two detail chips, profile + deactivate buttons) in the real 1/2/3/4-column grid, and the 3 KPI cards (label + icon, big figure) that sit under the grid.
- `Staff.tsx` returns the skeleton while loading (after its hooks) and drops the old inline grid placeholder; the `!isLoading` guards and the tour's `ready` flag no longer need the loading term. The "add new role" dashed card is not drawn (it is a control, not content).
- The old test ("keeps its header and shows cards as placeholders") asserted the real header; it is replaced by a structure test with no real text.

## 5. Why It Changed?
Same standards as the other views: frame of the real page, no real text, nothing jumps when the data arrives (the KPIs used to appear below the grid only after loading).

### Verification
- New test failed first. `pnpm exec vitest run src/pages/admin src/pages/loadingStates.test.tsx` 87/87; `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings); full suite 369/370 (only the known `MenuJoin` failure).

### NOT verified
- Not measured in the running app; sizes are estimated from the code. The guided tour starts only after the page has loaded (it already waited for that), but I did not run the tour.
