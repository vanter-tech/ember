-- Refunds & Voids (2026-08-17 design spec) — append-only reversal of a CONFIRMED Payment via a
-- new `refunds` table (never mutates `payments`), plus a VOIDED terminal `bills.status` for
-- cancelling a bill before any payment lands. A CLOSED cash_shifts row is never touched
-- retroactively by a refund — see PaymentService#refundPayment, which records the till impact as
-- an ordinary CASH_OUT cash_movements row against whichever shift is open *now*.
--
-- refunded_by/voided_by store users.id directly (varchar(255)), same convention as
-- payments.processed_by / cash_shifts.opened_by (see V7__cash_shifts.sql) — no JPA @ManyToOne to
-- User, which carries a LAZY restaurantId association that risks LazyInitializationException if
-- embedded and serialized here.

CREATE TABLE IF NOT EXISTS refunds (
    id           bigserial PRIMARY KEY,
    tenant_id    uuid NOT NULL,
    payment_id   bigint NOT NULL REFERENCES payments(id),
    amount       numeric(10,2) NOT NULL,
    reason       varchar(255) NOT NULL,
    refunded_by  varchar(255) NOT NULL,
    created_at   timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_refunds_payment ON refunds (payment_id);
CREATE INDEX IF NOT EXISTS idx_refunds_tenant ON refunds (tenant_id);

ALTER TABLE bills ADD COLUMN IF NOT EXISTS voided_by varchar(255);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS voided_at timestamp;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS void_reason varchar(255);

-- A VOIDED bill must free its session for a fresh calculateBill call, so the old all-statuses
-- unique constraint becomes a partial index that only guards non-VOIDED rows — same technique as
-- uk_cash_shifts_tenant_open in V7__cash_shifts.sql.
ALTER TABLE bills DROP CONSTRAINT IF EXISTS uk_bills_tenant_session;
CREATE UNIQUE INDEX IF NOT EXISTS uk_bills_tenant_session_active
    ON bills (tenant_id, session_id)
    WHERE status <> 'VOIDED';

-- bills.status's CHECK constraint predates this migration (baselined from ddl-auto, see
-- application.yml's `hibernate.ddl-auto: update`) and only allows OPEN/PAID. ddl-auto:update never
-- edits an existing constraint when an enum gains a new literal — the exact scenario
-- V3__restaurant_columns_backfill.sql already had to work around for restaurants.status — so
-- VOIDED must be added here explicitly, or a live (non-H2-test) Postgres rejects the very insert
-- this feature exists to make.
ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_status_check;
ALTER TABLE bills ADD CONSTRAINT bills_status_check
    CHECK (status IN ('OPEN', 'PAID', 'VOIDED'));

-- bill_splits.paid (boolean) -> status (UNPAID | PARTIALLY_PAID | PAID): a partial refund can no
-- longer be represented as a single true/false flag.
ALTER TABLE bill_splits ADD COLUMN IF NOT EXISTS status varchar(20);
UPDATE bill_splits SET status = CASE WHEN paid THEN 'PAID' ELSE 'UNPAID' END WHERE status IS NULL;
ALTER TABLE bill_splits ALTER COLUMN status SET NOT NULL;
ALTER TABLE bill_splits DROP COLUMN IF EXISTS paid;
