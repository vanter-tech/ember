# Report 25 — task-2.17: tenant-scope the Mongo aggregates (`Session`, `KitchenOrder`)

## 1. Identification
- **Report number:** 25
- **Task ID:** task-2.17
- **Predecessor task:** task-2.16 (report 24 — cross-tenant isolation regression tests for the JPA repositories)

## 2. Objective
Give the two MongoDB aggregates a real tenant discriminator and make every repository read
tenant-scoped, closing the cross-tenant reads that the JPA side already blocks via Hibernate
`@TenantId` — including `KitchenService.findDisplay()`, which returned every restaurant's kitchen
queue through an untenanted `findAll()`.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/session/model/Session.java`
- `backend/src/main/java/com/vanter/ember/kitchen/model/KitchenOrder.java`
- `backend/src/main/java/com/vanter/ember/session/repository/SessionRepository.java`
- `backend/src/main/java/com/vanter/ember/kitchen/repository/KitchenOrderRepository.java`
- `backend/src/main/java/com/vanter/ember/session/service/SessionService.java`
- `backend/src/main/java/com/vanter/ember/session/service/DashboardService.java`
- `backend/src/main/java/com/vanter/ember/kitchen/service/KitchenService.java`
- `backend/src/main/java/com/vanter/ember/session/event/KitchenItemsConfirmed.java`
- `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java`
- `backend/src/test/java/com/vanter/ember/kitchen/service/KitchenServiceTest.java`
- `backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTest.java`
- `backend/src/test/java/com/vanter/ember/kitchen/repository/KitchenOrderRepositoryTest.java`
- `backend/src/test/java/com/vanter/ember/E2EOrderFlowTest.java`
- `PROGRESS.md`

## 4. What Changed?

### Models
`Session` and `KitchenOrder` each gained a `UUID tenantId` field. Mongo has no equivalent of
Hibernate's `@TenantId` filter, so the discriminator has to be carried explicitly by every query.

### Repositories
All custom finders are now tenant-first; the unscoped variants were removed so no call site can
accidentally keep using one.

| Before | After |
| --- | --- |
| `findByTableIdAndStatus` | `findByTenantIdAndTableIdAndStatus` |
| `findByParticipants_UserId` | `findByTenantIdAndParticipants_UserId` |
| `findByTableIdInAndStatus` | `findByTenantIdAndTableIdInAndStatus` |
| `findByJoinCodeAndStatusAndTableIdIn` | `findByTenantIdAndJoinCodeAndStatus` |
| (none) | `SessionRepository.findByIdAndTenantId` |
| `KitchenOrderRepository.findBySessionId` | `findByTenantIdAndSessionId` |
| `KitchenOrderRepository.findByItems_Status` | `findByTenantIdAndItems_Status` |
| (none) | `KitchenOrderRepository.findByTenantId`, `findByIdAndTenantId` |

### `SessionService`
- `createSession` stamps `tenantId` from `TenantContextHolder.requireTenantId()` and checks table
  occupancy with the tenant-scoped query.
- `findById` is now the single tenant-enforcing load point
  (`findByIdAndTenantId` + `requireTenantId`), and `joinSession`, `expandCapacity`, `closeSession`,
  `closeEmptySession`, `confirmDraftsForUser` and `getSessionStatus` all route through it instead of
  calling `sessionRepository.findById` directly.
- The task-2.13 stopgaps are gone: `assertSessionBelongsToCurrentTenant` (which proved ownership via
  the session's `DiningTables` row) and `currentTenantTableIds()` (which scoped the join-code lookup
  to the tenant's active table ids) were deleted; `joinSessionCode` now queries by tenant directly.
- The published `KitchenItemsConfirmed` event carries the session's `tenantId`.

### `KitchenService`
- `findAll()` → `findByTenantId(...)`, and `findDisplay()` now groups over `findAll()` instead of its
  own `kitchenOrderRepository.findAll()`.
- `findBySessionId` and `updateItemStatus` resolve through the tenant-scoped finders, so an order id
  or session id from another restaurant yields `ResourceNotFoundException`, not data.
- `handleOrderItemAdded` looks the order up by `(tenantId, sessionId)` and stamps `tenantId` on
  newly created orders, taking the value from the event rather than the ambient thread-local.

### `DashboardService`
`getLiveStatus` uses `findByTenantIdAndTableIdInAndStatus`, so the table-id list is no longer the
only thing standing between tenants.

### Tests
`SessionServiceTest` and `KitchenServiceTest` bind a tenant in `@BeforeEach` (clearing it in
`@AfterEach`) and stub the tenant-scoped finders. New cases: `createSession` stamps the tenant;
`findById`/`updateItemStatus`/`findBySessionId` raise `ResourceNotFoundException` when the scoped
lookup misses; `confirmDraftsForUser` publishes the tenant on its event; and the two Mongo
`@DataMongoTest` classes assert that `findByIdAndTenantId`, `findByTenantIdAndJoinCodeAndStatus`,
`findByTenantId` and `findByTenantIdAndSessionId` do not resolve another tenant's documents.

## 5. Why It Changed?
Until now, tenant ownership of a `Session` was inferred from its dining table and `KitchenOrder` had
no tenant marker at all. That made every Mongo read either indirect (an extra join through
`DiningTables`, which fails as soon as a table is deleted or reassigned) or entirely unscoped —
`GET /kitchen/display` and `GET /kitchen/orders` served every restaurant's queue to any authenticated
KITCHEN/ADMIN user, and any session id was readable across tenants. Putting the discriminator on the
documents makes tenancy an intrinsic property of the data, matching the `@TenantId` model already in
place for the JPA entities, and pushing enforcement into the repository query means a missed check at
a call site can no longer leak another restaurant's data.

The event carries the tenant explicitly instead of letting the listener read the thread-local: the
kitchen order must belong to the *session's* tenant, and an explicit field keeps that true if the
listener ever moves off the publishing thread.

## 6. Verification
`cd backend && ./mvnw test` → **BUILD SUCCESS, 355/355 tests passing** (348 before; +7 new).

## 7. Follow-up (task-2.18)
Existing `sessions`/`kitchen_orders` documents have no `tenantId` and are therefore invisible to the
new queries until the backfill migration lands. That migration, plus dedicated cross-tenant isolation
suites for the Mongo repositories, is task-2.18.
