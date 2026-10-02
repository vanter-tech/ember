# Report 675 — ACCOUNTANT-SKELETONS

## 1. Identification
- Report: 675
- Task ID: ACCOUNTANT-SKELETONS
- Predecessor: 674 (SKELETONS-NO-REAL-TEXT)

## 2. Objective
Give the accountant's two screens a loading state that mirrors the real page (no real text, placeholder blocks) and stop the cash receipts view from showing misleading "0", "$0.00" and "no pending payments" while its data loads.

## 3. Modified Files
- New: `frontend/src/components/skeletons/PageHeaderSkeleton.tsx`, `frontend/src/pages/accountant/cashRegister/components/CashRegisterSkeleton.tsx`, `frontend/src/pages/accountant/cashDrawer/CashReceiptsSkeleton.tsx`
- Modified: `frontend/src/pages/accountant/cashRegister/CashRegister.tsx`, `frontend/src/pages/accountant/CashReceipts.tsx`, `.../cashDrawer/CashReceiptsSummary.tsx`, `.../cashDrawer/PendingCashList.tsx`, `.../cashDrawer/ReceivedCashList.tsx`, `frontend/src/locales/{es,en}/waiter.ts` (`loadingCashReceipts`); tests `loadingStates.test.tsx`, `skeletons.test.tsx`, `CashReceipts.test.tsx`
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- **Cash register (Caja):** `CashRegisterSkeleton` draws the page the way it is: header (title + subtitle blocks), the current-shift card (label and status blocks, meta line, four stat tiles with the highlighted "expected cash" tile, three action buttons), and the movements (5 columns) and payments (8 columns) tables, all as placeholders. It replaces the earlier generic cards.
- **Cash receipts (Cobros en efectivo):** while the events query loads, the page header (with its round icon), the two summary tiles and three pending-payment cards are placeholders; the received list renders nothing yet. Before, the summary showed `0` and `$0.00` and the pending list showed the "no pending payments" empty state until the first answer arrived. The three components share the same deduplicated query. The loading announcement ("Cargando cobros en efectivo…") is a screen-reader `role="status"`.
- `PageHeaderSkeleton` (title + subtitle blocks, optional round icon) is shared and tested.

## 5. Why It Changed?
Same criteria as the other views: a skeleton must have the page's frame, no real text, and a loading state must never look like an empty or zero state.

### Verification
- New tests failed first (cash register frame; receipts placeholders and absence of the false empty state/zeros; page header). `pnpm exec vitest run src/pages/accountant src/pages/loadingStates.test.tsx src/components/skeletons` 42/42; `pnpm run build` exit 0; `pnpm run lint` 0 errors. The existing `CashReceipts` test now waits for the title (it appears after the data).

### NOT verified
- The pages were not opened in the running app: the accountant routes are only reachable with an ACCOUNTANT login (an admin gets 403), so geometry could not be measured; sizes are estimates from the code.
