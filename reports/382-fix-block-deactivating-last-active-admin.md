# Report 382 — fix(identity): block deactivating the last active admin

## Identification
- **Report:** 382
- **Task:** ad-hoc bug fix (`fix/admin-self-deactivation`, branched off `main`@`ceab79c2`)
- **Predecessor:** report 380 (customer banner preset picker) — last task on `main`.

## Objective
`PATCH /admin/staff/{userId}` (`UserAdminService.updateProfile`) applied `active=false`
unconditionally. An ADMIN could deactivate their own account — or the last remaining active
ADMIN of the tenant. Because `SecurityConfig`'s auth filter only authenticates
`userDetails.isEnabled()` users, a tenant left with zero active admins has **no way back in**:
no one can manage staff, roles, or catalog, and the deactivation cannot be undone from inside
the app. The restaurant is effectively bricked permanently.

## Modified Files
- `backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java`
- `backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java`

## What Changed?
`UserAdminService.updateProfile`: before applying `active=false`, call the new private
`assertNotLastActiveAdmin(target, tenantId)`. It is a no-op unless the target is a currently
active ADMIN; then it loads the tenant's active admins
(`UserRepository.findByRestaurantId_IdAndRoleAndActiveTrue(tenantId, ADMIN)` — already existed)
and throws `IllegalArgumentException("Cannot deactivate the last active administrator of this
restaurant.")` when no *other* active admin remains. `IllegalArgumentException` is already mapped
to HTTP 400 by `GlobalExceptionHandler`, consistent with the sibling "Email already in use"
guard in the same method.

Deactivating an admin while another active admin exists is still allowed — it is reversible by
that other admin.

Tests: `updateProfile_blocksDeactivatingTheSoleActiveAdmin` (throws, `save` never called) and
`updateProfile_allowsDeactivatingAnAdminWhenAnotherActiveAdminRemains` (succeeds). Existing
`updateProfile_appliesOnlyNonNullFields` already covers the non-admin path (guard returns early,
no extra repo call).

## Why It Changed?
The guard is deliberately "last *active* admin", not "self": the service has no caller identity,
and the harmful outcome is zero-active-admins regardless of who triggers it. Checking role +
active state keeps the extra query off every non-admin profile edit.

Out of scope (noted for follow-up): `UserAdminService.updateRole` takes no `tenantId` and has no
tenant-scope guard — an admin can change the role of a user in another tenant by id, and can
demote the last admin the same way. Separate fix.

## Verification
`cd backend && ./mvnw test` — `UserAdminServiceTest` 18/18; full suite green.
