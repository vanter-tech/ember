# Report 77 — EMB-PC-08

## 1. Identification
- **Report:** 77
- **Task ID:** EMB-PC-08
- **Predecessor Task:** EMB-PC-07 (report 76)

## 2. Objective
Add `POST /platform/restaurants` — operator-driven tenant onboarding that creates a `Restaurant` and its initial `ADMIN` `User` in one request, and writes a `PlatformAuditLog` entry, following the same operator-resolve + audit pattern established by EMB-PC-07's `updateStatus`.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantCreateRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java`
- `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`
- `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java`

## 4. What Changed?
- New `PlatformRestaurantCreateRequest` DTO: `name`, `slug` (lowercase-hyphen `@Pattern`), `adminName`, `adminEmail` (`@Email`), `adminPassword` (same complexity `@Pattern`/`@Size` as `RegisterRequest`).
- `PlatformRestaurantService.create(request, operatorEmail)` (new `@Transactional` method): resolves the calling `PlatformOperator` via `authentication.getName()` (same `BadCredentialsException` guard as `updateStatus`); rejects with `IllegalArgumentException` (409, via existing `GlobalExceptionHandler` mapping) on `existsBySlug`/`existsByEmail`; saves the `Restaurant` (plan/status/timezone/currency all take their entity defaults — FREE/ACTIVE/UTC/USD); saves the `User` (`role=ADMIN`, `restaurantId` set to the saved restaurant, `passwordHash` via the shared `PasswordEncoder` bean); writes one `PlatformAuditLog` row (`action=RESTAURANT_CREATED`, `newValue`=slug) in the same transaction; returns `PlatformRestaurantSummaryResponse`.
- `PlatformRestaurantController.create(...)`: `@PostMapping`, `@Valid @RequestBody`, `Authentication`, delegates straight through, `200 OK` (matches this controller's existing convention on `updateStatus`).
- Test additions: service-level tests for success (asserts saved `User` fields + audit log fields), duplicate slug, duplicate email, unknown operator; controller-level tests for 401 (no auth header), 400 (missing fields), 200 (success), 409 (duplicate slug via service exception).

## 5. Why It Changed?
EMB-PC-08 is the next item in the Platform/Super-Admin Console backlog (PROGRESS.md) and the first restaurant-creation code path in the codebase — no prior onboarding/signup flow existed to create a `Restaurant`. The password strategy (operator supplies the initial ADMIN password directly in the request, validated with the existing complexity regex) was chosen over server-generated or invite/reset-token flows because no email/invite infrastructure exists yet in this codebase, and it required no new infra while staying consistent with `RegisterRequest`'s existing validation pattern.

## Verification
- `./mvnw test -Dtest=PlatformRestaurantServiceTest,PlatformRestaurantControllerTest` — PASSING.
- `./mvnw test` (full suite) — PASSING, exit 0.
