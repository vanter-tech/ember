# Report 646 — MONEY-CENT-ROUNDING-FIXES

## 1. Identification
- Report: 646
- Task ID: MONEY-CENT-ROUNDING-FIXES
- Predecessor: 645 (ACCOUNTANT-CASH-RECEIPTS-TITLE-CARD-TIME)

## 2. Objective
Remove the two places where cents could drift: floating-point totals sent by the denomination counter, and per-diner rounding in split-by-consumption.

## 3. Modified Files
- `frontend/src/lib/denominations.ts`
- `frontend/src/lib/denominations.test.ts`
- `backend/src/main/java/com/vanter/ember/billing/service/BillingService.java`
- `backend/src/test/java/com/vanter/ember/billing/service/BillingServiceTest.java`

## 4. What Changed?
- `sumBreakdown` now sums whole cents (`Math.round(value * 100) * quantity`) and divides once. Before, 3 x 0.10 gave `0.30000000000000004`, 3 x 0.05 gave `0.15000000000000002`, 7 x 0.10 gave `0.7000000000000001`; that total went to `/cash-shifts/open|close`, where `CashShiftService.validateBreakdown` compares it with the exact `BigDecimal` sum (`compareTo != 0`) and answered 400.
- `splitByConsumption` no longer rounds each diner's taxed share independently. New `splitsSummingToTotal` floors each share to cents and gives the cents still missing to reach the rounded grand total to the diners with the largest fractional remainder (ties by name order). Example: two diners at 1.05 with 10% tax gave 1.16 + 1.16 = 2.32 against a 2.31 bill; now 1.16 + 1.15 = 2.31.
- Tests: denominations exact-cent cases (0.3, 0.15, 0.7, 0.15 mixed); billing test that splits sum exactly to the bill total when each share would round up.

## 5. Why It Changed?
Money must add up to the cent. The first was a latent 400 on opening/closing a shift for certain coin counts; the second charged a cent more than the bill total for certain price/tax combinations (the table closes when all splits are paid, not when the bill total is reached).

## Verification
- Backend: `BillingServiceTest`, `BillingControllerTest`, `BillingEventListenerTest`, `PaymentServiceTest`, `E2EOrderFlowTest` 127/127. Full `./mvnw test`: 1676/1676, BUILD SUCCESS.
- Frontend: `pnpm run build` clean, lint 0 errors, `src/lib` + `src/pages/accountant` 57/57.
- Not reproduced against a running app.
