-- EMB-PC-01 — platform_operators: login table for the super-admin console.
--
-- Deliberately has NO foreign key to restaurants or users: platform operators sit entirely
-- outside the tenant data model. Mutual exclusion from tenant auth comes from a separate signing
-- key (platform.jwt.secret, EMB-PC-03/04), not a claim check, so this table must never be joined
-- against tenant-scoped tables.

CREATE TABLE IF NOT EXISTS platform_operators (
    id            uuid PRIMARY KEY,
    name          varchar(255) NOT NULL,
    email         varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    created_at    timestamp(6) with time zone NOT NULL
);

ALTER TABLE platform_operators
    DROP CONSTRAINT IF EXISTS uk_platform_operators_email;
ALTER TABLE platform_operators
    ADD CONSTRAINT uk_platform_operators_email UNIQUE (email);

-- Seed the initial operator so the console has a login before EMB-PC-08's operator-driven tenant
-- onboarding (or any operator-management flow) exists. Password is 'ChangeMe123!', hashed with the
-- same BCryptPasswordEncoder SecurityConfig wires up for tenant auth. This hash is committed to
-- source control and is not a live secret in the usual sense, but it MUST be rotated via
-- EMB-PC-05's self-service password-change endpoint the moment platform auth is live.
INSERT INTO platform_operators (id, name, email, password_hash, created_at)
VALUES (
    gen_random_uuid(),
    'Platform Admin',
    'platform-admin@ember.local',
    '$2a$10$yL9cRGZ5peuOyztoRdLQUemp1I0alVCdzOyxmdqhCZAukOlD1a7wC',
    now()
)
ON CONFLICT (email) DO NOTHING;
