# Report 336 — EMB-FEAT-01: `pin_hash` columns on `users`

## 1. Identification
- **Report number:** 336
- **Current Task:** EMB-FEAT-01 (Task 1 of `docs/superpowers/plans/2026-09-03-waiter-quick-login.md`), branch `feat/waiter-quick-login-table-actions` off `main`
- **Predecessor Task:** EMB-FEAT-00 / report 335 (`fix-console-logout-blank-redirect`)

## 2. Objective
Add the persistence layer for the waiter quick-login PIN: two nullable columns on `users`
(`pin_hash`, `pin_updated_at`) and their JPA mappings, so later EMB-FEAT tasks can store and
verify a BCrypt-hashed 4–6 digit PIN.

## 3. Modified Files
- `backend/src/main/resources/db/migration/V6__user_pin.sql` (new)
- `backend/src/main/java/com/vanter/ember/identity/model/User.java`
- `backend/src/test/java/com/vanter/ember/identity/model/UserPinColumnsTest.java` (new)

## 4. What Changed?
- **`V6__user_pin.sql`:** `ALTER TABLE users ADD COLUMN pin_hash VARCHAR(60)` and
  `ADD COLUMN pin_updated_at TIMESTAMP`. Both nullable — the overwhelming majority of users
  never set a PIN. `VARCHAR(60)` is the BCrypt digest width.
- **`User.java`:** added `@JsonIgnore @Column(name = "pin_hash", length = 60) private String pinHash;`
  and `@Column(name = "pin_updated_at") private Instant pinUpdatedAt;` immediately after the
  `pendingHours` field. `@JsonIgnore` and `java.time.Instant` were already imported.
- **`UserPinColumnsTest.java`:** a `@DataJpaTest` (`@Import(TenantIdentifierResolver.class)`, per the
  project-wide `@TenantId` requirement) that saves a `User` with both PIN fields set and asserts
  they round-trip through `findById`.

## 5. Why It Changed?
The quick-login feature (EMB-FEAT-02..10) needs a server-side credential distinct from the
password: a short PIN a waiter can tap on a shared floor tablet. Storing its BCrypt hash on the
existing `User` row (rather than a side table) keeps the login path a single lookup and reuses the
already-configured `PasswordEncoder`. `pin_updated_at` is recorded for future auditing / forced
re-enrolment. `@JsonIgnore` guarantees the hash is never serialized in any `User` response.

## 6. Verification
- `./mvnw test -Dtest=UserPinColumnsTest` — `Tests run: 1, Failures: 0, Errors: 0`.
- `./mvnw test` (full suite) — `Tests run: 942, Failures: 0, Errors: 0` — `BUILD SUCCESS`
  (was 941 before this task; +1 new test).
</content>
