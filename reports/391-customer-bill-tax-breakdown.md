# Report 391 — show the tax breakdown on the customer bill screen

## 1. Identification
- **Report:** 391
- **Task ID:** Q2 — configured tax not shown on the bill view
- **Predecessor:** report 390 — fix(billing/loyalty): reverse loyalty points on refund (Q1b, PR #89)

## 2. Objective
Show the customer, on their bill screen, how much of the total is tax — a `Subtotal` and
`Impuesto (X%)` line — instead of only the tax-inclusive lump total.

## 3. Modified Files
- `frontend/src/pages/customer/Bill.tsx`
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`

## 4. What Changed?
- `Bill.tsx` now pulls `settings.billing.taxRate` via `useSettingStore()` and, when the rate is
  `> 0`, renders a small breakdown above the per-participant splits:
  - `Subtotal = bill.total / (1 + taxRate/100)`
  - `Impuesto (X%) = bill.total - subtotal`
- With no tax rate configured, nothing extra is shown — the screen looks exactly as before.
- New i18n keys `billSubtotalLabel` and `billTaxLabel` (`Impuesto ({{rate}}%)` / `Tax ({{rate}}%)`)
  in the `customer` es/en dictionaries.
- Verified: `pnpm run build` clean, `pnpm run lint` 0 errors (16 pre-existing warnings),
  `pnpm run test:run` **91/91**.

## 5. Why It Changed?
The bill only ever carries a tax-inclusive `total`: `BillReadyMessage.of(billId, bill.getTotal(),
splits)` and `getBillState` both expose `total` alone, and the `Bill` entity persists only
`total` (no subtotal/tax columns). `BillingService.calculateBill` computes
`total = subtotal * (1 + rate/100)` and discards the parts. So the customer's `Bill.tsx` had no
data to show a tax line and displayed just the total.

Reconstructing client-side from the tenant's configured rate keeps this frontend-only. The
customer is already allowed to read `GET /settings` (E-05: needs the rate for their order
preview), and `ComandaView.tsx` already uses the same `useSettingStore()` + rate pattern for its
pre-order breakdown. The reconstruction is exact when the rate is unchanged since the bill was
calculated; `subtotal + tax` always equals the displayed total.

The waiter-side half of the original Q2 report was a non-bug — the tax rate simply wasn't
configured in that test session; the waiter's pre-bill view already renders the breakdown when a
rate is set. Persisting `subtotal`/`taxAmount` on `Bill` (so the *post-charge* waiter view and
printed receipts could show a breakdown too) was considered and deferred — it needs a `V9`
migration and the prod manual-`ALTER` step, and no one is currently blocked on it.
