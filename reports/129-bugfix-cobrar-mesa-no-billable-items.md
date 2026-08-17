# Report 129 — bugfix-cobrar-mesa-no-billable-items

**Predecessor Task:** bugfix-billing-request-generated-type (report 128)

## Objective
Fix a 409 (`No billable items in session`) thrown when a waiter picks "Por consumo" or "Partes iguales" in `ChargeTableModal` and confirms.

## Modified Files
- `frontend/src/pages/waiter/TableInformation.tsx`

## What Changed?
Added `hasBillableItems` — `itemsToWaiter.some(item => item.status === 'READY' || item.status === 'DELIVERED')` — and used it to `disabled`-gate the "Cobrar Mesa" button, swapping its label to "Esperando entrega de pedidos" when there are confirmed items but none yet billable.

## Why It Changed?
Root cause (systematic-debugging, Phase 1): `BillingService.calculateBill` (pre-existing, unchanged) only bills items with status `READY` or `DELIVERED` — items still `PENDING`/`PREPARING` in the kitchen aren't counted, and it throws `IllegalStateException("No billable items in session")` (409) if zero items qualify. `TableInformation.tsx`'s "Cobrar Mesa" button (added in EMB-PAY-03, report 126) was gated on `hasItems`, which only excludes `DRAFT` items — so a waiter could open the charge modal and confirm a split method for a table whose items had all been confirmed to the kitchen but not yet marked ready/delivered, hitting the backend's legitimate business rule with no client-side guard. The backend rule itself is correct and untouched (you shouldn't charge for food not yet served); the bug was the frontend letting the waiter reach an action the backend was always going to reject.

## Verification
`cd frontend && pnpm run build` and `pnpm run lint` — build clean; lint unchanged at 17 pre-existing errors/8 warnings (zero new issues).
