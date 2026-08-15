# Report 107 — bugfix-kitchen-orphan-item

## 1. Identification
- **Report Number:** 107
- **Task ID:** bugfix-kitchen-orphan-item
- **Predecessor Task:** bugfix-comanda-resend (report 106)

## 2. Objective
Make a waiter's item deletion remove the item from the live Kitchen Display System (KDS) too, instead of only from the waiter's table view.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/kitchen/event/KitchenItemRemoved.java` (new)
- `backend/src/main/java/com/vanter/ember/kitchen/service/KitchenService.java`
- `backend/src/main/java/com/vanter/ember/kitchen/listener/KitchenWebSocketListener.java`

## 4. What Changed?
Added `KitchenItemRemoved(tenantId, sessionId, itemId)`. `KitchenService` gained `handleItemDeleted`, an `@EventListener` for the existing `session.event.DeleteItem`: it looks up the session's `KitchenOrder` (tenant-scoped via `TenantContextHolder`, mirroring `handleSessionClosed`), removes the matching `KitchenItem` by `itemId`, saves, and — only if an item was actually removed — publishes `KitchenItemRemoved`. `KitchenWebSocketListener` broadcasts that new event to `/topic/kitchen/{tenantId}`, the same channel `KitchenItemsConfirmed`/`KitchenItemUpdated`/`KitchenOrderRetired` already use; the frontend's `subscribeToKitchen` already invalidates `['kitchenOrders']` on any message on that topic, so no frontend change was needed.

## 5. Why It Changed?
`SessionService.removeItem` only ever published `DeleteItem` to `/topic/session/{sessionId}` — nothing updated the separate Mongo `KitchenOrder` document that the KDS reads from (`kitchen_orders` collection, distinct from `sessions`). So a confirmed item deleted by the waiter correctly vanished from `TableInformation` (session-topic listener) but stayed on the kitchen queue forever, since the KDS's own backing record was never touched. This mirrors the existing `handleSessionClosed` pattern (session-side event closing/retiring the kitchen record) rather than inventing a new mechanism.

## 6. Verification
- `cd frontend && pnpm run build` — passed (no frontend changes required for this fix beyond what was already verified for report 106).
- `cd backend && ./mvnw test` — exit code 0, all tests passed (built together with report 106's changes; ran once covering both).
