# Report 22 — task-2.14: Hibernate DISCRIMINATOR multi-tenancy

## 1. Identification
- **Report number:** 22
- **Task ID:** task-2.14
- **Predecessor task:** task-2.13 (report 21)

## 2. Objective
Configure Hibernate DISCRIMINATOR multi-tenancy driven by a `CurrentTenantIdentifierResolver`, and mark the JPA entities that hold tenant-owned data with `@TenantId`, so tenant filtering happens inside the ORM instead of depending on every query author remembering to scope by restaurant.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/TenantIdentifierResolver.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/model/Category.java`
- `backend/src/main/java/com/vanter/ember/catalog/model/MenuItem.java`
- `backend/src/main/java/com/vanter/ember/billing/model/Bill.java`
- `backend/src/main/java/com/vanter/ember/billing/model/BillSplit.java`
- `backend/src/main/java/com/vanter/ember/billing/model/Payment.java`
- `backend/src/main/java/com/vanter/ember/settings/model/DiningTables.java`
- `backend/src/main/java/com/vanter/ember/settings/model/RestaurantSettings.java`
- `backend/src/test/java/com/vanter/ember/E2EOrderFlowTest.java`
- `backend/src/test/java/com/vanter/ember/billing/repository/BillRepositoryTest.java`
- `backend/src/test/java/com/vanter/ember/billing/repository/BillSplitRepositoryTest.java`
- `backend/src/test/java/com/vanter/ember/billing/repository/PaymentRepositoryTest.java`
- `backend/src/test/java/com/vanter/ember/catalog/repository/CategoryRepositoryTest.java`
- `backend/src/test/java/com/vanter/ember/catalog/repository/MenuItemRepositoryTest.java`

## 4. What Changed?

### 4.1 Tenant identifier resolver
Added `TenantIdentifierResolver`, a `@Component` implementing both
`CurrentTenantIdentifierResolver<UUID>` and `HibernatePropertiesCustomizer`. It reads the
tenant from `TenantContextHolder` (populated from the JWT `rid` claim by `jwtAuthFilter` and
`JwtChannelInterceptor`, per task-2.11) and registers itself into Hibernate via
`AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER`.

Hibernate rejects a null tenant identifier outright (`HibernateException: SessionFactory
configured for multi-tenancy, but no tenant identifier specified`), so unbound contexts —
login/registration on permitAll `/auth/**`, repository bootstrap, schema generation — resolve
to a `NO_TENANT` sentinel (`UUID(0,0)`). The sentinel owns no real rows, so an unbound context
reads and writes an empty partition rather than seeing another tenant's data.

`validateExistingCurrentSessions()` returns `false`: the app is stateless per request and
`TenantContextHolder` is cleared in a `finally` block, so there is no long-lived session whose
tenant could drift.

### 4.2 Entity annotations
Two shapes, depending on whether the entity already carried a tenant column:

- **New column** — `Category`, `MenuItem`, `Bill`, `BillSplit`, `Payment` gained
  `@TenantId @Column(name = "tenant_id", updatable = false) private UUID tenantId;`.
- **Existing column reused** — `DiningTables` and `RestaurantSettings` already had a
  `restaurant_id` UUID field, so `@TenantId` was placed directly on it. No duplicate column,
  and existing queries such as
  `findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(...)` keep working (now doubly
  filtered, which is harmless).

`updatable = false` on every tenant column prevents a row from being reassigned to a different
tenant through an ordinary entity update.

Hibernate now auto-stamps the tenant on insert and appends an implicit tenant predicate to
every query, load, and collection fetch for these seven entities.

### 4.3 Test adaptations
- `E2EOrderFlowTest.setUp()` seeds `DiningTables`, `Category`, and `MenuItem` straight through
  repositories/services with no HTTP request in flight, so no tenant was bound. Those rows
  would have landed on the `NO_TENANT` sentinel and been invisible to the JWT-scoped requests
  under test. The test now binds `TenantContextHolder.setTenantId(restaurant.getId())`
  immediately after the restaurant is persisted, with an `@AfterEach` that clears it.
- The five `@DataJpaTest` repository slices needed `@Import(TenantIdentifierResolver.class)`.
  `@DataJpaTest` does not component-scan `@Component` beans, so the resolver was missing from
  the slice while `@TenantId` still switched multi-tenancy on — every JPA-slice context failed
  to start on `userRepository` bootstrap. The full application context component-scans the
  resolver normally, so production wiring needed no change.

## 5. Why It Changed?
Before this task, tenant isolation on the JPA side was enforced by hand: each service had to
remember to filter by restaurant, and tasks 2.9/2.12/2.13 each fixed one place where that was
forgotten — including a live cross-tenant IDOR. Discriminator multi-tenancy moves the
guarantee into the ORM, so a forgotten `WHERE` clause is no longer a data leak. It also gives
task-2.15 (backfill + composite unique constraints) and task-2.16 (isolation regression tests)
a real column to work against.

Reusing the existing `restaurant_id` column on `DiningTables`/`RestaurantSettings` keeps the
schema honest — one tenant column per table, not a `tenant_id` shadowing a `restaurant_id`
that means the same thing.

## 6. Deviation from the task description — `User` excluded (approved)
The task text listed `User` among the entities to annotate. It was deliberately left out, with
the user's approval, for two independent reasons:

1. **Type mismatch.** `User.restaurantId` is a `@ManyToOne Restaurant` association, not a basic
   attribute. `@TenantId` requires a basic type, so annotating `User` would mean adding a
   second, redundant `tenant_id` UUID column alongside the existing FK.
2. **It would break all authentication.** `SecurityConfig.java:95` calls
   `loadUserByUsername(email)` *before* `TenantContextHolder.setTenantId(...)` at line 101, and
   `AuthService.login`'s `findByEmail` runs on permitAll `/auth/**` where no tenant is ever
   bound. With `@TenantId` on `User`, both lookups would filter to the `NO_TENANT` sentinel and
   return empty — every login and every authenticated request would 401.

`User.email` is globally unique and `User` is already tenant-bound through its `restaurant_id`
FK, so it is a global-lookup entity by design. Making it tenant-filtered is a separate piece of
work: user lookup would have to move to `(email, tenant)` and login would need to resolve the
tenant from a slug/subdomain before touching the user table.

## 7. Deferred / follow-up notes
- `Category.name` keeps its global `unique = true`. The composite `unique(tenant_id, name)` is
  task-2.15's scope; until it lands, two tenants cannot both have a category with the same
  name.
- With `ddl-auto: update`, PostgreSQL will add the new `tenant_id` columns but leave existing
  rows null — those rows become invisible to every tenant until task-2.15's backfill runs.
- Hibernate generated the `tenant_id` columns as `not null`. Task-2.15's backfill must populate
  them before the constraint can be satisfied on a non-empty database.
- The pre-existing H2 `jsonb` DDL warning on `restaurant_settings` is unrelated and unchanged;
  Hibernate logs it and continues.

## 8. Verification
`cd backend && ./mvnw test` → **BUILD SUCCESS**, `Tests run: 305, Failures: 0, Errors: 0,
Skipped: 0`. Baseline (305/305) maintained.
