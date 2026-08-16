# Report 114 — EMB-FloatingNav (Staff Management view)

## 1. Identification
- **Report number:** 114
- **Task ID:** EMB-FloatingNav (from `PROGRESS.md`'s Task Queue Status, brainstormed by user)
- **Predecessor Task:** analytics-cards-padding-and-title-polish (report 113)

## 2. Objective
Build a new "Gestión de Personal" (Staff Management) admin view per the task's mockup spec and wire it into `FloatingNav`. Purely a UI scaffold with typed mock data — no backend endpoint for staff listing exists yet (`UserAdminController` only exposes `PATCH /admin/users/{userId}/role`), so the task explicitly asked to leave it "ready to connect later."

## 3. Modified/New Files
- `frontend/src/pages/admin/staff/types.ts` (new) — `StaffMember`/`StaffDepartment`/`StaffFilter` types, `DEPARTMENT_CONFIG`, `STAFF_FILTERS`.
- `frontend/src/pages/admin/staff/mock-data.ts` (new) — 7-employee placeholder roster.
- `frontend/src/pages/admin/staff/components/StaffHeader.tsx` (new)
- `frontend/src/pages/admin/staff/components/StaffFilters.tsx` (new)
- `frontend/src/pages/admin/staff/components/StaffCard.tsx` (new) — exports `StaffCard` + `AddStaffCard`.
- `frontend/src/pages/admin/staff/components/StaffGrid.tsx` (new)
- `frontend/src/pages/admin/staff/components/StaffKpis.tsx` (new)
- `frontend/src/pages/admin/staff/Staff.tsx` (new) — page composition + local `search`/`department` filter state.
- `frontend/src/App.tsx` — new `Staff` import + `<Route path="staff" element={<Staff />} />` under the existing `/admin` `ProtectedRoute` group.
- `frontend/src/components/FloatingNav.tsx` — new `Users` icon import + nav `Link` to `/admin/staff` ("Personal"), inside the existing `role === 'ADMIN'` block, between Analíticas and the Configuración divider.

## 4. What Changed?
- **Header:** title + subtitle, search `Input` (client-side name filter), primary `Button` "Nuevo empleado" (brand red by default variant).
- **Filters:** segmented pill group (Todos/Cocina/Comedor/Administración/Limpieza) reusing the exact `rounded-full bg-muted/60 p-1` + `Button` default/ghost pattern already established in `SalesChart.tsx`.
- **Grid:** responsive `StaffCard`s (`Avatar` + colored status dot, name, role label, department `Badge` with a per-department soft color, 2 metadata pills, "Perfil"/`...` actions) plus a dashed-border `AddStaffCard` ghost tile.
- **KPIs:** 3 bottom cards (Personal total / Activos ahora / Horas pendientes) reusing the exact icon-chip (`bg-primary/10`) + `text-primary` value styling established in `SummaryCards.tsx` (reports 112–113), computed from the full mock roster (not the filtered view).
- All action props (`onAddEmployee`, `onViewProfile`, `onOpenActions`, `onAddRole`) are optional and unused by `Staff.tsx` — buttons render but are inert until a parent wires real handlers, matching the task's "leave ready to connect later" requirement. No `staffService`/`useQuery` exists; `MOCK_STAFF` is a static array.
- `Role.java` (backend) only has `CUSTOMER`/`WAITER`/`KITCHEN`/`ADMIN` — no "Cleaning" or department concept. `StaffDepartment` is therefore a new frontend-only type, not a reuse of the auth `Role` enum; a future backend integration will need its own department field.
- Per user's explicit choice, all copy is in Spanish (matching the rest of the admin app: "Analíticas", "Configuración") rather than the English wording used in the task's own mockup description.

## 5. Why It Changed?
New feature task (`EMB-FloatingNav`) appended directly to `PROGRESS.md` by the user with a full mockup-based spec. Followed the same visual language established over reports 111–113 (icon-chip titles, `text-primary` KPI values, segmented pill filters, `py-6` Cards) for consistency with the rest of the admin app, and used realistic varied placeholder data rather than generic names per this repo's existing content conventions.

## Verification
- `pnpm run build` (`tsc -b && vite build`): **PASS**.
- `pnpm exec eslint` on all 10 new/touched files: **PASS**, 0 errors/warnings.
- **Not verified in a live browser** — no browser/screenshot tool available in this session. Build+lint confirm the code compiles and type-checks, but the actual rendered layout (grid wrapping, avatar fallback sizing, pill alignment) has not been visually inspected; recommend a quick manual check of `/admin/staff` after `pnpm run dev`.
