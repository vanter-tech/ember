-- Cash Register & Daily Shift Management (2026-08-16 design spec) — single shared till per
-- tenant: at most one OPEN cash_shifts row at a time, enforced by the partial unique index below
-- rather than application logic alone. A CLOSED row's financial columns are written exactly once
-- (at close) and never revisited, so that row doubles as the shift's immutable Z-record — there
-- is no separate z_reports table.
--
-- opened_by/closed_by/created_by/processed_by store users.id directly (varchar(255), confirmed
-- against the running database) rather than a JPA @ManyToOne — User carries a LAZY restaurantId
-- association that would risk LazyInitializationException if embedded and serialized here
-- (open-in-view is false), and nothing in this module needs to navigate from a
-- shift/movement/payment back to the full User row in Java, only display a name via a lookup the
-- response-DTO layer performs explicitly.

CREATE TABLE IF NOT EXISTS cash_shifts (
    id                  bigserial PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    shift_number        integer NOT NULL,
    status              varchar(10) NOT NULL,
    opening_float       numeric(10,2) NOT NULL,
    opened_by           varchar(255) NOT NULL,
    opened_at           timestamp NOT NULL,
    closed_by           varchar(255),
    closed_at           timestamp,
    expected_cash       numeric(10,2),
    counted_cash        numeric(10,2),
    variance            numeric(10,2),
    total_cash_sales    numeric(10,2),
    total_digital_sales numeric(10,2),
    total_cash_in       numeric(10,2),
    total_cash_out      numeric(10,2)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cash_shifts_tenant_open
    ON cash_shifts (tenant_id)
    WHERE status = 'OPEN';

CREATE INDEX IF NOT EXISTS idx_cash_shifts_tenant_closed_at ON cash_shifts (tenant_id, closed_at);

CREATE TABLE IF NOT EXISTS cash_movements (
    id            bigserial PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    cash_shift_id bigint NOT NULL REFERENCES cash_shifts(id),
    type          varchar(10) NOT NULL,
    amount        numeric(10,2) NOT NULL,
    reason        varchar(255) NOT NULL,
    created_by    varchar(255) NOT NULL,
    created_at    timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cash_movements_shift ON cash_movements (cash_shift_id);

ALTER TABLE payments ADD COLUMN IF NOT EXISTS cash_shift_id bigint REFERENCES cash_shifts(id);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS processed_by varchar(255);
CREATE INDEX IF NOT EXISTS idx_payments_cash_shift ON payments (cash_shift_id);
