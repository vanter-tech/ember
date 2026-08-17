# Report 126 — task-EMB-PAY-03

**Predecessor Task:** EMB-PAY-02 (frontend billing API/WebSocket wiring, report 125)

## Objective
Replace the waiter's dead "Cobrar Mesa" button and fictional client-side tax/tip preview in `TableInformation.tsx` with a real, live billing and payment flow.

## Modified Files
- `frontend/src/pages/waiter/TableInformation.tsx`
- `frontend/src/pages/waiter/components/ChargeTableModal.tsx` (new)
- `frontend/src/store/uiStore.ts`

## What Changed?
- `uiStore.ts`: added `'CHARGE_TABLE'` to `ModalType`.
- `ChargeTableModal.tsx` (new, follows the existing `ParticipantsQrModal`/`GlobalDeleteModal` dialog+`useUIStore` pattern): lets the waiter pick `BY_CONSUMPTION` or `EQUAL_PARTS`, then calls `billingService.requestBilling`.
- `TableInformation.tsx`:
  - Subscribes to `subscribeToWaiterSession(id)` on mount (this page previously had **no** live subscription at all, unlike the `Tables.tsx` sidebar).
  - Reads `['bill', id]` via `useQuery({ enabled: false })` — a read-only view onto the cache entry that `websocket.ts` populates from `BILL_READY`/`SPLIT_PAID`/`DIGITAL_PAYMENT_INITIATED`.
  - "Cobrar Mesa" now opens `ChargeTableModal`. Once a bill exists, the Resumen card is replaced by a real split list: paid splits show a "Pagado" badge, unpaid splits with a pending digital payment show "Confirmar digital" (`billingService.confirmDigitalPayment`), everything else shows "Marcar pagado" (`billingService.registerPhysicalPayment`, backend-gated on an open cash shift).
  - Navigates back to `/waiter/tables` once `sessionData.status === 'CLOSED'` (mirrors the existing customer-side redirect-on-close pattern).
  - The pre-existing fictional 10%/15% tax/tip preview is kept only as a *pre-bill estimate* (the backend's `Bill.total` is a plain item-price sum with no tax/tip concept) — it's replaced by the authoritative bill total once one exists.

## Why It Changed?
Direct continuation of the payment-cycle work: this was the primary "charge the table" entry point identified during the initial investigation, previously entirely unwired (no `onClick` at all). `Tables.tsx`'s own sidebar "Cobrar mesa"/"Imprimir cuenta" buttons were left untouched — out of scope, and redundant with this page's "Ver Informacion" link.

## Verification
`cd frontend && pnpm run build` and `pnpm run lint` — build clean; lint introduces zero new errors (verified by diffing lint output before/after: file-by-file, every remaining error in touched files pre-existed this change, e.g. `TableInformation.tsx`'s `no-unused-expressions` on the untouched `;(e.preventDefault(), e.stopPropagation())` line).
