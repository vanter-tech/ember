-- Persists the amount the customer personally paid for this visit (from
-- BillSplit.amount at accrual time) — needed to show payment history on
-- the customer Home loyalty dashboard (see
-- docs/superpowers/specs/2026-08-17-customer-home-loyalty-dashboard-design.md).
-- Nullable: pre-existing rows from before this migration have no
-- recoverable amount.
ALTER TABLE loyalty_transactions ADD COLUMN IF NOT EXISTS amount numeric(10,2);
