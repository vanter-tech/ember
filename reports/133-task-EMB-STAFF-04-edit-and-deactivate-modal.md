# Report 133 — task-EMB-STAFF-04

**Predecessor Task:** EMB-STAFF-03 (edit/deactivate backend, report 132)

## Objective
Wire the "Perfil" (edit) and "..." (delete) buttons on each `/admin/staff` card — dead since EMB-FloatingNav (report 114) — to the now-real backend.

## Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/store/uiStore.ts`
- `frontend/src/pages/admin/staff/components/EditStaffModal.tsx` (new)
- `frontend/src/components/GlobalDeleteModal.tsx`
- `frontend/src/pages/admin/staff/Staff.tsx`

## What Changed?
- `api.ts`: `UpdateStaffProfileRequest` switched from the generated schema alias to a hand-typed interface adding `name`/`email` (the generated one predates EMB-STAFF-03's backend change — same temporary gap as `CreateStaffRequest`).
- `EditStaffModal.tsx` (new): follows the `EditMenuModal.tsx` convention exactly — `values: {...}` reactively pre-fills the form from `modalPayload` (the clicked `StaffMemberResponse`), including the same `Switch`-in-`FormField` pattern for a boolean toggle, here used for `active` (so re-activating a deactivated employee is just flipping the same switch back, no separate "undo" flow needed). Fields: name, email, jobTitle, shift, contractType, location, active.
- `GlobalDeleteModal.tsx`: added a third case (`DELETE_STAFF`) alongside the existing `DELETE_CATEGORY`/`DELETE_ITEMS` ones. Calls `staffService.updateProfile(member.id, { active: false })` rather than any hard-delete endpoint (none exists, by design — see report 132). Labels are deliberately honest about what's happening: confirm button reads "Sí, Desactivar" (not "Eliminar") for this case, with an explanatory line ("quedará inactivo... su historial se conserva") — avoids the UI overpromising a hard delete it isn't performing.
- `Staff.tsx`: wires `StaffGrid`'s already-existing `onViewProfile`/`onOpenActions` props (present since EMB-FloatingNav but never passed) to `openModal('EDIT_STAFF', member)` / `openModal('DELETE_STAFF', member)`; mounts `EditStaffModal` and `GlobalDeleteModal` (not previously mounted on this page).

## Why It Changed?
Completes the "edit, delete personal information" request. Scope call (offered as "your call" by the user for view-vs-modal): built as a modal, not a separate route/view — matches the codebase's exclusive existing convention for every other admin edit surface (`EditMenuModal`, `EditCategoryModal`), and both dead buttons this wires (`onViewProfile`/`onOpenActions`) were already designed with a modal-payload shape in mind, not a navigable page.

## Verification
`cd frontend && pnpm run build` and `pnpm run lint` — build clean; lint unchanged at 17 pre-existing errors/8 warnings (zero new issues). Not manually exercised in a browser — no browser/Playwright tooling available in this session.
