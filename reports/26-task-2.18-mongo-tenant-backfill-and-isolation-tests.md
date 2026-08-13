# Report 26 — task-2.18: Mongo tenant backfill + cross-tenant isolation tests

## 1. Identification
- **Report number:** 26
- **Task ID:** task-2.18
- **Predecessor task:** task-2.17 (report 25 — tenant-scoped the Mongo aggregates)

## 2. Objective
Give the documents written before task-2.17 a `tenantId` — they are currently invisible to every
tenant-scoped query — and pin the isolation guarantees of both Mongo repositories with dedicated
regression suites, mirroring what task-2.16 did for the JPA side.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/MongoTenantBackfill.java` *(new)*
- `backend/src/test/java/com/vanter/ember/config/MongoTenantBackfillTest.java` *(new)*
- `backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTenantIsolationTest.java` *(new)*
- `backend/src/test/java/com/vanter/ember/kitchen/repository/KitchenOrderRepositoryTenantIsolationTest.java` *(new)*
- `backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTest.java`
- `backend/src/test/java/com/vanter/ember/kitchen/repository/KitchenOrderRepositoryTest.java`
- `PROGRESS.md`

## 4. What Changed?

### `MongoTenantBackfill` — the migration
An `ApplicationRunner` in `config/`, deliberately **not** a new dependency: Flyway does not cover
MongoDB and Mongock would have meant adding a framework for a single change. The public
`backfill()` method returns a `Result` record (`sessionsUpdated`, `kitchenOrdersUpdated`,
`unresolvedSessions`, `unresolvedKitchenOrders`) so the behaviour is testable without booting a
second context.

Resolution is per document, not a global guess:

1. **Sessions** — for each `Restaurant`, its dining-table ids are collected and one `updateMulti`
   stamps every untenanted session sitting on one of those tables. `DiningTables` is a `@TenantId`
   entity, so the tenant must be bound for the JPA read to see anything at all; `tableIdsOf` binds
   it via `TenantContextHolder` and **restores the previous value** rather than clearing, so calling
   `backfill()` from inside a request is safe.
2. **Kitchen orders** — each untenanted order inherits the `tenantId` of the session it references,
   after step 1 has tenanted the sessions.
3. **Orphans** (dining table or session deleted, so ownership has no evidence) — assigned to the
   sole restaurant when exactly one exists, which is the rule Flyway `V2` already applies on the SQL
   side. Otherwise they are left untenanted, logged at ERROR with counts and instructions, and
   startup continues: an untenanted document is invisible to every tenant-scoped query, so the
   failure mode is missing data, never leaked data. Aborting a boot over legacy rows would be the
   more damaging option.

Re-running is a no-op — every query filters on `tenantId: null` (which matches a missing field too),
and the first thing `backfill()` does is count those and return early. An audit document
(`changeId`, `appliedAt`, counts) is appended to a `mongo_migrations` collection only on runs that
actually changed documents.

### Tests
- `MongoTenantBackfillTest` (`@SpringBootTest` — the backfill spans JPA *and* Mongo, so a slice
  cannot host it): sessions get the tenant of their table; kitchen orders inherit from their
  session; orphans stay unresolved when two restaurants exist; orphans fall back to the sole
  restaurant when only one does; already-tenanted documents are never rewritten; a second run
  changes nothing and writes no second audit row.
- `SessionRepositoryTenantIsolationTest` and `KitchenOrderRepositoryTenantIsolationTest`
  (`@DataMongoTest`): two tenants hold deliberately identical data — same table id, participant,
  join code, session id and item status — so any finder that dropped the tenant from its query would
  return both documents. Every tenant-scoped finder on both repositories is covered.
- The three ad-hoc isolation assertions added to `SessionRepositoryTest`/`KitchenOrderRepositoryTest`
  in task-2.17 were removed; the dedicated suites now own that coverage, matching how the JPA tests
  are split.

Unlike the JPA suites, these do not extend `config/AbstractTenantIsolationTest`: that base exists to
work around Hibernate resolving the tenant once per session, and Mongo has no ambient filter to
work around — the tenant is an explicit query parameter.

## 5. Why It Changed?
task-2.17 made `tenantId` the sole basis for reading `sessions` and `kitchen_orders`, which silently
orphaned every document written before it: live sessions would vanish from the floor dashboard and
in-flight orders from the KDS. The backfill closes that gap, and deriving each document's owner from
its dining table (rather than assuming a single restaurant, as the SQL migration had to) keeps the
result correct on multi-tenant deployments.

The isolation suites exist because Mongo gives no structural protection here. On the JPA side a
missing tenant predicate is still caught by Hibernate's `@TenantId` filter; on the Mongo side a
finder declared without `TenantId` in its name simply returns everyone's data, and nothing but a
test will notice.

## 6. Verification
`cd backend && ./mvnw test` → **BUILD SUCCESS, 367/367 tests passing** (355 before: +6 backfill,
+5 session isolation, +4 kitchen isolation, −3 relocated).
