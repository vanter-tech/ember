# Report 338 — EMB-FEAT-03: `POST /auth/login/pin` (service + endpoint + error codes)

## 1. Identification
- **Report number:** 338
- **Task ID:** EMB-FEAT-03
- **Predecessor Task:** EMB-FEAT-02 (report 337 — `PinAttemptGuard` in-memory PIN lockout)

## 2. Objective
Expose a quick-login endpoint that trades a 4–6 digit PIN for the exact same JWT as password
login, throttled by `PinAttemptGuard` and returning machine-readable `code` bodies for the
"no PIN set" and "locked out" cases.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/identity/model/dto/PinLoginRequest.java` (created)
- `backend/src/main/java/com/vanter/ember/identity/exception/PinNotSetException.java` (created)
- `backend/src/main/java/com/vanter/ember/identity/service/AuthService.java`
- `backend/src/main/java/com/vanter/ember/identity/controller/AuthController.java`
- `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`
- `backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java`
- `backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java`
- `backend/src/test/java/com/vanter/ember/identity/controller/AuthControllerTest.java`

## 4. What Changed?
- **`PinLoginRequest`** — `{ email, pin }` DTO. `email` `@NotBlank @Email`; `pin` `@NotBlank`
  `@Pattern("^\\d{4,6}$")`.
- **`PinNotSetException`** — trivial `RuntimeException` ("No PIN is set for this account.").
- **`AuthService`** — new `private final PinAttemptGuard pinAttemptGuard` (picked up by the Lombok
  `@RequiredArgsConstructor`). New `loginWithPin(PinLoginRequest)`: `assertNotLocked(email)` →
  `findByEmail` (401 `BadCredentialsException` if unknown) → `PinNotSetException` when
  `pinHash == null` → on wrong PIN **or** inactive user, `recordFailure(email)` + 401 → otherwise
  `recordSuccess(email)` and return `buildResponse(user, tenantIdOf(user))` — the identical
  `AuthResponse` shape (and tenant-scoping rules) as password `login`.
- **`AuthController`** — `POST /auth/login/pin` → `200` + `AuthResponse`, delegating to
  `authService.loginWithPin`.
- **`GlobalExceptionHandler`** — two handlers next to `handleBadCredentials`:
  `PinNotSetException` → `409` + `code: PIN_NOT_SET`; `PinLockedException` → `423` (`HttpStatus.LOCKED`)
  + `code: PIN_LOCKED`. Same `ProblemDetail.setProperty("code", …)` convention as
  `CASH_SHIFT_OVERDUE`. Wrong-PIN stays `401` via the existing `BadCredentialsException` handler.
- **`RateLimitProperties`** — `/auth/login/pin` added to the guarded `paths` default list so the
  new endpoint is bucketed by `(tenant, IP)` like `/auth/login`.
- **Tests** — `AuthServiceTest`: `@Mock PinAttemptGuard` + 4 cases (matches → same claims as
  password & guard asserted/cleared; `pinHash == null` → `PinNotSetException`; wrong PIN →
  `recordFailure` + `BadCredentialsException`; inactive user → `BadCredentialsException`).
  `AuthControllerTest`: 4 cases (`200` happy path; `409`/`PIN_NOT_SET`; `423`/`PIN_LOCKED`;
  `400` on malformed pin `"12"` via bean validation).

## 5. Why It Changed?
The quick-login UI needs an endpoint that (a) issues a normal session token so no downstream code
knows PIN login happened, (b) is brute-force-throttled given the PIN's low entropy, and (c) tells
the frontend *why* it failed so `QuickLoginModal` (EMB-FEAT-09) can fall back to password on
`PIN_NOT_SET`/`PIN_LOCKED` rather than showing a generic error. Reusing `buildResponse` keeps the
claim set and customer/staff tenant rules in one place.

## Verification
- `./mvnw test -Dtest=AuthServiceTest,AuthControllerTest,PinAttemptGuardTest` → **BUILD SUCCESS**,
  `Tests run: 29, Failures: 0, Errors: 0`.
- `./mvnw test` → **BUILD SUCCESS**, `Tests run: 954, Failures: 0, Errors: 0, Skipped: 0`
  (946 baseline + 8 new).
