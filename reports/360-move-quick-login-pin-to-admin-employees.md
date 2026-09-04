# Report 360 — Move the quick-login PIN from self-service headers to an admin-only per-employee control

## 1. Identification
- **Report number:** 360
- **Current Task:** feature change — the "Configurar PIN de acceso rápido" control showed in every waiter/admin view; make it admin-only, set per account inside `/admin/employees`
- **Predecessor Task:** report 359 (stale-shift alert admin block + waiter loop)
- **Branch:** `feat/waiter-quick-login-table-actions`

## 2. Objective
The quick-login PIN was self-service: a button in `AdminLayout` / `WaiterLayout` headers (visible on every screen) plus a post-password-login nudge, backed by `POST/DELETE /account/pin` gated on the caller's own password. Per the user: the PIN must be assigned **only by the admin**, individually per restaurant account, from each account's config in `/admin/employees` — surfaced there as "Agregar PIN para inicio rápido".

## 3. Modified Files
### Backend
- `identity/dto/AdminSetPinRequest.java` (new)
- `identity/dto/StaffMemberResponse.java` — `+ boolean hasPin`
- `identity/service/UserAdminService.java` — `setPin` / `clearPin` (+ `requireTenantUser` helper), `hasPin` in `toStaffResponse`
- `identity/controller/UserAdminController.java` — `PUT` / `DELETE /admin/staff/{userId}/pin` (ADMIN)
- `identity/controller/AccountController.java` (deleted)
- `identity/model/dto/SetPinRequest.java` (deleted)
- `identity/service/AuthService.java` — `setPin` / `clearPin` removed
- Tests: `AccountControllerTest.java` (deleted); `AuthServiceTest.java` (3 PIN tests removed); `UserAdminServiceTest.java` (+4); `UserAdminControllerTest.java` (+6, `hasPin` in 3 fixtures); `config/SecurityAuditTest.java` (+2 rows)

### Frontend
- `layouts/AdminLayout.tsx`, `layouts/WaiterLayout.tsx` — PIN button + `SetPinPrompt` + related hooks removed
- `pages/auth/QuickLoginModal.tsx` — post-login "create a PIN" nudge branch removed
- `pages/auth/SetPinPrompt.tsx` + `SetPinPrompt.test.tsx` (deleted)
- `store/quickAccessStore.ts` — `pinDismissed` / `dismissPinPrompt` removed
- `lib/api.ts` — `authService.setPin` / `clearPin` removed; `staffService.setPin(userId, pin)` / `clearPin(userId)` added
- `lib/backend-types.ts` — `StaffMemberResponse.hasPin?: boolean`
- `pages/admin/staff/components/EditStaffModal.tsx` — new `StaffPinSection` (add / update / remove PIN)
- `pages/admin/staff/components/EditStaffModal.test.tsx` (new, 3 tests)
- `locales/{en,es}/auth.ts` — 9 dead `setPin*` keys removed
- `locales/{en,es}/admin.ts` — 13 `staffPin*` keys added (parity)
- `store/quickAccessStore.test.ts`, `pages/auth/Login.quickaccess.test.tsx` — drop `pinDismissed`
- `test/setup.ts` — `ResizeObserver` stub (jsdom lacks it; Radix `Select` in `EditStaffModal` needs it)

## 4. What Changed?
- **New admin endpoints** `PUT /admin/staff/{userId}/pin` and `DELETE /admin/staff/{userId}/pin`, `@PreAuthorize("hasRole('ADMIN')")`, 204. `UserAdminService.setPin` bcrypt-hashes the PIN and stamps `pinUpdatedAt`; `clearPin` nulls both. Both go through `requireTenantUser` — the same guard as `updateProfile` (user must belong to the caller's tenant, else 404). **No password check** — the admin cannot know the employee's password; the ADMIN role + tenant scope is the authorization, consistent with role changes / deactivation.
- `StaffMemberResponse` now carries `hasPin` (`pinHash != null`) so the UI can show status and pick the right action.
- **Self-service PIN path fully removed**: `AccountController`, `SetPinRequest`, `AuthService.setPin/clearPin`, the header buttons, the `SetPinPrompt` dialog, the post-login nudge, and the `pinDismissed` bookkeeping are all gone. `AuthService.loginWithPin` is untouched — the PIN still works for quick login; only *setting* it moved.
- **`EditStaffModal`** gains a bordered "PIN para inicio rápido" section (outside the profile form's submit): a status chip (`PIN configurado` / `Sin PIN`), PIN + confirm inputs (digits only, 4–6), and `Agregar PIN` / `Actualizar PIN` plus `Quitar PIN` when one exists. Each action is its own mutation → `staffService.setPin` / `clearPin`, toast, and `invalidateQueries(['staff'])`. Client-side validation mirrors the backend `^\d{4,6}$` and a match check.

## 5. Why It Changed?
The PIN is a shared-terminal convenience for staff, but letting each user self-assign it (and showing the entry point on every screen) is the wrong ownership model for this product — the admin manages accounts. Consolidating it into `EditStaffModal` puts it where every other account setting already lives, and dropping the self-service endpoint removes a credential-write path that no longer has a caller.

## 6. Verification
- Backend: `./mvnw test` — **992 tests, 0 failures / 0 errors, BUILD SUCCESS**.
- Frontend: `pnpm run build` — 0 TS errors. `pnpm run lint` — 0 errors (16 pre-existing warnings). `pnpm run test:run` — **68/68** (22 files).
