# Report 331 — fix: remove hardcoded tip from waiter table view

## 1. Identification
- **Report number:** 331
- **Current Task:** fix-waiter-remove-hardcoded-tip
- **Predecessor Task:** report 330 (landing-tiltedshot-float-animation)

## 2. Objective
The waiter's pre-bill preview (`TableInformation.tsx`) added a hardcoded 15% tip
on top of subtotal + 10% taxes. The customer's comanda view (`ComandaView.tsx`)
only shows subtotal + 10%. The two totals disagreed and the waiter's amount to
pay was inflated by 15%. Remove the tip from the waiter side so both views agree.

## 3. Modified Files
- `frontend/src/pages/waiter/TableInformation.tsx`
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
- `TableInformation.tsx`: deleted `const tip = subtotal * 0.15`; `total` changed
  from `taxes + tip + subtotal` to `subtotal + taxes`. Removed the JSX row that
  rendered `t('tipLabel')` / `${tip.toFixed(2)}`; merged the padding onto the
  remaining taxes row (`p-4`).
- `locales/es/waiter.ts` / `locales/en/waiter.ts`: removed the now-unused
  `tipLabel` key from both dictionaries to keep `satisfies typeof esWaiter`
  parity.

Not touched: `billData.total` (line ~455) — that is the real backend-computed
bill shown once a bill exists; only the client-side pre-bill estimate had the
phantom tip.

## 5. Why It Changed?
The 15% tip was a client-only constant with no backend counterpart and no
representation in the customer-facing comanda, so it could only ever produce a
mismatch between what the waiter reads out and what the customer sees. Dropping
it aligns the waiter preview formula (`subtotal + 10%`) with
`ComandaView.tsx`'s `tableSubTotal + tableSubTotal * 0.1`.

## Verification
- `cd frontend && pnpm run build` — PASS (`tsc -b && vite build`, built in 5.12s,
  0 TypeScript errors).
