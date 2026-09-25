# Report 590 — ACCOUNTANT-CASH-REGISTER-OVERVIEW

## 1. Identification
- **Report number:** 590
- **Task ID:** ACCOUNTANT-CASH-REGISTER-OVERVIEW
- **Predecessor:** report 589 (ADD-ITEM-MODAL-RESPONSIVE)

## 2. Objective
Space out the three cards of the accountant cash-register view and give the accountant the information needed to run the shift.

## 3. Modified Files
- `frontend/src/pages/accountant/cashRegister/CashRegister.tsx`
- `frontend/src/pages/accountant/cashRegister/CashRegister.test.tsx` (new)
- `frontend/src/locales/es/waiter.ts`, `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
The cards sat inside `#waiter-cashregister-tour-content`, a plain div, so the parent's `gap-8` did not apply; it is now `flex flex-col gap-6`. Shift summary: opened-by/at, deadline with time left (refreshed every 30 s), "Vencido" badge when overdue, and tiles for opening float, cash sales, digital sales, cash in/out and the **expected cash in register**. Those figures are computed client-side from the shift detail using the backend's close formula (float + confirmed PHYSICAL payments + cash in − cash out; refunds not netted, PENDING ignored), because the backend only fills `expectedCash`/totals at close. Movements gain a time column and a totals line; payments gain time, table, method and status badges, refunds in red, and a total/refunded line.

## 5. Why It Changed?
The accountant could not see the running cash position or when/where each payment came from. Build clean, lint 0 errors, accountant+waiter tests 48/48 (1 new). Not visually verified; the expected-cash figure is an estimate until close.
