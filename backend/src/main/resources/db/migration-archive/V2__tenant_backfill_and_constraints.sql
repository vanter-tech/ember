-- task-2.15 — Multi-tenancy migration.
--
-- task-2.14 added @TenantId to Category, MenuItem, Bill, BillSplit and Payment (new `tenant_id`
-- column) and to the pre-existing `restaurant_id` of DiningTables/RestaurantSettings. This
-- migration makes the physical schema match: it backfills the new column on rows written before
-- tenancy existed, replaces the legacy GLOBAL unique index on categories(name) with a per-tenant
-- one, adds the per-tenant unique on bills(session_id) that BillingService already enforces in
-- code, and indexes every discriminator column so Hibernate's implicit `tenant_id = ?` predicate
-- is not a sequential scan.
--
-- Baseline note: this database was created by `ddl-auto: update`, so Flyway is configured with
-- baseline-on-migrate/baseline-version=1 and treats the existing schema as V1.

-- ---------------------------------------------------------------------------
-- 1. Columns (idempotent: `ddl-auto: update` may already have created them in dev)
-- ---------------------------------------------------------------------------
ALTER TABLE categories  ADD COLUMN IF NOT EXISTS tenant_id uuid;
ALTER TABLE menu_items  ADD COLUMN IF NOT EXISTS tenant_id uuid;
ALTER TABLE bills       ADD COLUMN IF NOT EXISTS tenant_id uuid;
ALTER TABLE bill_splits ADD COLUMN IF NOT EXISTS tenant_id uuid;
ALTER TABLE payments    ADD COLUMN IF NOT EXISTS tenant_id uuid;

-- ---------------------------------------------------------------------------
-- 2. Backfill
--
-- Pre-tenancy rows carry no evidence of which restaurant they belong to: bills reference a Mongo
-- session id that SQL cannot resolve, and categories/menu items reference nothing at all. The only
-- safe inference is the single-tenant case, so this block backfills from the sole `restaurants`
-- row and refuses to guess otherwise. A deployment with several restaurants and orphan rows must
-- be reconciled by hand before this migration can run.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    tenant_count  bigint;
    orphan_count  bigint;
    sole_tenant   uuid;
BEGIN
    SELECT count(*) INTO orphan_count
    FROM (
        SELECT 1 FROM categories  WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM menu_items  WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM bills       WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM bill_splits WHERE tenant_id IS NULL
        UNION ALL SELECT 1 FROM payments    WHERE tenant_id IS NULL
    ) AS orphans;

    IF orphan_count = 0 THEN
        RETURN;
    END IF;

    SELECT count(*) INTO tenant_count FROM restaurants;

    IF tenant_count <> 1 THEN
        RAISE EXCEPTION
            'V2 cannot backfill tenant_id: % untenanted row(s) found but % restaurant(s) exist. '
            'Backfilling is only unambiguous with exactly one restaurant — assign these rows '
            'manually, then re-run this migration.',
            orphan_count, tenant_count;
    END IF;

    SELECT id INTO sole_tenant FROM restaurants;

    UPDATE categories SET tenant_id = sole_tenant WHERE tenant_id IS NULL;
    UPDATE menu_items SET tenant_id = sole_tenant WHERE tenant_id IS NULL;
    UPDATE bills      SET tenant_id = sole_tenant WHERE tenant_id IS NULL;

    -- Children inherit from their bill rather than from the sole-tenant assumption, so they stay
    -- correct even if a bill was tenanted by some earlier partial backfill.
    UPDATE bill_splits s SET tenant_id = b.tenant_id
    FROM bills b WHERE b.id = s.bill_id AND s.tenant_id IS NULL;

    UPDATE payments p SET tenant_id = b.tenant_id
    FROM bills b WHERE b.id = p.bill_id AND p.tenant_id IS NULL;
END $$;

-- Every write now goes through Hibernate's DISCRIMINATOR stamping, so the column can be sealed.
ALTER TABLE categories  ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE menu_items  ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE bills       ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE bill_splits ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE payments    ALTER COLUMN tenant_id SET NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. Uniqueness — drop the pre-tenancy global constraint on categories(name)
--
-- `@Column(unique = true)` let Hibernate name the constraint, so it is dropped by lookup rather
-- than by a literal name. Only single-column unique constraints over `name` are touched.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    legacy_constraint text;
BEGIN
    FOR legacy_constraint IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'categories'
          AND nsp.nspname = current_schema()
          AND con.contype = 'u'
          AND con.conkey = ARRAY[
              (SELECT attnum FROM pg_attribute
               WHERE attrelid = con.conrelid AND attname = 'name')
          ]
    LOOP
        EXECUTE format('ALTER TABLE categories DROP CONSTRAINT %I', legacy_constraint);
    END LOOP;
END $$;

ALTER TABLE categories
    DROP CONSTRAINT IF EXISTS uk_categories_tenant_name;
ALTER TABLE categories
    ADD CONSTRAINT uk_categories_tenant_name UNIQUE (tenant_id, name);

-- Mirrors BillingService's "Session already billed" guard at the database level.
ALTER TABLE bills
    DROP CONSTRAINT IF EXISTS uk_bills_tenant_session;
ALTER TABLE bills
    ADD CONSTRAINT uk_bills_tenant_session UNIQUE (tenant_id, session_id);

-- ---------------------------------------------------------------------------
-- 4. Tenant indexes
--
-- categories and bills are already covered by the unique constraints above; restaurant_settings
-- by its own unique(restaurant_id).
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_menu_items_tenant  ON menu_items (tenant_id);
CREATE INDEX IF NOT EXISTS idx_bill_splits_tenant ON bill_splits (tenant_id);
CREATE INDEX IF NOT EXISTS idx_payments_tenant    ON payments (tenant_id);
CREATE INDEX IF NOT EXISTS idx_dining_table_tenant ON dining_table (restaurant_id);
