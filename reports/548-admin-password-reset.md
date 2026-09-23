# Report 548 — ADMIN-PASSWORD-RESET (F-25): operator-assisted password recovery

## 1. Identification
- **Report:** 548
- **Task ID:** ADMIN-PASSWORD-RESET (F-25) — new item from the pilot-readiness workflow review
  (2026-09-23), not from `AUDIT_BLUEPRINT.md`
- **Predecessor Task:** report 547 — WAITER-ADD-ITEM-FOURTH-REVIEW-PASS
- **Branch:** none yet — pending commit on `main` (see §5)

## 2. Objective
Close the "no recuperar contraseña" gap flagged live in report 461 and explicitly deferred at the
time. `UserAdminController` already let a restaurant's ADMIN reset a WAITER/KITCHEN/ACCOUNTANT's
PIN (F-17), but nothing let the ADMIN recover their own password if they forgot it — there is no
one above them in the tenant. No mail infrastructure exists in the backend, so a self-service
email-reset flow was out of scope; the user chose operator-assisted recovery via the platform
Console instead, plus a forced "set a new password" modal on the affected user's next login.

## 3. Modified Files
- `backend/src/main/resources/db/migration/V18__user_must_change_password.sql` (new)
- `backend/src/main/java/com/vanter/ember/identity/model/User.java`
- `backend/src/main/java/com/vanter/ember/identity/model/dto/AuthResponse.java`
- `backend/src/main/java/com/vanter/ember/identity/service/AuthService.java`
- `backend/src/main/java/com/vanter/ember/identity/dto/ChangePasswordRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/identity/controller/UserProfileController.java`
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformAdminPasswordResetRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java`
- `backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java`
- `backend/src/test/java/com/vanter/ember/identity/controller/UserProfileControllerTest.java`
- `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`
- `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java`
- `frontend/src/lib/backend-types.ts`
- `frontend/src/lib/api.ts`
- `frontend/src/lib/platformApi.ts`
- `frontend/src/store/authStore.ts`
- `frontend/src/pages/auth/ForcePasswordChangeModal.tsx` (new)
- `frontend/src/pages/auth/ForcePasswordChangeModal.test.tsx` (new)
- `frontend/src/components/ProtectedRoute.tsx`
- `frontend/src/components/ProtectedRoute.test.tsx`
- `frontend/src/pages/console/ConsoleRestaurantDetail.tsx`
- `frontend/src/locales/en/auth.ts`
- `frontend/src/locales/es/auth.ts`

## 4. What Changed?
**Backend:**
- `users.must_change_password` (`V18`, idempotent, backfilled `false`).
- `POST /platform/restaurants/{id}/reset-admin-password` (SUPER_ADMIN only): operator sets a temp
  password for a named `userId`, validated as an ADMIN of that restaurant. Sets the password hash,
  flags `mustChangePassword=true`, bumps `tokenVersion` (invalidates any live session), and writes
  a `PlatformAuditLog` entry (`ADMIN_PASSWORD_RESET`) — same shape as every other operator action
  in `PlatformRestaurantService`.
- `POST /users/me/password` (any authenticated user, self-service): `AuthService.changePassword`
  verifies the current password, sets the new one, clears `mustChangePassword`, bumps
  `tokenVersion`, and returns a fresh `AuthResponse` so the caller isn't logged out by their own
  request.
- `AuthResponse` carries `mustChangePassword` on every login/register/PIN-login response.

**Frontend:**
- `ProtectedRoute` renders `ForcePasswordChangeModal` instead of `<Outlet/>` whenever
  `mustChangePassword` is true in the auth store — blocks every authenticated route, not just one
  screen, with no close/outside-click/Escape dismissal.
- `ForcePasswordChangeModal`: temp password + new password + confirm, calls
  `userProfileService.changePassword` and feeds the fresh `AuthResponse` back into `setAuth`.
- Console (`ConsoleRestaurantDetail`): "Resetear contraseña" button per admin row opens a dialog
  for the operator to type a temp password (min 8 chars client-side, real strength rule enforced
  server-side), calling the new endpoint.
- `backend-types.ts` hand-patched (`mustChangePassword` on `AuthResponse`) — no full `openapi`
  regen, consistent with prior hand-patches in this repo when the diff would be unrelated drift.

## 5. Why It Changed?
No mail infrastructure exists anywhere in the backend (`pom.xml` has zero mail dependencies), so a
classic email-link reset would have meant adding a new dependency, secret, token model, and
anti-enumeration hardening comparable to F-10's — disproportionate for a single-tenant pilot where
the founder is already the sole operator and reachable (`PROGRESS.md`'s pilot-readiness item 3,
already an accepted posture). The user chose the smaller, already-fitting pattern instead: reuse
the existing operator/Console trust boundary (`PlatformRestaurantController`, `SUPER_ADMIN`-only,
audited) the same way `create` already sets an initial ADMIN password, and force a real password
choice on next login so the operator-known temp value has a short lifetime.

## 6. Verification
- `./mvnw test` — full suite green (exit 0).
- `pnpm run build` (`tsc -b && vite build`) — clean.
- `pnpm run lint` — 0 errors (15 pre-existing warnings, none in touched files).
- `pnpm run test:run` — 198/198 (54 files), including 5 new tests across
  `ForcePasswordChangeModal.test.tsx` and `ProtectedRoute.test.tsx`.

## 7. Follow-up (not done here)
- Point 2 from the same workflow review (Hub local printing without the separate print-agent) —
  next task, separate context per `CLAUDE.md` §7.
