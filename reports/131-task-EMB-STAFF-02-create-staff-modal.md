# Report 131 — task-EMB-STAFF-02

**Predecessor Task:** EMB-STAFF-01 (create-staff endpoint, report 130)

## Objective
Wire a real "add employee" modal on `/admin/employees` (`/admin/staff`) to the new `POST /admin/staff` endpoint.

## Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/store/uiStore.ts`
- `frontend/src/pages/admin/staff/components/CreateStaffModal.tsx` (new)
- `frontend/src/pages/admin/staff/Staff.tsx`

## What Changed?
- `api.ts`: hand-typed `CreateStaffRequest` (name/email/password/role — no generated schema exists yet for this brand-new endpoint) and `staffService.create`.
- `uiStore.ts`: added `'CREATE_STAFF'` to `ModalType`.
- `CreateStaffModal.tsx` (new): follows the same `Dialog` + `react-hook-form` + `zod` + `useMutation` + `useUIStore` convention as `NewCategoryModal.tsx`/`MovementDialog.tsx`, including the same `Select`/`SelectTrigger`/`SelectItem` pattern `MovementDialog.tsx` uses for its own enum field. Fields: name, email, password (client-side zod mirrors the backend's password-complexity regex so bad input is caught before the round-trip), role (`WAITER`/`KITCHEN`/`ADMIN`, labeled via the existing `ROLE_LABELS`). On success, invalidates the `['staff']` query so the new employee appears immediately.
- `Staff.tsx`: wires the already-existing but previously-dead `StaffGrid`'s `onAddRole` prop to `openModal('CREATE_STAFF')`, and mounts `<CreateStaffModal/>`.

## Why It Changed?
Completes the "add new employee" request now that EMB-STAFF-01 gives it something real to submit to. Deliberately scoped to identity fields only (name/email/password/role) — the extra HR profile fields (job title, shift, contract type...) already have their own `PATCH /admin/staff/{userId}` endpoint but no UI trigger yet (the "Perfil"/"..." buttons on each staff card are still dead); wiring that was called out as a separate, pre-existing gap and left out of scope per the approved plan.

## Verification
`cd frontend && pnpm run build` and `pnpm run lint` — build clean; lint unchanged at 17 pre-existing errors/8 warnings (zero new issues). Not manually exercised in a browser — no browser/Playwright tooling available in this session; flagging per verification-before-completion rather than claiming a check that wasn't done.
