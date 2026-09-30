# Report 641

## 1. Identification
- **Report Number:** 641
- **Task ID:** ADMIN-STAFF-PASSWORD-RESET — an ADMIN resets the password of a waiter (and kitchen/accountant staff)
- **Predecessor Task:** report 640 (EMB-DRAWER review change). EMB-DRAWER (reports 634-640) and this task are all still uncommitted at the time of writing.

## 2. Objective
Let the restaurant's ADMIN set a new password for a staff member who forgot theirs, without going through the platform operator (which only handles ADMIN accounts, F-25).

## 3. Design decisions (agreed with the user before coding)
- The **admin types the new password** (same policy as creating staff: 8-128 chars with upper, lower, digit, symbol).
- It is **not temporary**: no forced change at next login (`must_change_password` stays false). The person can change it themselves through the existing self-service change.
- **Cooldown of 6 hours per account** after a reset (enforced in the backend, 429; the UI disables the button and shows when it opens again).
- Applies to **WAITER, KITCHEN and ACCOUNTANT** of the caller's own restaurant. Never to an ADMIN (still the platform operator's job) or to customers.
- Not done on purpose: email-based recovery, forced change, an audit view, resetting another ADMIN. The PIN is untouched.

## 4. Modified Files
- Backend: `db/migration/V21__user_admin_password_reset.sql` (new), `identity/model/User.java`, `identity/dto/AdminResetPasswordRequest.java` (new), `identity/dto/StaffMemberResponse.java`, `identity/service/UserAdminService.java`, `identity/controller/UserAdminController.java`
- Backend tests: `UserAdminServiceTest`, `UserAdminControllerTest`, `SecurityAuditTest`
- Frontend: `lib/api.ts` (`staffService.resetPassword`), `lib/backend-types.ts` (hand-patched `passwordResetAvailableAt`), `pages/admin/staff/components/EditStaffModal.tsx` (+ `.test.tsx`), `locales/es|en/admin.ts`
- `PROGRESS.md`

## 5. What Changed?
- **V21:** `users.password_reset_at timestamp(6) with time zone` and `users.password_reset_by varchar(255)`, both nullable, idempotent.
- **`POST /admin/staff/{userId}/reset-password`** (`ADMIN`, body `{ newPassword }`, 204). `UserAdminService.resetPassword`: tenant-scoped lookup (another tenant → 404), role must be WAITER/KITCHEN/ACCOUNTANT (else 409 via `IllegalArgumentException`), within the cooldown → `ResponseStatusException` 429 (nothing changes), otherwise stores the new hash, forces `mustChangePassword=false`, records `passwordResetAt` (now) and `passwordResetBy` (the admin's email), and bumps `tokenVersion` so the person's open sessions stop working. The password is never logged; only the target id and admin email are.
- **`GET /admin/staff`** now returns `passwordResetAvailableAt` per member (null when a reset is allowed now).
- **UI:** a "Restablecer contraseña" section in the edit-staff modal (new password + confirm, client-side policy and match checks, hint that it is not temporary and closes sessions). Hidden when editing an ADMIN; while locked the inputs and button are disabled and it shows "Disponible de nuevo a las HH:mm".

## 6. Verification
- Backend `./mvnw test`: **1640 tests**, 12 new (7 service, 4 controller, 1 security-audit 401), all passing; the only error is the known `PortableMinioBootstrapCredentialsIntegrationTest` flake (passes alone; see report 640). RED first: compile failures before implementing.
- **V21 verified on real Postgres 16** (scratch container, V1..V21; the developer's DB untouched, container removed): the columns exist with the expected types, re-running V21 is a no-op, and the app booted with `ddl-auto=validate` against that schema (`Started EmberApplication`).
- Frontend: `tsc -b` clean, `pnpm run build` OK, `pnpm run lint` 0 errors (an impure `Date.now()` in render was caught by the React lint rule and fixed by capturing the time on mount, same pattern as `CashRegister`). Tests: **253/253** excluding `MenuJoin.test.tsx`; 5 new tests for the section (reset call, mismatch, policy, hidden for ADMIN, cooldown). `MenuJoin.test.tsx` (untouched) fails one test whenever the user's local backend is running and passes otherwise (see report 640).
- **Not verified:** no live browser/API run. In particular the 429 path through the real HTTP stack is only covered at service level (the `ResponseStatusException` status), and the frontend shows a generic error toast for it (it also refetches staff so the button locks).
- The developer's running backend must be restarted to get the endpoint and apply V21 to the dev database.
