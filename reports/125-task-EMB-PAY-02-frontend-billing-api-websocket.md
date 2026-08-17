# Report 125 — task-EMB-PAY-02

**Predecessor Task:** EMB-PAY-01 (billing broadcast pipeline, report 124)

## Objective
Wire the frontend API layer and WebSocket store to the new backend billing endpoints/broadcasts, so both the waiter and customer stores can receive bill/payment state that has no `GET` endpoint to fall back on.

## Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/store/sessionStore.tsx`
- `frontend/src/store/websocket.ts`

## What Changed?
- `api.ts`: new `billingService` (`requestBilling`, `registerPhysicalPayment`, `initiateDigitalPayment`, `confirmDigitalPayment`) plus exported types `Bill`/`BillSplit`/`Payment`/`SplitMethod` (from the already-generated `backend-types.ts` schemas — the schemas predate this task) and hand-typed `PendingDigitalPayment`/`WaiterBillState` (no generated schema exists for the WebSocket-only shapes, matching the existing `PublicBranding` precedent in this file). `RequestBillingRequest` has no generated schema either since `backend-types.ts` wasn't regenerated (would require a live backend + `pnpm run openapi`, out of scope here) — the request body is built inline instead.
- `sessionStore.tsx` (customer): added `bill`/`billSplits` fields plus `setBillReady`/`markSplitPaid` actions; both are cleared in `clearSession`.
- `websocket.ts`: `subscribeToSession` (customer) now handles `BILL_READY` (writes `sessionStore.bill`/`billSplits`) and `SPLIT_PAID` (flips the matching split's `paid`). `subscribeToWaiterSession` (waiter) now handles `BILL_READY`/`SPLIT_PAID`/`DIGITAL_PAYMENT_INITIATED` by writing directly into the `['bill', sessionId]` React Query cache via `setQueryData` — `invalidateQueries` doesn't apply here since there's no query function to refetch from; the cache entry only ever exists because the WebSocket handler put it there. `SESSION_CLOSED` now also removes that cache entry.

## Why It Changed?
Direct continuation of EMB-PAY-01: the backend now broadcasts bill/payment state, but nothing on the frontend consumed it yet. Both the waiter's future "Cobrar Mesa" view (task-EMB-PAY-03) and the customer's future bill screen (task-EMB-PAY-04) need this shared plumbing.

## Verification
`cd frontend && pnpm run build` — `tsc -b && vite build` succeeded, no type errors.
