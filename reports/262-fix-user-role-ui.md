# Report 262 — add UI trigger for `PATCH /admin/users/{id}/role`

## 1. Identification
- **Report number:** 262
- **Task ID:** fix-user-role-ui
- **Predecessor Task:** report 261 (fix-ci-lint-workflow)

## 2. Objective
The backend endpoint `PATCH /admin/users/{userId}/role` (`UserAdminController.updateRole`,
ADMIN-only) has existed since EMB-STAFF but had **no frontend caller** — an admin could set a
staff member's role only at creation time (`CreateStaffModal`), never change it afterward.
Close that gap. Minor-gap task in Fase 1, on its own `fix/*` branch off `main`.

## 3. Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/pages/admin/staff/components/EditStaffModal.tsx`

## 4. What Changed?
- **`api.ts`:** added `staffService.updateRole(userId, role)` → `api.patch('/admin/users/{userId}/role', { role })`,
  plus an exported `StaffRole` type = the generated `UpdateUserRoleRequest['role']` minus
  `CUSTOMER` (that role is self-assigned only, via `POST /auth/register`).
- **`EditStaffModal.tsx`:** added a **Rol** `<Select>` field (WAITER / KITCHEN / ADMIN),
  reusing the exact pattern + `ROLE_LABELS` map already used by `CreateStaffModal`. The zod
  schema gained `role: z.enum(['WAITER','KITCHEN','ADMIN'])`; `form.values.role` seeds from
  `member.role` (falls back to `WAITER` if absent or `CUSTOMER`). The submit `mutationFn` is now
  async: it always `PATCH`es the HR profile fields (`updateProfile`, unchanged), then — only if
  the selected role differs from `member.role` — also calls `updateRole`. `onSuccess` /
  `onError` / the `['staff']` invalidation are untouched, so the existing `staffUpdatedToast`
  covers both. The Select is `disabled` when the row being edited is the current user's own
  account (`member.id === useAuthStore().userId`) to stop an admin demoting themselves out of
  the panel.
- **i18n:** no new keys — `roleLabel` already exists in `es/admin.ts` + `en/admin.ts`.

## 5. Why It Changed?
Editing role belongs on the same modal as the rest of a staff member's editable attributes, not
a bespoke control — `EditStaffModal` is where an admin already goes to change a person's details.
Keeping the two backend calls (profile vs role) but firing the role one only on an actual change
avoids a redundant write and a pointless audit entry on every profile save. The self-demotion
guard is a cheap footgun-removal, not a security control (the backend still allows it).

## Verification
- `pnpm run lint` — exit 0 (0 errors / 17 warnings)
- `pnpm run build` — PASS (tsc `-b` clean + vite)
- `pnpm run test` — 41/41 PASS
- CI (PR): `lint-backend` / `lint-frontend` / `lint-gateway` all green.
