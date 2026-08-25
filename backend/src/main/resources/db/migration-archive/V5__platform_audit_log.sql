-- EMB-PC-02 — platform_audit_log: immutable audit trail for platform-operator actions.
--
-- Deliberately has NO foreign key to platform_operators or restaurants: like platform_operators
-- (V4), this table sits entirely outside the tenant data model. operator_id/operator_email are a
-- snapshot at write time, not a live reference, so entries stay readable even if the operator row
-- is later changed or removed. restaurant_id is nullable since not every platform action targets
-- a specific tenant (e.g. future operator-management actions).

CREATE TABLE IF NOT EXISTS platform_audit_log (
    id             uuid PRIMARY KEY,
    operator_id    uuid NOT NULL,
    operator_email varchar(255) NOT NULL,
    restaurant_id  uuid,
    action         varchar(255) NOT NULL,
    old_value      text,
    new_value      text,
    created_at     timestamp(6) with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_platform_audit_log_restaurant_id
    ON platform_audit_log (restaurant_id);
