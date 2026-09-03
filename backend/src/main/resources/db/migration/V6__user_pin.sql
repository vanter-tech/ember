-- Quick-login PIN: BCrypt hash of a 4-6 digit PIN, nullable (most users never set one).
ALTER TABLE users ADD COLUMN pin_hash VARCHAR(60);
ALTER TABLE users ADD COLUMN pin_updated_at TIMESTAMP;
