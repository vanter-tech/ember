# Report 671 — KDS-SKELETON

## 1. Identification
- Report: 671
- Task ID: KDS-SKELETON
- Predecessor: 670 (LOADING-SKELETONS)

## 2. Objective
The kitchen display (KDS) skeleton from report 670 was a single row of plain boxes; it did not look like the real board, which has small order cards and the big focused card. Make the loading state mirror the real layout.

## 3. Modified Files
- New: `frontend/src/pages/kitchen/components/KdsSkeleton.tsx`
- Modified: `frontend/src/pages/kitchen/OrdersDisplay.tsx`, `frontend/src/pages/loadingStates.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- `OrdersDisplay` extracts the screen header (title, language switcher, connection badge) into a `header` const, since it does not depend on the orders: it is now drawn from the first frame in the loading state too, instead of being replaced by a lone block.
- `KdsSkeleton` draws the board below the header the way it really is: a row of 3 small queue cards (300px high, like `QueueCard`: table / ticket / elapsed time on the left, three dishes with badge and action button on the right) and, below, the focused card (`FocusedCard`: heading, ticket and entry time, action buttons, and its three status columns with two dish rows each). The loading text stays as a screen-reader `role="status"`.
- The generic `CardGridSkeleton` is no longer used by the KDS.

## 5. Why It Changed?
A skeleton is only useful if it has the shape of what replaces it; the single grid of boxes gave no hint of the queue + focused-card board and shifted the layout when the orders arrived.

### Verification
- The rewritten test (header visible, ≥2 queue-card placeholders, 1 focused-card placeholder, 3 status columns, status text) failed first. `pnpm exec vitest run src/pages/loadingStates.test.tsx src/pages/kitchen` 22/22; `pnpm run build` exit 0; `pnpm run lint` 0 errors.

### NOT verified
- How it looks on screen and whether the placeholder heights/widths (`h-[300px]`, `w-[420px]`) match the real cards pixel for pixel: the dev database has no kitchen orders to compare against, and the browser used for checks was hidden.
