# Report 672 — WAITER-TABLES-SKELETON

## 1. Identification
- Report: 672
- Task ID: WAITER-TABLES-SKELETON
- Predecessor: 671 (KDS-SKELETON)

## 2. Objective
The `waiter/tables` skeleton from report 670 showed only 6 placeholder tables that did not fill the window, and the right-hand panel was missing (just the "select a table" prompt). Fill the window and show the panel skeleton as it looks with a table selected.

## 3. Modified Files
- New: `frontend/src/pages/waiter/lib/tableSkeletonCount.ts` (+ test), `frontend/src/pages/waiter/components/TableDetailsSkeleton.tsx`
- Modified: `frontend/src/pages/waiter/Tables.tsx`, `frontend/src/pages/waiter/Tables.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- `tableSkeletonCount(width, height)` returns how many placeholder cards fill the window: 3 columns from 640px (2 below), rows = ceil((height - 160) / 176) clamped to 4..8. `Tables` computes it once on mount from the window size, so a taller/wider window gets more placeholders (15 on a 1401x990 window, 12 at 1024x768).
- `TableDetailsSkeleton` mirrors the right panel of a selected table: big table number and waiter on top, status row, three order lines, three stacked buttons. While the floor loads the panel shows the real "Detalles de mesa" title plus this skeleton instead of the "Selecciona una mesa…" prompt; once loaded, the prompt returns until a table is picked, as before.

## 5. Why It Changed?
A skeleton should have the final frame of the page: a floor that fills the window and both columns, so nothing jumps when the data arrives.

### Verification
- Tests failed first; `pnpm exec vitest run src/pages/waiter` 84/84; `pnpm run build` exit 0; `pnpm run lint` 0 errors.
- Measured in the running app (window 1401x990, dashboard request held pending): 15 placeholder tables in 3 columns reaching y=1042 (past the 990 bottom), the panel skeleton present in the right column (362x494), the "select a table" prompt not shown.

### NOT verified
- The panel skeleton's height against a real selected-table panel (it depends on how many order lines the table has) and how it looks on screen; the window size is read only on mount (not on resize).
