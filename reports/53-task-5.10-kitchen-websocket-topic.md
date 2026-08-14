# Report 53 — task-5.10: Tenant-scoped kitchen WebSocket topic

**Predecessor:** bugfix-kds-stale-orders (report 52)

## Objective
Give the KDS live updates instead of requiring a manual refetch: broadcast kitchen-relevant
events to a new `/topic/kitchen/{tenantId}` STOMP topic and subscribe from `OrdersDisplays`
via `KitchenLayout`.

## Modified Files
- `backend/src/main/java/com/vanter/ember/kitchen/event/KitchenItemUpdated.java`
- `backend/src/main/java/com/vanter/ember/kitchen/event/KitchenOrderRetired.java` (new)
- `backend/src/main/java/com/vanter/ember/kitchen/service/KitchenService.java`
- `backend/src/main/java/com/vanter/ember/kitchen/listener/KitchenWebSocketListener.java` (new)
- `backend/src/test/java/com/vanter/ember/kitchen/service/KitchenServiceTest.java`
- `backend/src/test/java/com/vanter/ember/kitchen/listener/KitchenWebSocketListenerTest.java` (new)
- `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java`
- `frontend/src/store/websocket.ts`
- `frontend/src/layouts/KitchenLayout.tsx`

## What Changed?
- `KitchenItemUpdated` gained a `tenantId` field, populated from `TenantContextHolder.requireTenantId()`
  in `KitchenService.updateItemStatus`.
- New `KitchenOrderRetired(tenantId, sessionId)` event, published from
  `KitchenService.handleSessionClosed` using the retired order's own `tenantId`.
- New `KitchenWebSocketListener` (mirrors `SessionWebSocketListener`) broadcasts
  `KitchenItemsConfirmed`, `KitchenItemUpdated`, and `KitchenOrderRetired` to
  `/topic/kitchen/{tenantId}`.
- `websocket.ts` gained `subscribeToKitchen(tenantId)`: subscribes to the tenant topic and calls
  `queryClient.invalidateQueries({queryKey: ['kitchenOrders']})` on any message, refreshing
  `OrdersDisplays`'s existing `useQuery` without further plumbing.
- `KitchenLayout` now connects the STOMP client and calls `subscribeToKitchen` using
  `useAuthStore().restaurantId`, disconnecting on unmount (same pattern as `CustomerLayout`).
- Updated existing test call sites for the new `KitchenItemUpdated` field and added assertions for
  the new `KitchenOrderRetired` publication.

## Why It Changed?
Backend previously only broadcast session/kitchen events to `/topic/session/{sessionId}` for the
customer view; the KDS (`OrdersDisplays.tsx`) never subscribed to anything, so item-status changes
made from a second kitchen terminal were invisible until a manual page refresh. Routing the
existing kitchen-domain events to a tenant-scoped topic and invalidating the orders query on
receipt closes that gap with minimal new surface area, reusing events already published by
`KitchenService`.

**Known limitation (out of scope):** no STOMP SUBSCRIBE-destination ACL was added for
`/topic/kitchen/{tenantId}`; this matches the existing, already-accepted posture for
`/topic/session/{sessionId}`, which has no such guard either.

## Verification
- `./mvnw test` — 433/433 passing.
- `pnpm run build` (`tsc -b && vite build`) — 0 TypeScript errors, build succeeded.
