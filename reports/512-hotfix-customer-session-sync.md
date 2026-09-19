# Report 512 — Hotfix: customer stays on the bill view after the table is paid and closed

## 1. Identification
- **Report number:** 512
- **Task ID:** ad-hoc live bug (customer/guest view)
- **Predecessor task:** report 511 — bump 0.2.6.1.1

## 2. Objective
After every diner paid and the table closed, a diner (reported as a guest) stayed on the bill view; after F5 the view offered "Pagar mi parte" again and the payment failed because the share was already paid. The waiter view was correct (all paid, table closed).

## 3. Modified Files
- `frontend/src/layouts/CustomerLayout.tsx`
- `frontend/src/pages/customer/Menu.tsx`
- `frontend/src/pages/customer/Bill.tsx`
- `frontend/src/layouts/CustomerLayout.test.tsx` (new)
- `frontend/src/pages/customer/Bill.test.tsx` (new)
- `PROGRESS.md`
- `reports/512-hotfix-customer-session-sync.md`

## 4. What Changed?
- `CustomerLayout` now subscribes to the session topic (`subscribeToSession`) whenever the socket is connected and a session id is stored, and runs the closed-session guard (`sessionStatus` query → `CLOSED`/error ⇒ `clearSession` + navigate `/customer/home`). Both used to live only in `Menu.tsx`; they were removed from there (plus their now-unused imports/variables).
- `Bill` always fetches `getBillState` on open (previously only when the store had no bill) and syncs it into the store; a `null` answer clears a stale persisted bill, unless a live `BILL_READY` frame already replaced it (ref-guarded against that race).
- Tests: 5 for the layout (subscribes a guest-identity user on the bill route, not while disconnected, CLOSED ⇒ leaves, unknown session ⇒ leaves, no session ⇒ nothing) and 2 for `Bill` (stale UNPAID split replaced by server PAID so no pay button; persisted bill the server no longer has is dropped).

## 5. Why It Changed?
Root cause (from code; not reproduced live): the backend is correct — the last payment publishes `PaymentCompleted`, the session is closed and `SESSION_CLOSED` is sent to `/topic/session/{id}`. But the client subscribed to that topic only from `Menu.tsx`. On `/comanda` or `/bill`, especially after a reload or reconnect, the diner received neither `SPLIT_PAID` nor `SESSION_CLOSED`, so nothing moved them out. The session store is persisted in `localStorage`, and `Bill` only asked the server when the store had no bill, so after F5 it rendered the stale UNPAID split and offered payment again; the backend rejects that because the split is no longer UNPAID. Guests are not special: `setAuth` stores their `CUSTOMER` identity, so they pass `ProtectedRoute` and mount `CustomerLayout` like any customer.

Verification: `pnpm run test:run` **171/171** (164 + 7), `pnpm run lint` 0 errors (15 warnings), `pnpm run build` OK.
