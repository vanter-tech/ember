# Report 680 — ADMIN-CASH-REGISTER-SKELETONS

## 1. Identification
- Report: 680
- Task ID: ADMIN-CASH-REGISTER-SKELETONS
- Predecessor: 679 (STAFF-SKELETONS)

## 2. Objective
Make the admin cash register (shift history and daily Z report) mirror its real layout while loading, with no real text.

## 3. Modified Files
- New: `frontend/src/components/skeletons/TableSkeleton.tsx`, `frontend/src/pages/admin/cashRegister/components/{CashRegisterSkeletons.tsx,useShiftHistory.ts}`
- Modified: `frontend/src/pages/admin/cashRegister/CashRegister.tsx`, `.../components/{ShiftHistoryTable,DailyZReportPanel}.tsx`, `frontend/src/pages/accountant/cashRegister/components/CashRegisterSkeleton.tsx` (now uses the shared table), test `frontend/src/pages/loadingStates.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- **Page, first load:** the whole page is blocks until the first page of shift history arrives: title/subtitle, the manual-open button, the section sidebar (one block on mobile; the three nav buttons on desktop) and the history card. The page and the table share one query (`useShiftHistory`), so there is a single request.
- **History table:** its card holds a 7-column table of placeholders (header + 8 rows). The same skeleton is used when the history page changes.
- **Daily (Z) report:** the date picker stays a real input (it is data-independent, and swapping it for a block on each date change would drop its focus); below it, 5 figure cards (icon, label, value) and the shifts table (7 columns) are placeholders. Before: two bare rows.
- The table placeholder (header + rows, `skeleton-table` / `skeleton-table-row`) moved to `components/skeletons/TableSkeleton.tsx`, shared with the accountant cash register.
- Screen-reader announcements remain `role="status"`.

## 5. Why It Changed?
Same standards as the other views: real frame, no real text, no jump on arrival. The real page header and sidebar are text, so they are blocks while loading; they come back with the data.

### Verification
- New tests failed first (page, history table, Z panel). `pnpm exec vitest run src/pages/admin src/pages/accountant src/pages/loadingStates.test.tsx src/components/skeletons` 122/122; `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings); full suite 369/370 (only the known `MenuJoin` failure).

### NOT verified
- Not measured in the running app (sizes estimated from the code), and not run with the page tour. If history fails (error), the page shows its real header/sidebar with the error message in the content, as before.
