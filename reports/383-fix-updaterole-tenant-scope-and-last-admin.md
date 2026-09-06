# Report 383 — fix(identity): tenant-scope + last-admin guard on updateRole

## Identification
- **Report:** 383
- **Task:** ad-hoc security fix (`fix/update-role-tenant-scope`, off `main`@`9404e5e3`)
- **Predecessor:** report 382 (block deactivating a restaurant's last active admin) — this is the
  role-change half of the same class of bug, noted as follow-up there.

## Objective
`PATCH /admin/users/{userId}/role` → `UserAdminService.updateRole(userId, role)` did
`findById(userId)` then `setRole` with **no tenant check and no last-admin guard**:

1. **Cross-tenant (IDOR):** any ADMIN could change the role of any user in any restaurant by
   guessing/knowing the user id.
2. **Self-lockout:** an ADMIN could demote themselves (or the last remaining ADMIN) to
   WAITER/KITCHEN, leaving the tenant with zero admins — unrecoverable from inside the app, the
   same brick as report 382's deactivation path.
3. **CUSTOMER:** unlike `create`, `updateRole` accepted `Role.CUSTOMER`, which would strand a
   staff row in a self-assign-only role.

## Modified Files
- `backend/src/main/java/com/vanter/ember/identity/controller/UserAdminController.java`
- `backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java`
- `backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java`
- `backend/src/test/java/com/vanter/ember/identity/controller/UserAdminControllerTest.java`

## What Changed?
- **Controller:** `updateRole` now passes `TenantContextHolder.requireTenantId()` into the
  service (same as `updateStaffProfile`, `createStaff`, etc.).
- **Service:** `updateRole(String, UUID tenantId, Role)`:
  - `requireTenantUser(userId, tenantId)` (existing helper) — a target outside the caller's
    restaurant 404s (`ResourceNotFoundException`), hiding its existence, exactly like
    `updateProfile`.
  - Rejects `newRole == CUSTOMER` (`IllegalArgumentException` → 400), matching `create`.
  - If the target is a currently-active ADMIN and `newRole != ADMIN`, blocks when no other
    active admin remains.
  - Report 382's `assertNotLastActiveAdmin` (void) was refactored into a reusable predicate
    `isLastActiveAdmin(target, tenantId)`; the deactivation call site keeps its own message,
    the role call site adds "Cannot change the role of the last active administrator…". Both
    still contain "last active administrator", so report 382's assertions are unaffected.
- **Tests:** 5 new `UserAdminServiceTest` cases (happy WAITER→KITCHEN; cross-tenant 404;
  CUSTOMER rejected; demoting sole active admin blocked + `save` never called; demoting with a
  co-admin succeeds). The two `UserAdminControllerTest` `updateRole` stubs updated to the 3-arg
  signature + `TenantContextHolder.setTenantId(TENANT_ID)`.

## Why It Changed?
The endpoint is `@PreAuthorize("hasRole('ADMIN')")` only — role alone, no resource check — so
every guard has to be in the service. Reusing `isLastActiveAdmin` keeps the "zero admins bricks
the tenant" rule in one place for both the deactivation and the demotion route.

Out of scope (unchanged): the response still serializes the `User` entity;
`passwordHash`/`pinHash` are already `@JsonIgnore` and the frontend discards the body
(`staffService.updateRole` returns `Promise<void>`), so no shape change was warranted.

## Verification
`cd backend && ./mvnw test` — `UserAdminServiceTest` 23/23, `UserAdminControllerTest` 22/22,
full suite green.
