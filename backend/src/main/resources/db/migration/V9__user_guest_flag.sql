-- Walk-in diners can join a table without an account (see the guest-join spec). A guest is a
-- real users row flagged here; it never earns loyalty points or visits.
ALTER TABLE users ADD COLUMN guest boolean NOT NULL DEFAULT false;
