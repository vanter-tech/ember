# Report 492

## 1. Identification
- **Report number:** 492
- **Task ID:** CASH-SHIFT-DENOMINATION-COUNT Task 8 — `CloseShiftDialog` uses the counter + notes
- **Predecessor task:** report 491 (Task 7 — `OpenShiftDialog` uses the counter)

## 2. Objective
Replace `CloseShiftDialog`'s single typed-amount input with the `DenominationCounter` grid, and add
an optional close-notes field, matching the backend's Task 3 `close(id, countedCash, breakdown?, notes?)`.

## 3. Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/pages/accountant/cashRegister/components/CloseShiftDialog.tsx`
- `frontend/src/pages/accountant/cashRegister/components/CloseShiftDialog.test.tsx` (new)
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
`cashShiftService.close` gains `breakdown?: DenominationCount[]` and `notes?: string` params,
forwarded in the POST body alongside `countedCash`.

`CloseShiftDialog.tsx` drops its `zod`/`react-hook-form` single-amount form. The pre-close screen
now renders `<DenominationCounter onChange={...}>` plus a `Textarea` notes field; submission calls
`cashShiftService.close(shiftId, total, breakdown, notes.trim() || undefined)` (empty notes become
`undefined`, never `''`). The post-close result screen (expected/counted/variance) is unchanged.

Same two adjustments applied as in Tasks 6-7:
- Test uses the real `formatCurrency` output (`$100.00`), not the plan's assumed `C$100.00`.
- Added `closeNotesLabel`/`closeNotesPlaceholder` to `es`/`en` `waiter.ts` now rather than deferring
  to Task 10 — `t()`'s key is strictly typed off the real dictionary, so referencing a key that
  doesn't exist yet fails `tsc -b`.

Also removed two now-orphaned i18n keys (`countedCashLabel`, `countedCashNegativeError`) from both
locale files — both were only used by the deleted zod-form input (verified no other call site).

## 5. Why It Changed?
Closes the second half of the original bug report: both open *and* close previously accepted a
single typed total instead of a real denomination count. The i18n/dead-key handling follows the
same reasoning established in Tasks 6-7 for consistency.

## Verification
- `cd frontend && pnpm vitest run src/pages/accountant/cashRegister/components/CloseShiftDialog.test.tsx` → 2/2 pass.
- `cd frontend && pnpm run build` → clean.
- `cd frontend && pnpm run lint` → 0 errors, 16 pre-existing warnings (unchanged).
- `cd frontend && pnpm run test:run` → **155/155**.
