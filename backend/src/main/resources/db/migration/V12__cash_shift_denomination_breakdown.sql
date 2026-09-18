-- Denomination-count audit trail for the arqueo de caja: how many of each córdoba bill/coin were
-- actually counted at open and close, instead of just a typed-in total. JSON, not a relational
-- child table — matches CashShift's own "no separate report table" design (see its class javadoc).
ALTER TABLE cash_shifts ADD COLUMN IF NOT EXISTS opening_breakdown jsonb;
ALTER TABLE cash_shifts ADD COLUMN IF NOT EXISTS closing_breakdown jsonb;
ALTER TABLE cash_shifts ADD COLUMN IF NOT EXISTS close_notes text;
