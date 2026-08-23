# Report 190

## 1. Identification
- **Report:** 190
- **Task ID:** INV-03 (frontend half — backend broadcast already shipped in INV-02/report 189)
- **Predecessor Task:** INV-02 (report 189)

## 2. Objective
Add a dedicated frontend WebSocket subscription slot for the future low-stock alert, without reusing the shared `currentSubscription` slot that already serves `subscribeToSession`/`subscribeToKitchen`/`subscribeToWaiter` — reusing it would silently break whichever of those is active at the same time, the same class of bug report 123 previously fixed for the waiter subtree.

## 3. Modified Files
- `frontend/src/store/websocket.ts`

## 4. What Changed?
- New `LowStockAlert` type (`menuItemId`, `menuItemName`, `currentStock`, `unit`, `threshold`) mirroring the backend's `LowStockAlertMessage` (report 189) field-for-field.
- `useWebsocketStore` gains `inventorySubscription`/`lastLowStockAlert` state and `subscribeToInventory(tenantId)`/`unsubscribeFromInventory()`/`clearLowStockAlert()` methods, following the exact same shape as the file's existing `subscribeToWaiterSession`/`unsubscribeFromWaiterSession` pair (guards on `stompClient?.connected`, unsubscribes any existing slot occupant before subscribing, `currentClient.subscribe(...)`).
- The message handler only acts on `eventData.type === 'LOW_STOCK'`: invalidates the `['inventoryItems']` query and stores the alert payload in `lastLowStockAlert` (a translated toast can't be shown from the store itself — it isn't a React component — so a later task's page component reads this field and calls `t()` there).
- `disconnect()` now also unsubscribes and nulls `inventorySubscription` alongside the pre-existing `currentSubscription`/`waiterSessionSubscription` cleanup.

Nothing in the app calls these new exports yet — Task 5 (Admin UI) is where the future `Inventory.tsx` page wires them up. That's expected for this task.

## 5. Why It Changed?
This is Task 3 of `docs/superpowers/plans/2026-08-22-emb-inv.md`, the frontend half of spec §5's real-time low-stock alert. Landing the dedicated subscription slot as its own task (ahead of the admin UI that will consume it) keeps this infrastructure piece small and independently reviewable.

## Verification
- `cd frontend && pnpm run build` → passed (`tsc -b && vite build`, 2824 modules, no errors).
