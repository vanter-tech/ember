# Report 24 — task-2.16: Cross-tenant isolation regression tests for every JPA repository

## 1. Identification
- **Report number:** 24
- **Task ID:** task-2.16 — Add cross-tenant isolation regression tests for every JPA repository
- **Predecessor task:** task-2.15 (report 23 — Flyway V2 `tenant_id` backfill + per-tenant constraints)

## 2. Objective
Lock in the discriminator multi-tenancy introduced by task-2.14/2.15 with executable regression tests:
every JPA repository must prove that a caller bound to tenant B cannot read, count, or delete tenant A's
rows, and that writes are stamped with the bound tenant. The two intentionally *unscoped* repositories
(`User`, `Restaurant`) get tests pinning that exclusion so it stays a decision instead of an accident.

## 3. Modified Files
- `backend/src/test/java/com/vanter/ember/config/AbstractTenantIsolationTest.java` (new)
- `backend/src/test/java/com/vanter/ember/catalog/repository/CategoryRepositoryTenantIsolationTest.java` (new)
- `backend/src/test/java/com/vanter/ember/catalog/repository/MenuItemRepositoryTenantIsolationTest.java` (new)
- `backend/src/test/java/com/vanter/ember/billing/repository/BillRepositoryTenantIsolationTest.java` (new)
- `backend/src/test/java/com/vanter/ember/billing/repository/BillSplitRepositoryTenantIsolationTest.java` (new)
- `backend/src/test/java/com/vanter/ember/billing/repository/PaymentRepositoryTenantIsolationTest.java` (new)
- `backend/src/test/java/com/vanter/ember/settings/repository/DiningTableRepositoryTenantIsolationTest.java` (new)
- `backend/src/test/java/com/vanter/ember/settings/repository/SettingsRepositoryTenantIsolationTest.java` (new)
- `backend/src/test/java/com/vanter/ember/identity/repository/UserRepositoryTenantScopeTest.java` (new)
- `backend/src/test/java/com/vanter/ember/restaurant/repository/RestaurantRepositoryTenantScopeTest.java` (new)
- `backend/src/main/java/com/vanter/ember/settings/model/RestaurantSettings.java` (edited — one line)

## 4. What Changed?

### `AbstractTenantIsolationTest` (shared harness)
`@DataJpaTest` + `@Import(TenantIdentifierResolver.class)` + **`@Transactional(propagation = NOT_SUPPORTED)`**,
plus `TENANT_A`/`TENANT_B` constants, `asTenant(uuid, Runnable)` / `readAs(uuid, Supplier<T>)` helpers that
bind and always clear `TenantContextHolder`, and an `@AfterEach` that calls the subclass's abstract
`deleteAll()` once per tenant.

`NOT_SUPPORTED` is the load-bearing detail. Hibernate resolves the tenant identifier **once, when the
session opens**, so under the transaction `@DataJpaTest` normally wraps around a test (a single shared
`EntityManager`) a mid-test tenant switch has no effect — the test would silently assert nothing.
Disabling the test transaction makes every repository call run in its own transaction and re-read the
`ThreadLocal`, which is exactly how a real HTTP request behaves. The cost is that rows commit rather than
roll back, hence the mandatory per-subclass `deleteAll()` (children before parents, so FKs stay satisfied).

### Per-repository tests (7 tenant-scoped repositories)
Each writes as `TENANT_A` and then asserts from `TENANT_B` that reads come back empty, covering the derived
queries each repository actually exposes:
- **Category:** `findByName`, `existsByName`, `findById`, `findAll`, `count`, `deleteAll`; plus the
  task-2.15 constraint — the same category name is accepted in two tenants.
- **MenuItem:** `findByCategoryId`, `countByCategoryId`, `findByAvailableTrue`, `findById`, `findAll`.
- **Bill:** `findBySessionId`, `findByStatus`, `findById`, `deleteAll`; plus the same `sessionId` in two
  tenants (`uk_bills_tenant_session`).
- **BillSplit:** `findByBillId`, `findByBillIdAndParticipantName`, `findById`, `findAll` — a foreign bill id
  resolves nothing.
- **Payment:** `findByBillId`, `findByStatus`, `findById`.
- **DiningTable / Settings:** these take an explicit `restaurantId` argument *and* are discriminator-filtered,
  so the tests pass **another tenant's id as the argument** and assert the discriminator still wins
  (`countByRestaurantIdAndIsActiveTrue`, `findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc`,
  `findMaxTableNumberByRestaurantId`, `findByRestaurantId`). This is the shape a real IDOR attempt takes.

Every suite also asserts that a save under a bound tenant comes back stamped with that tenant, confirming
`@TenantId` writes as well as reads.

### Non-scoped repositories (regression guards)
`UserRepositoryTenantScopeTest` and `RestaurantRepositoryTenantScopeTest` assert the *opposite*:
`findByEmail`/`existsByEmail` and `findBySlug`/`existsBySlug`/`findById` resolve with **no tenant bound at
all** and under a foreign tenant. Both are looked up before a tenant exists (registration, login,
`jwtAuthFilter`), so adding `@TenantId` to either would 401 every request — these tests fail loudly if
someone "completes" the tenant rollout by annotating them.

### `RestaurantSettings.payload`
Dropped `columnDefinition = "jsonb"` (kept `@JdbcTypeCode(SqlTypes.JSON)`). Verified fix, not cosmetic:
`jsonb` is a PostgreSQL-only type name, so H2's `create-drop` failed on that one table and skipped it —
`restaurant_settings` did not exist in the test schema, which is why `SettingsRepository` had never had a
test. `SqlTypes.JSON` already resolves to `jsonb` on PostgreSQL and to the dialect's own JSON type
elsewhere, so DDL and binding agree on both sides. No Flyway migration is needed: the production column is
already `jsonb` and that is still what Hibernate expects under `ddl-auto=validate`.

## 5. Why It Changed?
Tasks 2.14 and 2.15 wired discriminator multi-tenancy but nothing proved it holds — a dropped `@TenantId`,
a repository method added later, or a resolver regression would leak one restaurant's catalog, bills, or
payments into another's with no failing test. Tenant filtering is a security control, and an unverified
security control is a claim. These tests turn it into a checked invariant, cover the explicit-id query
shape an attacker would actually use, and document the two deliberate exclusions so the next contributor
doesn't "fix" them.

## 6. Verification
`cd backend && ./mvnw test` → **BUILD SUCCESS, Tests run: 348, Failures: 0, Errors: 0** (305 before, +43 new).
