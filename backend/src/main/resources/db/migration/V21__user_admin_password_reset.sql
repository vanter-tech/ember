-- Admin-driven staff password reset (POST /admin/staff/{userId}/reset-password). The new password is
-- NOT temporary (no must_change_password), so these two columns are what enforce and audit the
-- feature: password_reset_at drives the per-user 6-hour cooldown (UserAdminService), and
-- password_reset_by records which admin (email) did it.
--
-- Both nullable, no backfill: null = never reset by an admin. Idempotent (prod Flyway is not
-- baselined), same pattern as V11/V13/V16/V17/V18. Type matches users.created_at (Instant).
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_at timestamp(6) with time zone;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_by varchar(255);
