# Report 337 — EMB-FEAT-02: `PinAttemptGuard` (in-memory PIN lockout)

## 1. Identification
- **Report number:** 337
- **Task ID:** EMB-FEAT-02
- **Predecessor Task:** EMB-FEAT-01 (report 336 — `pin_hash`/`pin_updated_at` columns + `V6` migration)

## 2. Objective
Add a best-effort brute-force throttle for the upcoming `POST /auth/login/pin` endpoint: an
in-memory, per-email failure counter that locks an account after 5 failed PIN attempts inside a
15-minute rolling window, raising `PinLockedException` until the window rolls off.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/identity/exception/PinLockedException.java` (created)
- `backend/src/main/java/com/vanter/ember/identity/service/PinAttemptGuard.java` (created)
- `backend/src/main/java/com/vanter/ember/identity/config/IdentityClockConfig.java` (created)
- `backend/src/test/java/com/vanter/ember/identity/service/PinAttemptGuardTest.java` (created)

## 4. What Changed?
- **`PinLockedException`** — trivial `RuntimeException` with a fixed message
  (`"Too many failed PIN attempts. Use your password."`). Canonical copy; EMB-FEAT-03 will map it
  to HTTP 423 / `code: PIN_LOCKED`.
- **`PinAttemptGuard`** — `@Component` holding a `ConcurrentHashMap<String, Attempt>` keyed by a
  trimmed, lower-cased email. `Attempt` is a `record(int count, Instant windowStart)`.
  - `assertNotLocked(email)` — throws `PinLockedException` when `count >= 5` and the 15-min window
    has not expired.
  - `recordFailure(email)` — `map.compute`; starts a fresh window (count 1) when absent or expired,
    otherwise increments `count` keeping the original `windowStart`.
  - `recordSuccess(email)` — removes the entry.
  - Constructor takes a `java.time.Clock` so tests can advance time; `MAX_FAILURES = 5`,
    `WINDOW = 15 min` are private constants.
- **`IdentityClockConfig`** — a single `@Bean Clock clock()` returning `Clock.systemUTC()`. The
  project had no `Clock` bean anywhere (`AuthRateLimiterFilter` uses a `LongSupplier`, not
  `java.time.Clock`), so `PinAttemptGuard`'s constructor injection needed one.
- **`PinAttemptGuardTest`** — 4 plain JUnit 5 tests (no Spring context) driving a hand-rolled
  movable `Clock` backed by an `AtomicReference<Instant>`: locks after 5 failures in-window,
  success clears the counter, lock lifts after the window (+16 min), email match is
  case-insensitive.

## 5. Why It Changed?
A PIN is low-entropy (4–6 digits); without throttling the `POST /auth/login/pin` endpoint
(EMB-FEAT-03) would be trivially brute-forceable. An in-memory, node-local counter that resets on
restart is acceptable here because the password login path — with its own existing protections —
remains the fallback, and Ember is a single-node modular monolith. Injecting `Clock` keeps the
window logic unit-testable without `Thread.sleep`.

## Verification
- `./mvnw test` → **BUILD SUCCESS**, `Tests run: 946, Failures: 0, Errors: 0, Skipped: 0`
  (942 baseline + 4 new).
