# Report 591 — ACCOUNTANT-REMOVE-DIGITAL-SALES

## 1. Identification
- **Report number:** 591
- **Task ID:** ACCOUNTANT-REMOVE-DIGITAL-SALES
- **Predecessor:** report 590 (ACCOUNTANT-CASH-REGISTER-OVERVIEW)

## 2. Objective
Remove the "Ventas digitales" tile from the accountant shift summary; digital payments are not live yet.

## 3. Modified Files
- `frontend/src/pages/accountant/cashRegister/CashRegister.tsx`
- `frontend/src/locales/es/waiter.ts`, `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
Deleted the tile, the `digitalSales` computation and the `digitalSalesLabel` key (waiter namespace only; the admin namespace key used by the Z report is untouched). Summary grid is `lg:grid-cols-4`; the payments total line now sums cash only. The method/status columns in the payments table stay.

## 5. Why It Changed?
Digital payments are still a stub, so the tile would always show $0. Build clean, lint 0 errors, accountant tests 9/9.
