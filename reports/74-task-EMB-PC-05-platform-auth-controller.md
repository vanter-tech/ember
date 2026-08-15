# Report 74 — EMB-PC-05: `PlatformAuthController`

## 1. Identification
- **Report number:** 74
- **Task ID:** EMB-PC-05
- **Predecessor Task:** EMB-PC-04 (report 73)

## 2. Objective
Give the `/platform/**` chain (EMB-PC-04) an actual entry point: `POST /platform/auth/login` to
mint a `PlatformOperator` JWT, and `PATCH /platform/auth/password` for self-service password
change, throttled the same way tenant `/auth/login` already is.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformLoginRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformAuthResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformPasswordChangeRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformAuthService.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformAuthController.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/config/PlatformSecurityConfig.java` (permitAll carve-out)
- `backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java` (added guarded path)
- `backend/src/test/java/com/vanter/ember/platform/service/PlatformAuthServiceTest.java` (new)
- `backend/src/test/java/com/vanter/ember/platform/controller/PlatformAuthControllerTest.java` (new)

## 4. What Changed?
- `PlatformAuthService.login()` looks up the operator by email via `PlatformOperatorRepository`,
  checks the password with the shared `PasswordEncoder` bean (`SecurityConfig`'s `BCryptPasswordEncoder`,
  same instance the tenant `AuthService` uses), and mints a token through `PlatformJwtService` with
  `role=PLATFORM_ADMIN` and `operatorId` claims. Unknown email or bad password both surface as
  `BadCredentialsException`, which `GlobalExceptionHandler` already maps to 401 — no new exception
  handling needed.
- `PlatformAuthService.changePassword(email, request)` re-verifies `currentPassword` before
  re-encoding and saving `newPassword`; also `BadCredentialsException` on mismatch. It is
  deliberately self-service only — the caller's own email comes from `Authentication.getName()` in
  the controller (set by `PlatformSecurityConfig`'s `platformJwtAuthFilter`), never a path/body
  parameter, so an operator cannot target another operator's account.
- `PlatformAuthController` mirrors `identity/controller/AuthController`'s shape: `POST /login`
  returns the token response, `PATCH /password` takes `Authentication` and returns 204.
- `PlatformSecurityConfig.platformFilterChain` gained
  `.requestMatchers("/platform/auth/login").permitAll()` ahead of `anyRequest().authenticated()` —
  the only unauthenticated route on the `/platform/**` chain; `/platform/auth/password` stays
  behind the existing `authenticated()` catch-all since it needs a caller identity.
- `RateLimitProperties`'s default `paths` list now also guards `/platform/auth/login`. The filter
  (`AuthRateLimiterFilter`) is a servlet-wide `@Component`, not attached to either `SecurityFilterChain`,
  so no separate wiring was needed for the second chain — adding the path to the shared properties
  bean was sufficient.
- New tests: `PlatformAuthServiceTest` (Mockito unit tests — login success/unknown-email/wrong-password,
  password-change success/wrong-current-password) and `PlatformAuthControllerTest` (`@WebMvcTest`
  + `@Import(PlatformSecurityConfig.class)`, mirroring `AuthControllerTest`'s pattern — login
  200/401/400, password-change 401-unauthenticated/204/401-wrong-current, the 204 case driven
  through a real simulated JWT-filter pass with `PlatformJwtService`/`PlatformOperatorDetailsService`
  mocked to authenticate as the operator).

## 5. Why It Changed?
EMB-PC-04 built the `/platform/**` filter chain and its `PlatformJwtAuthFilter`, but there was no
route that could actually issue a token — every request to `/platform/**` was necessarily
unauthenticated and 401'd. This closes that gap so EMB-PC-06 onward (restaurant list/detail,
status toggle, onboarding, audit log) have a real login flow to authenticate against, and gives
operators a way to rotate the seeded `ChangeMe123!` password (EMB-PC-01's seed migration) without a
manual DB update.

## Verification
`cd backend && ./mvnw test` — 553/553 passing (542 pre-existing + 11 new).
