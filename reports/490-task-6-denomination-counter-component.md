# Report 490

## 1. Identification
- **Report number:** 490
- **Task ID:** CASH-SHIFT-DENOMINATION-COUNT Task 6 — `DenominationCounter` shared grid component
- **Predecessor task:** report 489 (Task 5 — frontend denomination catalog + `backend-types.ts` patch)

## 2. Objective
Build the shared 14-row denomination counting grid (bills + coins, per-row quantity input,
auto-summing read-only total) that Tasks 7-8 will drop into `OpenShiftDialog`/`CloseShiftDialog` in
place of their single typed-amount input.

## 3. Modified Files
- `frontend/src/pages/accountant/cashRegister/components/DenominationCounter.tsx` (new)
- `frontend/src/pages/accountant/cashRegister/components/DenominationCounter.test.tsx` (new)
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
`DenominationCounter` renders `NICARAGUA_DENOMINATIONS` (Task 5) as two grids (bills, coins), each
row a labeled quantity `<Input type="number">`; a running total (`sumBreakdown`) is shown read-only
at the bottom. `onChange(breakdown, total)` fires on every keystroke with only the non-zero rows.

Two real deviations from the plan's original spec, found and fixed before/while implementing:
1. **Currency format.** The plan's test assumed a `C$`-prefixed formatter. The actual
   `formatCurrency` (`frontend/src/lib/format.ts`) renders `$100.00` (USD-style `Intl.NumberFormat`)
   — the same convention every sibling cash-register component already uses
   (`CloseShiftDialog.tsx`, `ShiftHistoryTable.tsx`, etc.). Used the real output instead of inventing
   a córdoba-specific formatter, keeping this component consistent with its siblings.
2. **i18n keys can't be deferred.** `useTranslation`'s `t()` key parameter is typed as
   `keyof (typeof dictionaries)['es'][N]` — strictly derived from the actual dictionary object. The
   plan intended to defer `billsLabel`/`coinsLabel`/`totalCountedLabel`/`billDenominationLabel`/
   `coinDenominationLabel` to Task 10, but that fails `tsc -b` today, not just renders raw key names
   as the plan assumed (vitest's esbuild transform doesn't typecheck, but `pnpm run build` does).
   Added the 5 keys to `es`/`en` `waiter.ts` now so the build stays green; Task 10 will only need
   whatever i18n work remains for Tasks 7-9's own new UI.

Also found and fixed a genuine bug in the plan's own test while running it: `getByText('$200.00')`
was ambiguous because the `bill_200` row's own label text is also `"$200.00"` — colliding with the
running total once 2×`bill_100` is entered. Fixed by giving the total a `data-testid`
(`denomination-total`) and querying by that instead of by text in both total-related assertions.

## 5. Why It Changed?
Reusing the existing `formatCurrency`/`useTranslation` matches how every other cash-register
component in this codebase already works — introducing a parallel córdoba formatter or bypassing
the i18n type system would create an inconsistent, harder-to-maintain component. The test's text
collision was a latent bug regardless of `$` vs `C$` (a $200 total always collides with the C$200
bill's own label) — a `data-testid` is the standard RTL fix for "the exact text I want also appears
elsewhere on the page."

## Verification
- `cd frontend && pnpm vitest run src/pages/accountant/cashRegister/components/DenominationCounter.test.tsx` → 5/5 pass.
- `cd frontend && pnpm run build` → clean.
- `cd frontend && pnpm run lint` → 0 errors, 16 pre-existing warnings (unchanged baseline).
