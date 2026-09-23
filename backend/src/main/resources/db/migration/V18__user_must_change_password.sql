-- F-25 (password recovery): platform operators can now reset a locked-out restaurant ADMIN's
-- password (POST /platform/restaurants/{id}/reset-admin-password). users.must_change_password
-- flags that the current password is an operator-issued temp one, so the frontend forces the
-- user through a "set a new password" modal on their next login before they can use the app.
--
-- Backfill to false: no existing user has a pending forced reset. Idempotent, same pattern as
-- V11/V13/V16/V17.
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN;
UPDATE users SET must_change_password = false WHERE must_change_password IS NULL;
ALTER TABLE users ALTER COLUMN must_change_password SET NOT NULL;
ALTER TABLE users ALTER COLUMN must_change_password SET DEFAULT false;
