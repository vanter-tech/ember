# Report 130 — task-EMB-STAFF-01

**Predecessor Task:** bugfix-cobrar-mesa-no-billable-items (report 129)

## Objective
Add a backend endpoint capable of creating a new staff user (WAITER/KITCHEN/ADMIN) for the current tenant — investigation confirmed no such endpoint existed anywhere: `POST /auth/register` only creates tenant-less `CUSTOMER`s, and the only other user-creation code (`PlatformRestaurantService.create`) is platform-operator-only and bundled with creating a brand-new restaurant.

## Modified Files
- `backend/src/main/java/com/vanter/ember/identity/dto/CreateStaffRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java`
- `backend/src/main/java/com/vanter/ember/identity/controller/UserAdminController.java`
- `backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java`
- `backend/src/test/java/com/vanter/ember/identity/controller/UserAdminControllerTest.java`
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`

## What Changed?
- `CreateStaffRequest(name, email, password, role)` — same password complexity `@Pattern` as `RegisterRequest`/`PlatformRestaurantCreateRequest` (upper+lower+digit+special char, 8–128 chars).
- `UserAdminService.create(tenantId, request)`: rejects `role == CUSTOMER` (that role is self-registration-only), rejects a duplicate email (`userRepository.existsByEmail`, mirrors `AuthService.register`/`PlatformRestaurantService.create`), looks up the tenant's `Restaurant` via the newly-injected `RestaurantRepository`, encodes the password via the newly-injected `PasswordEncoder`, and saves a tenant-bound `User`. Returns `StaffMemberResponse` (reuses the existing `toStaffResponse` mapper).
- `UserAdminController`: `POST /admin/staff` (ADMIN-only, 201 Created), tenant resolved via `TenantContextHolder.requireTenantId()` — same pattern as the sibling `GET`/`PATCH /admin/staff` endpoints.
- Added `SecurityAuditTest` route, controller tests (created/403/401/400-weak-password), and service tests (success + tenant-binding assertion, CUSTOMER rejection, duplicate email, restaurant-not-found).

## Why It Changed?
The user asked to implement an "add new employee" modal on `/admin/employees`. Investigation (dispatched as a background agent before implementation) found the frontend page already has a real, wired `GET /admin/staff` data flow and even a dead "Agregar nuevo rol" ghost card, but literally nothing in the backend could create a staff user — this was a hard blocker, not a scope choice, and the user approved proceeding with the backend addition.

## Verification
`cd backend && ./mvnw test` — full suite green (exit code 0), including all new/updated test files.
