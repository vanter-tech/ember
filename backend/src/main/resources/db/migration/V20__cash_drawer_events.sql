-- EMB-DRAWER: pending cash receipts (one per confirmed physical payment) and manual drawer
-- openings. Idempotent (prod Flyway is not baselined, see V9/V11). Prod runs ddl-auto=validate,
-- so the column list must match CashDrawerEvent exactly.
CREATE TABLE IF NOT EXISTS cash_drawer_events (
    id uuid NOT NULL PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    tenant_id uuid NOT NULL,
    type varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    payment_id bigint,
    cash_shift_id bigint,
    table_number integer,
    amount numeric(10,2),
    reason varchar(255),
    created_by varchar(255),
    received_by varchar(255),
    received_at timestamp(6) without time zone,
    print_job_id uuid,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT cash_drawer_events_type_check CHECK (type IN ('CASH_SALE', 'MANUAL')),
    CONSTRAINT cash_drawer_events_status_check CHECK (status IN ('PENDING', 'RECEIVED'))
);
CREATE INDEX IF NOT EXISTS idx_cash_drawer_events_tenant_status ON cash_drawer_events (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_cash_drawer_events_shift ON cash_drawer_events (cash_shift_id);
-- One pending receipt per payment: a duplicated event can never double-record the same cash.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cash_drawer_events_payment
    ON cash_drawer_events (payment_id) WHERE payment_id IS NOT NULL;
