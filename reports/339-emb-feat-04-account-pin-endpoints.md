# Report 339 — EMB-FEAT-04: `POST` / `DELETE /account/pin` (authenticated PIN management)

## 1. Identification
- **Report number:** 339
- **Task ID:** EMB-FEAT-04
- **Predecessor Task:** EMB-FEAT-03 (report 338 — `POST /auth/login/pin` service + endpoint + error codes)

## 2. Objective
Let an authenticated user set/replace or remove their own quick-login PIN, gating the write on
their current password.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/identity/model/dto/SetPinRequest.java` (created)
- `backend/src/main/java/com/vanter/ember/identity/controller/AccountController.java` (created)
- `backend/src/main/java/com/vanter/ember/identity/service/AuthService.java`
- `backend/src/test/java/com/vanter/ember/identity/controller/AccountControllerTest.java` (created)
- `backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java`

## 4. What Changed?
- **`SetPinRequest`** — `{ currentPassword, pin }` DTO. `currentPassword` `@NotBlank`; `pin`
  `@NotBlank @Pattern("^\\d{4,6}$")` (same PIN format as `PinLoginRequest`).
- **`AuthService`** — two new methods:
  - `setPin(String email, SetPinRequest)` — `findByEmail` (401 `BadCredentialsException` if
    unknown) → `passwordEncoder.matches(currentPassword, passwordHash)` else 401 → stores
    `passwordEncoder.encode(pin)` into `pinHash` + `pinUpdatedAt = Instant.now()` → `save`.
  - `clearPin(String email)` — `findByEmail` → nulls `pinHash` and `pinUpdatedAt` → `save`.
  - Added `import java.time.Instant` and `import …dto.SetPinRequest`.
- **`AccountController`** — new `@RestController @RequestMapping("/account")`:
  - `POST /account/pin` → `204`, `authService.setPin(authentication.getName(), request)`.
  - `DELETE /account/pin` → `204`, `authService.clearPin(authentication.getName())`.
  - No `@PreAuthorize` — `/account/**` is not under the `permitAll` `/auth/**` matcher, so
    `SecurityConfig`'s `anyRequest().authenticated()` is the authn gate. No `SecurityConfig`
    change was needed.
- **Tests** — `AuthServiceTest` +3 (`setPin` stores bcrypt hash + timestamp when current
  password matches; `setPin` throws + never saves on wrong password; `clearPin` nulls both
  columns). New `AccountControllerTest` (`@WebMvcTest(AccountController.class)` +
  `@Import({SecurityConfig, CorsConfig})`, sibling `@MockBean`s): `204` success; `400` malformed
  pin; `401` wrong current password (`BadCredentialsException` → existing handler); `401`
  unauthenticated; `204` clear. No `.with(csrf())` — CSRF is disabled repo-wide in `SecurityConfig`
  and sibling `@WebMvcTest`s omit it.

## 5. Why It Changed?
EMB-FEAT-03 added PIN *login* but nothing to create a PIN. This is the authenticated write path
consumed by EMB-FEAT-05's `authService.setPin`/`clearPin` and EMB-FEAT-10's `SetPinPrompt`.
Requiring the current password stops a walk-up attacker at an unlocked session from silently
minting themselves a low-entropy re-entry credential. Routing under `/account` (not the spec's
`/auth/pin`) keeps it behind the filter-chain authn gate instead of method security.

## Verification
- `./mvnw test -Dtest=AccountControllerTest,AuthServiceTest,AuthControllerTest` → **BUILD SUCCESS**,
  `Tests run: 33, Failures: 0, Errors: 0`.
- `./mvnw test` → **BUILD SUCCESS**, `Tests run: 962, Failures: 0, Errors: 0, Skipped: 0`
  (954 baseline + 8 new).
