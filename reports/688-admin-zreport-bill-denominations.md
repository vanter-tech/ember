# Report 688 — ADMIN-ZREPORT-DENOMINATIONS

## 1. Identification
- **Report number:** 688
- **Task ID:** ADMIN-ZREPORT-DENOMINATIONS
- **Predecessor task:** report 687 — BILL-NUMBERING (this branch is stacked on `feat/bill-numbering`, which is not merged yet)

## 2. Objective
The admin cash-register "Corte Z" (daily close-out) listed each closed shift with expected / counted / variance only. The bill-by-bill count taken when the shift opened and closed, the opening float and the close notes were collected and returned by the backend but only visible in the shift history's expanded row.

## 3. Modified Files
- `frontend/src/pages/admin/cashRegister/components/ShiftBreakdownDetail.tsx` (new)
- `frontend/src/pages/admin/cashRegister/components/DailyZReportPanel.tsx`
- `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx`
- `frontend/src/pages/admin/cashRegister/components/DailyZReportPanel.test.tsx` (new)
- `frontend/src/locales/es/admin.ts`, `frontend/src/locales/en/admin.ts`

## 4. What Changed?
- New shared `ShiftBreakdownDetail`: opening breakdown, closing breakdown and close notes of a shift (the `denominationLabel` helper and the detail block moved out of `ShiftHistoryTable`). With an optional `emptyLabel` it says "this shift recorded no bill breakdown or notes" instead of rendering a blank row.
- `DailyZReportPanel`: new "Apertura" column (`openingFloat`) before expected/counted/variance; every shift row expands to show `ShiftBreakdownDetail`. No new request: `GET` daily report already returns `CashShiftResponse` with `openingBreakdown`, `closingBreakdown` and `closeNotes`.
- `ShiftHistoryTable`: same behavior, now using the shared component.
- Tests: opening float shown beside expected/counted; expanding shows both breakdowns (`$100.00 × 1`, `$100.00 × 3`) and notes; a shift without breakdown shows the empty message.

## 5. Why It Changed?
The count is the evidence behind the numbers of a close-out; showing only the totals made the Corte Z less auditable than the history view. No backend change was needed.

## Verification
`pnpm run build` exit 0, `pnpm run lint` 0 errors (15 pre-existing warnings), `src/pages/admin` vitest 88/88. Not opened in a browser.
