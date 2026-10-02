# Report 673 — TABLE-DETAIL-SKELETON

## 1. Identification
- Report: 673
- Task ID: TABLE-DETAIL-SKELETON
- Predecessor: 672 (WAITER-TABLES-SKELETON)

## 2. Objective
The loading state of the waiter's individual table view (report 670) was a generic list plus one block. Make it mirror the real page.

## 3. Modified Files
- New: `frontend/src/pages/waiter/components/TableInformationSkeleton.tsx`
- Modified: `frontend/src/pages/waiter/TableInformation.tsx`, `frontend/src/pages/waiter/TableInformation.loading.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
`TableInformationSkeleton` draws the page the way it is: the header (round back button, table number, status badge, waiter line, four round action buttons and the "add item" button) and the same 2/3 + 1/3 grid. On the left, the orders, participants and activity cards (3 order rows with checkbox, avatar, two lines, price and delete; two participant pills; a three-entry timeline); on the right, the bill summary card (subtotal, taxes, total and the big charge button). The card titles are static text, so they are the real translated ones ("Detalles de pedidos", "Participantes", "Actividad", "Resumen"); only the content is placeholders. The loading text is a screen-reader `role="status"`. `TableInformation` uses it in place of the earlier row list + single block.

## 5. Why It Changed?
A skeleton should have the frame of the page it stands in for, so the layout does not jump when the session arrives.

### Verification
- The rewritten loading tests (frame + four titles, placeholders per card, status) failed first; `pnpm exec vitest run src/pages/waiter` 86/86; `pnpm run build` exit 0; `pnpm run lint` 0 errors.
- Measured in the running app (window 1346x879, session requests held pending): header 104px high, left column 847px wide with three stacked cards (346 / 170 / 258 px) and the bill card 412px wide starting at the same top, page reaching y=982.

### NOT verified
- Pixel match with a real session (the dev database has no open session to compare against; heights depend on the number of order lines, participants and activity entries) and the on-screen look.
