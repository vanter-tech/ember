# Report 23 — task-2.15: tenant_id backfill migration, per-tenant constraints, tenant indexes

## 1. Identification
- **Report Number:** 23
- **Task ID:** task-2.15
- **Predecessor Task:** task-2.14 (report 22 — Hibernate DISCRIMINATOR multi-tenancy)

## 2. Objective
Make the physical PostgreSQL schema match the `@TenantId` model introduced in task-2.14: backfill
`tenant_id` on rows written before tenancy existed, replace the legacy **global** unique index on
`categories(name)` with a per-tenant one, add `unique(tenant_id, session_id)` on `bills`, and index
every discriminator column.

## 3. Modified Files
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V2__tenant_backfill_and_constraints.sql` (new)
- `backend/src/test/resources/application.properties`
- `backend/src/main/java/com/vanter/ember/catalog/model/Category.java`
- `backend/src/main/java/com/vanter/ember/catalog/model/MenuItem.java`
- `backend/src/main/java/com/vanter/ember/billing/model/Bill.java`
- `backend/src/main/java/com/vanter/ember/billing/model/BillSplit.java`
- `backend/src/main/java/com/vanter/ember/billing/model/Payment.java`
- `backend/src/main/java/com/vanter/ember/settings/model/DiningTables.java`

## 4. What Changed?

### 4.1 Migration runner (Flyway)
The project had no migration tooling: dev ran `ddl-auto: update`, prod runs
`ddl-auto: ${DDL_AUTO:validate}`. Neither can backfill data or drop an existing constraint, so
`flyway-core` (+ `flyway-database-postgresql` at runtime scope) was added; versions come from the
Spring Boot 3.5.14 BOM. Because the schema already exists and was built by Hibernate, Flyway is
configured in `application.yml` with `baseline-on-migrate: true` / `baseline-version: 1`: the
current state is adopted as V1 and versioned migrations start at V2.

Flyway is disabled in the test profile (`spring.flyway.enabled=false`). The test schema is H2 with
`create-drop`, generated from the entity mappings; the migration is PostgreSQL-specific
(`DO $$ … $$` blocks, `pg_constraint` introspection) and would not parse there.

### 4.2 `V2__tenant_backfill_and_constraints.sql`
1. **Columns** — `ADD COLUMN IF NOT EXISTS tenant_id uuid` on `categories`, `menu_items`, `bills`,
   `bill_splits`, `payments` (idempotent, since `ddl-auto: update` may already have added them in
   dev).
2. **Backfill** — a `DO` block counts untenanted rows; if there are none it returns immediately.
   Otherwise it requires **exactly one** row in `restaurants` and assigns it to
   `categories`/`menu_items`/`bills`. `bill_splits` and `payments` derive their tenant from their
   parent `bill` via `bill_id` rather than from the sole-tenant assumption, so they stay correct
   under a partial earlier backfill. Any other shape (0 or >1 restaurants with orphan rows) raises
   an exception naming the row and restaurant counts.
3. **NOT NULL** — the five columns are sealed once backfilled.
4. **Uniqueness** — the pre-tenancy constraint from `@Column(unique = true)` on `Category.name` was
   Hibernate-named, so it is dropped by lookup in `pg_constraint` (single-column unique over `name`
   only) rather than by literal name. Then `uk_categories_tenant_name UNIQUE (tenant_id, name)` and
   `uk_bills_tenant_session UNIQUE (tenant_id, session_id)` are added, each preceded by
   `DROP CONSTRAINT IF EXISTS` for re-runnability.
5. **Indexes** — `idx_menu_items_tenant`, `idx_bill_splits_tenant`, `idx_payments_tenant`,
   `idx_dining_table_tenant (restaurant_id)`. `categories` and `bills` are already covered by their
   unique constraints, `restaurant_settings` by its own `unique(restaurant_id)`.

### 4.3 Entity mappings mirrored
So the H2 test schema and prod `ddl-auto=validate` agree with the migration:
- `Category`: `unique = true` removed from `name`; `@Table(uniqueConstraints = @UniqueConstraint("uk_categories_tenant_name", {"tenant_id","name"}))`.
- `Bill`: `@Table(uniqueConstraints = @UniqueConstraint("uk_bills_tenant_session", {"tenant_id","session_id"}))`.
- `MenuItem`, `BillSplit`, `Payment`, `DiningTables`: matching `@Index` declarations.
- `tenant_id` marked `nullable = false` on all five `@TenantId` columns.

## 5. Why It Changed?
- **The global unique on `categories.name` was a live cross-tenant bug.** With task-2.14's
  discriminator filtering, `CategoryService.existsByName` is already scoped per tenant and reports
  "available", but the database would then reject the insert because another restaurant happens to
  own a category with that name. Tenant B could also probe tenant A's catalog by observing which
  names fail. `unique(tenant_id, name)` restores both correctness and isolation.
- **`unique(tenant_id, session_id)` on `bills`** promotes `BillingService`'s "Session already
  billed" guard from a check-then-act (racy under concurrent bill requests) to a database
  invariant.
- **Backfill refuses to guess.** Pre-tenancy rows carry no evidence of ownership — a bill points at
  a Mongo session id SQL cannot resolve, and categories reference nothing. Only the single-tenant
  case is unambiguous, so multi-restaurant deployments fail loudly instead of silently handing one
  tenant another's catalog.
- **Indexes** keep Hibernate's implicit `tenant_id = ?` predicate — now on every read of these
  tables — off sequential scans.

## 6. Verification
- `cd backend && ./mvnw test` → **305/305 passing, BUILD SUCCESS** (no regressions vs. task-2.14).
- The migration was dry-run against a clone of the live database
  (`CREATE DATABASE ember_v2_check TEMPLATE ember`): applied cleanly, backfilled 10/10 categories,
  produced exactly `uk_categories_tenant_name` + `uk_bills_tenant_session` and the four indexes,
  and re-ran a second time with exit 0 (idempotent). The clone was then dropped; the live `ember`
  database is untouched and will pick up V2 on the next application boot.
