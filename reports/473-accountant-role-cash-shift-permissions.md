# Report 473

## 1. Identification
- **Report Number:** 473
- **Task ID:** New `ACCOUNTANT` role — move cash-shift open/close/arqueo off the waiter
- **Predecessor Task:** report 472 (waiter tables badge white bg)

## 2. Objective
Brainstormed with the user: apertura/cierre de caja and arqueo (the blind cash-count shift close) were gated to `WAITER`, but the waiter only takes orders — cash reconciliation is a separate control-internal responsibility, ideally done by someone other than the person who registered the sales. Added a new `ACCOUNTANT` role that owns this flow exclusively; the waiter no longer sees or can touch it. Admin was explicitly excluded per the user's clarification — Admin already has visibility into shift history/daily rollup via its own `/admin/cash-register` page and doesn't need the open/close/arqueo flow.

## 3. Modified Files
- **Backend:**
  - `backend/src/main/java/com/vanter/ember/identity/model/Role.java` — add `ACCOUNTANT`
  - `backend/src/main/resources/db/migration/V11__add_accountant_role.sql` — new, widens `users_role_check`
  - `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java` — `@PreAuthorize` re-scoping
  - `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java`
  - `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerProlongTest.java`
  - `backend/src/test/java/com/vanter/ember/E2EOrderFlowTest.java` — seeds an `ACCOUNTANT` user, opens the shift with it
- **Frontend:**
  - `frontend/src/App.tsx` — new `/accountant` route tree, `RoleRedirect` case
  - `frontend/src/layouts/AccountantLayout.tsx` — new, minimal layout (`TopNav` + `Outlet` + `FloatingNav` + `CashShiftSentinel`)
  - `frontend/src/layouts/WaiterLayout.tsx` — `CashShiftSentinel` removed
  - `frontend/src/components/FloatingNav.tsx` — cash icon moved from `WAITER` to `ACCOUNTANT`, now points at `/accountant/cash-register`
  - `frontend/src/components/CashShiftSentinel.tsx` — import path updated
  - `frontend/src/pages/waiter/cashRegister/**` → moved to `frontend/src/pages/accountant/cashRegister/**` (`CashRegister.tsx`, `CloseShiftDialog.tsx`, `MovementDialog.tsx`, `OpenShiftDialog.tsx`)
  - `frontend/src/pages/auth/navigateForRole.ts` (+ `.test.tsx`) — `ACCOUNTANT` → `/accountant`
  - `frontend/src/pages/admin/staff/types.ts`, `CreateStaffModal.tsx`, `EditStaffModal.tsx` — `ACCOUNTANT` assignable from the staff admin UI
  - `frontend/src/lib/backend-types.ts` — hand-patched the 4 generated `Role` literal unions (`CreateStaffRequest`, `StaffMemberResponse`, `UpdateUserRoleRequest`, `User`) to include `"ACCOUNTANT"`, matching what `pnpm run openapi` would regenerate now that the backend enum changed

## 4. What Changed?
Backend: `Role` gained `ACCOUNTANT`; the `users_role_check` CHECK constraint was widened via a new idempotent migration (`DROP CONSTRAINT IF EXISTS` + re-`ADD`, same pattern as V9's note on prod not being Flyway-baselined). `CashShiftController`'s `@PreAuthorize` was re-scoped: `open`, `recordMovement`, `close`, `current`, `prolong` are now `hasRole('ACCOUNTANT')` only (previously `WAITER`, or `WAITER`+`ADMIN` for `current`/`prolong`); `history` and `detail` are `hasAnyRole('ACCOUNTANT','ADMIN')` (Admin's own Corte Z page still reads these two for its shift-history table). `daily-report` is untouched (already `ADMIN`-only).

Frontend: the whole open/close/arqueo page moved from `/waiter/cash-register` to a new `/accountant` route tree with its own minimal layout, gated `ProtectedRoute allowedRoles={['ACCOUNTANT']}`. `CashShiftSentinel` (the overdue-shift nag dialogs) moved from `WaiterLayout` to `AccountantLayout` since the waiter no longer has anything to do with shift deadlines. Staff creation/edit modals gained `ACCOUNTANT` as an assignable role.

Two functional bugs were caught and fixed while moving the code (both direct consequences of the permission split, not pre-existing):
1. `CashRegister.tsx` gated every action button behind `isWaiter = role === 'WAITER'` (a leftover from when this page was dual-purpose: full read-write for the waiter, read-only for an admin peeking in). Since the route is now `ACCOUNTANT`-only, that check was backwards — it would have shown the accountant a fully disabled, read-only page. Removed the gate entirely; every viewer of this route is by construction allowed to act.
2. `CloseShiftDialog.tsx`, on a "can't close — N tables still open" error, used to dismiss the dialog and `navigate('/waiter/tables')` so the waiter could go close those tables. The accountant has no route into `/waiter/tables` (`ProtectedRoute` would 403 them), so that navigate was dropped — the toast reporting the open-table count is enough; the stale-shift alert reappears once a waiter actually closes them.

## 5. Why It Changed?
Direct user request via brainstorming: separation of duties between order-taking (waiter) and cash auditing (accountant), so the person who books the sale is not the same person who certifies the drawer. Admin exclusion was an explicit correction mid-conversation — Admin's existing Corte Z / shift-history views already cover what Admin needs.

## 6. Verification
- `./mvnw test` (full backend suite) — **1277/1277**, 0 failures.
- `pnpm vitest run` (full frontend suite) — **136/136**, 0 failures.
- `pnpm run build` (`tsc -b && vite build`) — clean.
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in touched files).
- TDD: `CashShiftControllerTest`/`CashShiftControllerProlongTest` extended with RED (old `@PreAuthorize`, new role expectations) → confirmed failing for the right reason → GREEN after the controller change. `navigateForRole.test.tsx` got the same RED→GREEN treatment for the new `ACCOUNTANT` case.
- No live browser check performed (backend/frontend changes verified via automated suites only) — worth a manual pass through the accountant login → open shift → record movement → close shift flow next time the app is run live.
