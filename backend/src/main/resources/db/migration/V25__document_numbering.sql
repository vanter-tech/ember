-- Per-tenant consecutive numbering of bills and kitchen tickets, printed as e.g. ELPO-000123 / ELPO-KDS-000045.
-- Idempotent (prod Flyway is not baselined, see V9/V11). Prod runs ddl-auto=validate.
ALTER TABLE bills ADD COLUMN IF NOT EXISTS bill_number integer;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS bill_code varchar(24);
ALTER TABLE kitchen_orders ADD COLUMN IF NOT EXISTS ticket_number integer;
ALTER TABLE kitchen_orders ADD COLUMN IF NOT EXISTS ticket_code varchar(24);

CREATE TABLE IF NOT EXISTS document_counters (
    tenant_id   uuid        NOT NULL,
    series      varchar(16) NOT NULL,
    prefix      varchar(8)  NOT NULL,
    last_number integer     NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, series)
);

-- Backfill: existing bills are numbered per tenant in creation order, after any number already assigned.
-- Prefix = first 4 letters/digits of the slug (same rule as DocumentCodes.prefixFromSlug).
WITH base AS (
    SELECT tenant_id, COALESCE(MAX(bill_number), 0) AS last_used
    FROM bills
    GROUP BY tenant_id
), numbered AS (
    SELECT b.id,
           base.last_used + ROW_NUMBER() OVER (PARTITION BY b.tenant_id ORDER BY b.created_at, b.id) AS n
    FROM bills b
    JOIN base ON base.tenant_id = b.tenant_id
    WHERE b.bill_number IS NULL
)
UPDATE bills
SET bill_number = numbered.n
FROM numbered
WHERE bills.id = numbered.id;

INSERT INTO document_counters (tenant_id, series, prefix, last_number)
SELECT b.tenant_id,
       'BILL',
       COALESCE(NULLIF(LEFT(UPPER(REGEXP_REPLACE(r.slug, '[^a-zA-Z0-9]', '', 'g')), 4), ''), 'EMBR'),
       MAX(b.bill_number)
FROM bills b
JOIN restaurants r ON r.id = b.tenant_id
GROUP BY b.tenant_id, r.slug
ON CONFLICT (tenant_id, series) DO UPDATE
    SET last_number = GREATEST(document_counters.last_number, EXCLUDED.last_number);

UPDATE bills
SET bill_code = dc.prefix || '-' || LPAD(bills.bill_number::text, 6, '0')
FROM document_counters dc
WHERE dc.tenant_id = bills.tenant_id
  AND dc.series = 'BILL'
  AND bills.bill_code IS NULL
  AND bills.bill_number IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bills_tenant_number ON bills (tenant_id, bill_number);
