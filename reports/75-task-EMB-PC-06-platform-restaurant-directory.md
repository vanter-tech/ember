# Report 75 — EMB-PC-06

## Identification
- **Report:** 75
- **Task ID:** EMB-PC-06
- **Predecessor Task:** EMB-PC-05 (report 74)

## Objective
Add the platform operator's tenant directory: `GET /platform/restaurants` (paginated list) and `GET /platform/restaurants/{id}` (detail, including its `ADMIN` user(s)).

## Modified Files
- `backend/src/main/java/com/vanter/ember/identity/repository/UserRepository.java`
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantSummaryResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantAdminResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantDetailResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java` (new)
- `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java` (new)
- `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java` (new)

## What Changed?
`UserRepository` gained `findByRestaurantId_IdAndRole(UUID, Role)`. `PlatformRestaurantController` (`/platform/restaurants`) exposes a paginated list endpoint (`Page<PlatformRestaurantSummaryResponse>`, via `RestaurantRepository.findAll(Pageable)`) and a detail endpoint (`PlatformRestaurantDetailResponse`) that 404s via `ResourceNotFoundException` when the id doesn't exist. `PlatformRestaurantService` holds the read logic; the detail path additionally queries `UserRepository` for that tenant's `ADMIN` users and embeds them as `admins`. No `@PreAuthorize` was added — the `/platform/**` chain's `anyRequest().authenticated()` (`PlatformSecurityConfig`) already gates every route to a valid operator token, same as `PlatformAuthController#changePassword`.

## Why It Changed?
This is the first read-facing route of the super-admin console (EMB-PC-06–14 backlog) and the first `/platform/**` route exercised with a real operator token end-to-end. `Restaurant` and `User` both carry no `@TenantId` (confirmed in `Restaurant.java`/`User.java`), so both repositories can be queried directly with no `TenantContextHolder` binding — unlike `PublicRestaurantController`'s branding lookup, which needs the bind because `RestaurantSettings` does carry `@TenantId`. `PlatformAuthIsolationTest`'s existing `tenantToken_rejectedOnPlatformRoute` test already targets `GET /platform/restaurants`, so it doubles as coverage that a tenant JWT is rejected here too — no change needed there.

## Verification
`./mvnw test` — 559/559 passing, 0 failures (added 3 `PlatformRestaurantServiceTest` + 4 `PlatformRestaurantControllerTest` methods on top of the prior suite).
