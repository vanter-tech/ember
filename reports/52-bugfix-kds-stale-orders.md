# Report 52 — Fix KDS Showing Orders From Long-Closed Sessions

**Report Number:** 52
**Task ID:** bugfix-kds-stale-orders (ad-hoc, reported outside the PROGRESS.md queue)
**Predecessor Task:** task-5.9 (report 51)

## Objective
Fix the Kitchen Display System showing tickets for tables whose session ended long ago — the KDS was reading from what was effectively a global, unfiltered register of every order ever placed, not the current live queue.

## Modified Files
- `backend/src/main/java/com/vanter/ember/kitchen/model/KitchenOrder.java`
- `backend/src/main/java/com/vanter/ember/kitchen/repository/KitchenOrderRepository.java`
- `backend/src/main/java/com/vanter/ember/kitchen/service/KitchenService.java`
- `backend/src/main/java/com/vanter/ember/config/KitchenOrderActiveBackfill.java` (new)
- `backend/src/test/java/com/vanter/ember/kitchen/service/KitchenServiceTest.java`
- `backend/src/test/java/com/vanter/ember/config/KitchenOrderActiveBackfillTest.java` (new)

## What Changed?
- **Root cause:** `GET /kitchen/display` → `KitchenService.findDisplay()` read `kitchenOrderRepository.findByTenantId(tenantId)` — every `KitchenOrder` document ever created for the tenant, with no lifecycle filtering. Nothing ever retired an order when its session closed, so `OrdersDisplays.tsx` (polling `kitchenServices.getOrdersByTables()`) rendered tickets from sessions closed hours/days earlier alongside live ones.
- `KitchenOrder` gained an `active` boolean (`@Builder.Default = true`).
- `KitchenOrderRepository` gained `findByTenantIdAndActiveTrue(UUID tenantId)`.
- `KitchenService.findDisplay()` now queries that instead of the unfiltered list. `handleOrderItemAdded` explicitly sets `active(true)` on newly created orders. A new `@EventListener handleSessionClosed(SessionClosed event)` looks up the order for that session and flips `active = false` the moment the session closes — retiring it from the live display while leaving the document (and `GET /kitchen/orders`, which stays unfiltered) intact for history.
- `KitchenOrderActiveBackfill` (mirrors the existing `MongoTenantBackfill` idempotent-`ApplicationRunner` pattern): on boot, for every `KitchenOrder` still missing `active`, resolves its true state from its session (`active = session exists && status != CLOSED`) rather than guessing — a blanket "missing field = inactive" default would also have hidden genuinely in-progress tickets that predate this deploy, and "missing = active" would have kept the bug alive for existing data. Audits its run to `mongo_migrations` (`changeId = kitchen-order-active-backfill`), safe to re-run.
- Updated `KitchenServiceTest`'s `findDisplay_*` tests to stub the new repository method; added `handleSessionClosed_*` tests. Added `KitchenOrderActiveBackfillTest` (full `@SpringBootTest`, mirrors `MongoTenantBackfillTest`'s structure).

## Why It Changed?
The kitchen module had no concept of an order's lifecycle ending — `KitchenOrder` documents accumulated forever and `findDisplay()` returned all of them unfiltered, exactly the "global register" behavior the user diagnosed. Reacting to the existing `SessionClosed` event (already published by `SessionService.closeSession()`/`PaymentCompletedListener`) to retire orders keeps the fix inside the established event-driven pattern (`ApplicationEventPublisher`/`@EventListener`, no new coupling to the session module's repository) instead of introducing a live cross-module status check on every display fetch. The backfill runner exists because a missing boolean field can't be trusted to default correctly in either direction for pre-existing data, and the codebase already has a precedent (`MongoTenantBackfill`, task-2.18) for resolving this kind of gap at boot instead of via a one-off migration script.

## Verification
`cd backend && ./mvnw test` — 430/430 passing (423 baseline + 7 new: 2 in `KitchenServiceTest`, 5 in `KitchenOrderActiveBackfillTest`).
