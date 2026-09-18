# Report 491

## 1. Identification
- **Report number:** 491
- **Task ID:** CASH-SHIFT-DENOMINATION-COUNT Task 7 — `OpenShiftDialog` uses the counter
- **Predecessor task:** report 490 (Task 6 — `DenominationCounter` shared grid component)

## 2. Objective
Replace `OpenShiftDialog`'s single typed-amount input with the `DenominationCounter` grid from
Task 6, and wire `cashShiftService.open` to submit the counted breakdown alongside the total.

## 3. Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/pages/accountant/cashRegister/components/OpenShiftDialog.tsx`
- `frontend/src/pages/accountant/cashRegister/components/OpenShiftDialog.test.tsx` (new)
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
`cashShiftService.open` gains an optional `breakdown?: DenominationCount[]` second parameter,
forwarded in the POST body alongside `openingFloat`.

`OpenShiftDialog.tsx` drops its `zod`/`react-hook-form` single-amount form entirely and renders
`<DenominationCounter onChange={...}>` instead, tracking `breakdown`/`total` in local state and
submitting both via `cashShiftService.open(total, breakdown)` on button click.

Removed the now-orphaned `openingFloatNegativeError` i18n key (`es`/`en` `waiter.ts`) — it was the
zod validation message for the deleted typed-amount input; `DenominationCounter` already clamps
every row's quantity to `>= 0`, so a negative total can no longer occur and the message has no
remaining call site. `openingFloatLabel` (a different key, still used by `CashRegister.tsx`'s
summary card) was left untouched.

The test follows the plan's own snippet, adapted to the real `formatCurrency` output established in
Task 6 (`$100.00`, not `C$100.00`).

## 5. Why It Changed?
This is the first of the two dialogs (Task 8 does the other) that actually surfaces the denomination
grid to the accountant — closing the original bug report (one typed total instead of a real bill/coin
count). Deleting the dead i18n key rather than leaving it was a direct, minimal side effect of this
task's own edit (the key's only caller was removed by this same change), not a separate cleanup pass.

## Verification
- `cd frontend && pnpm vitest run src/pages/accountant/cashRegister/components/OpenShiftDialog.test.tsx` → 1/1 pass.
- `cd frontend && pnpm run build` → clean.
- `cd frontend && pnpm run lint` → 0 errors, 16 pre-existing warnings (unchanged).
- `cd frontend && pnpm run test:run` → **153/153**.
