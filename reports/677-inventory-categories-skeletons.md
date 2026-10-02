# Report 677 — INVENTORY-CATEGORIES-SKELETONS

## 1. Identification
- Report: 677
- Task ID: INVENTORY-CATEGORIES-SKELETONS
- Predecessor: 676 (ANALYTICS-SKELETONS)

## 2. Objective
Make the Categories and Inventory (stock) loading states mirror their real cards instead of plain grey rectangles.

## 3. Modified Files
- New: `frontend/src/pages/admin/components/AdminListSkeletons.tsx`
- Modified: `frontend/src/pages/admin/Category.tsx`, `frontend/src/pages/admin/Inventory.tsx`, test `frontend/src/pages/admin/AdminViews.loading.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- **Categories:** each placeholder card has the real structure: image area (h-48), name with its two action buttons, two description lines, and the product-count chip in the footer. Same 1/2/3-column grid, 6 cards.
- **Inventory:** each placeholder card has the item name with its edit button and the stock line, in the real 1/2-column grid, 10 cards so the window is filled.
- No real text; the loading announcement stays a screen-reader `role="status"`. Both pages' headers belong to the admin layout (not these views), so nothing else changes.
- The generic "placeholder cards" loading test no longer lists these two views; they have their own, stricter tests (structure of each card, no headings).

## 5. Why It Changed?
Same standards as the other views: the skeleton shows the real card's frame so nothing jumps when the data arrives.

### Verification
- New tests failed first. `pnpm exec vitest run src/pages/admin` 79/79; `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings).

### NOT verified
- Not measured in the running app: card heights are estimated from the code (the real category card also depends on its description length).
