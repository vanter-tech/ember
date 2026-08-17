# Report 127 — task-EMB-PAY-04

**Predecessor Task:** EMB-PAY-03 (waiter charge-table flow, report 126)

## Objective
Give customers a "Mi Cuenta" screen — previously the customer flow had zero bill/payment UI at all, even though `POST /billing/payments/digital` was already CUSTOMER-permitted server-side.

## Modified Files
- `frontend/src/pages/customer/Bill.tsx` (new)
- `frontend/src/App.tsx`
- `frontend/src/pages/customer/Menu.tsx`
- `frontend/src/pages/customer/components/MobileActionsIsland.tsx`

## What Changed?
- `Bill.tsx` (new page, route `/customer/menu/:id/bill`): reads `bill`/`billSplits` from `sessionStore` (populated by the `BILL_READY`/`SPLIT_PAID` WebSocket handlers from task-EMB-PAY-02 — no fetch on mount, since no `GET` endpoint exists). Resolves "my split" by matching the logged-in user's `userId` against `session.participants` to get their `name` (the identifier `BillSplit.participantName` actually keys on — the same indirection `ComandaView`/`ItemsFloatingIsland` already use for "my items"). Shows the full split list with each participant's paid/pending status, and a "Pagar mi parte" button (`billingService.initiateDigitalPayment`) that becomes "Esperando confirmación..." after the mutation succeeds (via `payMutation.isSuccess`, not local state, to avoid a synchronous `setState`-in-`useEffect` — the button's containing block already unmounts once `SPLIT_PAID` flips `mySplit.paid`, so no explicit reset was needed). Before a bill exists, shows a waiting message.
- `App.tsx`: registered the new route inside the existing `CustomerLayout` route group.
- `Menu.tsx`: added a persistent "Ver cuenta" button next to the table-code badge (unlike `ItemsFloatingIsland`, which only renders while the customer has draft items — exactly the moment least useful for checking a bill).
- `MobileActionsIsland.tsx`: added a "Ver cuenta" entry alongside the existing "Ver comanda" one.

## Why It Changed?
Completes the customer side of the payment cycle requested by the user. A known limitation carried over from the "no `GET` endpoint" design decision (EMB-PAY-01): a customer who reloads the page or reconnects after missing a broadcast (e.g., closed the tab while the waiter confirmed a payment) will see stale bill/split data until the next broadcast arrives — reintroducing a `GET` endpoint would fix this but was explicitly out of scope for the chosen "small backend touch" option.

## Verification
`cd frontend && pnpm run build` and `pnpm run lint` — build clean; lint clean for every file in this task (zero errors/warnings in `Bill.tsx`, `Menu.tsx`'s only remaining error is pre-existing and unrelated, `MobileActionsIsland.tsx` clean, `App.tsx` clean).
