# Report 162 — bugfix-kitchen-delivered-items-visible

## 1. Identification
- **Report Number:** 162
- **Task ID:** bugfix-kitchen-delivered-items-visible
- **Predecessor Task:** bugfix-bill-never-marked-paid (report 161)

## 2. Objective
On the Kitchen Display System (`/kitchen/orders`), marking a dish's final status (`DELIVERED`) did not remove it from the ticket view — it kept showing in `QueueCard`/`FocusedCard` alongside pending/preparing items, only losing its action button.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/kitchen/service/KitchenService.java`
- `frontend/src/pages/kitchen/components/QueueCard.tsx`
- `frontend/src/pages/kitchen/components/FocusedCard.tsx`

## 4. What Changed?
- **Backend:** kept the pre-existing uncommitted fix in `updateItemStatus` — when every item on a `KitchenOrder` reaches `DELIVERED`, the order is flagged `active=false` and a `KitchenOrderRetired` event fires, so `GET /kitchen/display` (`findByTenantIdAndActiveTrue`) stops returning the whole ticket. This part was already correct but unfinished/uncommitted.
- **Frontend:** `QueueCard.tsx` and `FocusedCard.tsx` now filter out items with `status === 'DELIVERED'` before rendering the item list, so a dish disappears from the ticket the moment it's marked delivered — not just when every item on the order is done.
- **Verified, no changes needed:** kitchen websocket real-time sync was reported as "not implemented," but it already is — `KitchenLayout` connects and calls `subscribeToKitchen(tenantId)`, `websocket.ts` subscribes to `/topic/kitchen/{tenantId}` and invalidates `['kitchenOrders']`, and backend `KitchenWebSocketListener` broadcasts `KitchenItemUpdated`/`KitchenOrderRetired`/`KitchenItemRemoved`/`KitchenItemsConfirmed` to that topic. No code changes were made here.

## 5. Why It Changed?
The KDS is meant to show only what's still outstanding — a served dish lingering on-screen forces kitchen staff to visually re-filter it themselves, and an order that's fully delivered has no reason to keep occupying a ticket slot. The two fixes are complementary: the backend removes the whole ticket once nothing is left to serve, the frontend removes each item as soon as it individually reaches its terminal state, closing the gap for orders that are only partially delivered.

## Verification
- Backend: `./mvnw test` → 723/723 green.
- Frontend: `pnpm run build` (`tsc -b && vite build`) → green.
