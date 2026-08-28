# Report 268 — HEARTBEAT-04: cloud `HubHeartbeatController` + security + rate-limit

## 1. Identification
- **Report number:** 268
- **Current Task ID:** HEARTBEAT-04 (Task 4 of the 7-task License Heartbeat plan, `docs/superpowers/plans/2026-08-28-hub-license-heartbeat.md`)
- **Predecessor Task:** HEARTBEAT-03 (report 267 — `HubHeartbeatService` + DTOs)

## 2. Objective
Expose Task 3's `HubHeartbeatService` over HTTP as a public, cloud-only endpoint so a Hub can POST its periodic license heartbeat: `POST /hub-heartbeat`, `permitAll`, `@Profile("!hub")`, JSON in/out, HTTP 400 on validation failure or `InvalidLicenseException`.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/licensing/controller/HubHeartbeatController.java` (new)
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- `backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java`
- `backend/src/test/java/com/vanter/ember/licensing/controller/HubHeartbeatControllerTest.java` (new)
- `PROGRESS.md`
- `reports/268-heartbeat-04-hub-heartbeat-controller.md` (this file)

## 4. What Changed?
- **`HubHeartbeatController`** — `@RestController @RequestMapping("/hub-heartbeat") @RequiredArgsConstructor @Profile("!hub")`. Single `@PostMapping` taking `@Valid @RequestBody HubHeartbeatRequest`, delegating to `hubHeartbeatService.heartbeat(request)` and returning `ResponseEntity.ok(...)`. Declares `throws InvalidLicenseException` (already mapped to 400 by `GlobalExceptionHandler`, per plan Global Constraints). Structure mirrors `HubActivationController` verbatim.
- **`SecurityConfig`** — added `.requestMatchers("/hub-heartbeat").permitAll()` immediately after the existing `/hub-activations` entry, with a short comment noting it authenticates via the license signature (`HubHeartbeatService`), not a bearer token.
- **`RateLimitProperties`** — appended `"/hub-heartbeat"` to the default `paths` list (now `/auth/login`, `/auth/register`, `/platform/auth/login`, `/hub-activations`, `/hub-heartbeat`), so the unauthenticated endpoint is throttled by `AuthRateLimiterFilter`.
- **`HubHeartbeatControllerTest`** — `@WebMvcTest(HubHeartbeatController.class)` + `@Import({SecurityConfig, CorsConfig})`, mirroring `HubActivationControllerTest`. 3 cases: no auth header still reaches 200 with `$.status == "OK"`; missing body fields → 400; service throwing `InvalidLicenseException` → 400.
- **No `SecurityAuditTest` change** — it is a protected-route 401 matrix (asserts routes 401 without auth); it does not enumerate `permitAll` routes (`/hub-activations` is likewise absent from it), so the plan's conditional edit does not apply.

## 5. Why It Changed?
The heartbeat needs a cloud reachable HTTP surface. It must be `permitAll` because the caller is an offline Hub with no user JWT — it proves identity by the RSA-signed license the request carries, verified inside `HubHeartbeatService`. `@Profile("!hub")` keeps the endpoint from also booting on a Hub install (which runs the same jar), matching the `HubActivationController` isolation precedent. It is added to the rate-limit path list for the same reason as the other unauthenticated endpoints: an un-throttled public POST is an abuse vector.

## 6. Verification
- `cd backend && ./mvnw test -Dtest=HubHeartbeatControllerTest,SecurityAuditTest` → `Tests run: 78, Failures: 0, Errors: 0` (3 new + 75 existing), BUILD SUCCESS.
- `cd backend && ./mvnw test` (full suite) → `Tests run: 885, Failures: 0, Errors: 0, Skipped: 0` (882 prior + 3 new), BUILD SUCCESS. No `LicenseIssuingService` eager-construction regression in any full-context test.
