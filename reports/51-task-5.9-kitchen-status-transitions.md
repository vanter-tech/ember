# Report 51 — Wire Kitchen Order Status Transitions

**Report Number:** 51
**Task ID:** task-5.9
**Predecessor Task:** task-5.8 (report 50)

## Objective
Consume the existing but unused `PATCH /kitchen/orders/{orderId}/items/{itemId}/status` endpoint from the frontend, giving `QueueCard`/`FocusedCard` real controls to advance a kitchen item through `PENDING → PREPARING → READY → DELIVERED`.

## Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/pages/kitchen/lib/itemStatus.ts` (new)
- `frontend/src/pages/kitchen/components/QueueCard.tsx`
- `frontend/src/pages/kitchen/components/FocusedCard.tsx`

## What Changed?
- `api.ts`: added `kitchenServices.updateItemStatus(orderId, itemId, status)`, typed via `UpdateItemStatusRequest`/`KitchenOrder`; exported `OrderItemStatus` type alias.
- `itemStatus.ts`: shared `NEXT_STATUS`/`STATUS_LABEL`/`NEXT_ACTION_LABEL` maps (`DELIVERED` has no next step) used by both KDS card components, mirroring the backend's strict sequential transition rule (`KitchenService.isValidTransition`).
- `QueueCard.tsx`: each item now shows a status `Badge` and, when not `DELIVERED`, an "advance" `Button` that calls a `useMutation` wrapping `updateItemStatus` and invalidates the `['kitchenOrders']` query on success.
- `FocusedCard.tsx`: previously rendered no item list at all — added one with the same status badge + advance-button pattern; existing `Imprimir`/`Anular` buttons left untouched (out of scope).

## Why It Changed?
`kitchenServices` had no client for the backend's item-status endpoint, so KDS staff had no way to progress an order once queued — `QueueCard`'s "Ver detalles" and `FocusedCard`'s header buttons carried no status-transition logic. This closes that gap using the existing `useMutation`/query-invalidation pattern already established across the app's settings tabs, and mirrors the backend's one-step-at-a-time transition contract instead of allowing arbitrary status jumps from the UI.

## Verification
`cd frontend && pnpm run build` — passed, 0 TypeScript errors.
`pnpm run lint` — 18 pre-existing errors/5 warnings in unrelated files (`EditMenuModal.tsx`, `Menu.tsx`, `sessionStore.tsx`, etc.); none in the files this task touched.
