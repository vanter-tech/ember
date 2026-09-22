-- F-17: JWTs carried no way to invalidate an already-issued token on demand (deactivation,
-- PIN change, "revoke sessions"). users.token_version backs the new `ver` JWT claim
-- (JwtService.extractTokenVersion) checked on every request by SecurityConfig's jwtAuthFilter.
--
-- Backfill to 0: matches the claim's default for tokens minted before this migration, so every
-- token already in the wild keeps working after deploy (no forced mass logout).
--
-- ADD COLUMN IF NOT EXISTS is idempotent, same pattern as V11/V13/V16.
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER;
UPDATE users SET token_version = 0 WHERE token_version IS NULL;
ALTER TABLE users ALTER COLUMN token_version SET NOT NULL;
ALTER TABLE users ALTER COLUMN token_version SET DEFAULT 0;
