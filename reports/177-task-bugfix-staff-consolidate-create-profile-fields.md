# Report 177 — bugfix-staff-consolidate-create-profile-fields

## Identification
- **Report number:** 177
- **Task ID:** bugfix-staff-consolidate-create-profile-fields
- **Predecessor Task:** feature-ticket-settings-live-preview (report 176)

## Objective
Fix the `/admin/employees` (Gestión de Personal) flow: creating a new employee only asked for account fields (name/email/password/role), then clicking "Perfil" on the new card immediately demanded more fields (puesto/turno/tipo de contrato/ubicación) as an unexpected second step. Consolidate all of it into the creation modal, laid out as a grid instead of a long vertical cascade, and fix the dead "Nuevo empleado" button in `TopNav` so it actually opens that modal.

## Modified Files
- `backend/src/main/java/com/vanter/ember/identity/dto/CreateStaffRequest.java`
- `backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java`
- `backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java`
- `backend/src/test/java/com/vanter/ember/identity/controller/UserAdminControllerTest.java`
- `frontend/src/pages/admin/staff/components/CreateStaffModal.tsx`
- `frontend/src/pages/admin/staff/components/EditStaffModal.tsx`
- `frontend/src/components/TopNav.tsx`
- `frontend/src/lib/backend-types.ts` (regenerated)

## What Changed?
- `CreateStaffRequest` gained 4 new `@NotBlank` fields: `jobTitle`, `shift`, `contractType`, `location` (required, per explicit user choice — matches how they were the only editable fields in the profile modal before). `UserAdminService.create` now sets them on the `User` at creation via the existing `User.builder()` chain — one atomic save, no second network round-trip and no partial-creation failure mode.
- All 8 backend `new CreateStaffRequest(...)` call sites (4 in `UserAdminServiceTest`, 4 in `UserAdminControllerTest`) updated with the 4 new positional args; added one new test, `createStaff_returns400ForBlankJobTitle`, to actually exercise the new validation.
- `CreateStaffModal.tsx`: `createStaffSchema` gained matching required `zod` fields; form default values extended; the 4 new `FormField`s (reusing the exact same `jobTitleLabel`/`shiftLabel`/`contractTypeLabel`/`locationLabel` i18n keys the old profile modal used, so no new i18n entries were needed) render alongside name/email/password/role. Layout switched from `className="space-y-5"` (single-column cascade) to `className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-5"`, dialog widened `sm:max-w-md` → `sm:max-w-xl`, and `DialogFooter` spans both columns (`sm:col-span-2`) so Cancel/Guardar stay right-aligned under the full-width grid.
- `EditStaffModal.tsx` ("Perfil" button on each `StaffCard`) kept its existing fields (no change there — profile editing after creation is still useful for later changes, per explicit user choice) but got the same grid treatment: same `grid grid-cols-1 sm:grid-cols-2` layout, same `sm:max-w-xl` width, and the `active` switch row + footer both set to `sm:col-span-2` so the boolean toggle doesn't try to pair with a neighboring field.
- `TopNav.tsx`: the `/admin/employees` branch of the button-config `if/else` chain was missing `actionType = 'CREATE_STAFF'` — every other route (menu items, categories) sets this, but employees never did, so the top-bar "Nuevo empleado" button called `openModal(null, ...)` and silently did nothing. Added the missing assignment; it now opens the same `CreateStaffModal` the dashed "+add" card in the staff grid already opened.
- `backend-types.ts` regenerated. First 2 regen attempts against a just-started backend returned the OLD (pre-change) `CreateStaffRequest` shape despite the process responding 200 on `/v1/v3/api-docs` — a live JSON fetch straight from the running instance (bypassing `pnpm run openapi`/`openapi-typescript`) confirmed the backend itself really was serving the stale schema at that moment, self-resolving a few seconds later on a fresh restart with no code changes. Read as a one-off springdoc/Tomcat warm-up race on this machine (likely Windows antivirus/first-JIT-hit compile stall delaying `@RequestMapping` handler registration past the point Tomcat starts accepting connections), not a stale-build-artifact issue like report 176's. Fix was operational: `curl`/`grep` the live `/v1/v3/api-docs` JSON directly to confirm the schema is actually current before trusting a regen. Final diff is exactly the 4 new `CreateStaffRequest` fields plus the same harmless pre-existing `Page`-schema field-ordering nondeterminism seen in report 176.

## Why It Changed?
User-reported UX problem in the admin Employees page: the two-step create-then-immediately-fill-profile flow felt broken/unexpected, the profile modal's vertical field cascade looked cramped, and the TopNav "new employee" button was a dead click (confirmed via code read, not just report — `actionType` was genuinely never assigned on that branch). Design choices were confirmed via brainstorming: extend the backend create endpoint (atomic, recommended over a client-side create-then-patch chain) rather than a create+patch, made the 4 operational fields required at creation (explicit user choice), and kept the profile modal for future edits rather than removing it (explicit user choice), restyled to the same grid.

## Verification
- `cd frontend && pnpm run build` → `tsc -b && vite build` clean, no TS errors.
- `cd backend && ./mvnw test` → 723/723 green (722 baseline + 1 new validation test).
- No browser click-through this session (no `claude-in-chrome` tool available) — disclosed gap, consistent with prior sessions.
