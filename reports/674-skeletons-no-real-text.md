# Report 674 — SKELETONS-NO-REAL-TEXT

## 1. Identification
- Report: 674
- Task ID: SKELETONS-NO-REAL-TEXT
- Predecessor: 673 (TABLE-DETAIL-SKELETON)

## 2. Objective
Some loading states still showed real text (page and card titles, legends, the KDS header). A skeleton looks more consistent when everything is a grey placeholder block, like the button placeholders.

## 3. Modified Files
- New: `frontend/src/pages/waiter/components/FloorHeaderSkeleton.tsx`
- Modified: `frontend/src/pages/waiter/Tables.tsx`, `frontend/src/pages/waiter/components/TableInformationSkeleton.tsx`, `frontend/src/pages/kitchen/components/KdsSkeleton.tsx`, `frontend/src/pages/kitchen/OrdersDisplay.tsx`, `frontend/src/pages/accountant/cashRegister/CashRegister.tsx`; tests `Tables.test.tsx`, `TableInformation.loading.test.tsx`, `loadingStates.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- Waiter floor: the header (title + occupied/free legend) is `FloorHeaderSkeleton`; the drag hint is hidden while loading; the right panel's "Detalles de mesa" title is a block.
- Waiter table detail: the four card titles are blocks (no `CardTitle` text, no translation hook in the skeleton).
- KDS: `KdsHeaderSkeleton` (brand title, subtitle and connection-badge blocks) replaces the real header while loading; the real header comes back with the data.
- Accountant cash register: the page title is a block.
- Rule now followed by these skeletons: a loading state shows no real text; the screen-reader `role="status"` announcement stays.

## 5. Why It Changed?
User feedback: text inside skeletons breaks the effect; placeholder blocks with a background are better.

### Verification
- The tests were changed first to forbid the real texts and the placeholders' test ids; 5 failed. Then `pnpm exec vitest run src/pages/waiter src/pages/loadingStates.test.tsx src/pages/kitchen` 108/108; `pnpm run build` exit 0; `pnpm run lint` 0 errors.

### NOT verified
- On-screen look. Still showing real text while loading, because those headers live in the real component and only the data zone is a skeleton: the card titles of the analytics blocks (sales chart, products, tables), the Staff header/filters, and the Settings sidebar. They were left as navigation/page chrome; they can be turned into placeholders too if the user wants.
