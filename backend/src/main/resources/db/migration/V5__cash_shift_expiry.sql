ALTER TABLE cash_shifts
    ADD COLUMN expires_at      TIMESTAMP,
    ADD COLUMN prolonged_until TIMESTAMP,
    ADD COLUMN prolonged_by    VARCHAR(255),
    ADD COLUMN prolong_count   INTEGER NOT NULL DEFAULT 0;

-- Give any shift that is OPEN at deploy time a finite deadline so the new
-- guards and the frontend sentinel have something to evaluate.
UPDATE cash_shifts
   SET expires_at = opened_at + INTERVAL '12 hours'
 WHERE status = 'OPEN' AND expires_at IS NULL;
