# Staff Management (EMB-FloatingNav follow-up) — Design Spec

**Date:** 2026-08-15
**Status:** Approved design, ready for implementation planning

---

## Overview

`/admin/employees` ("Gestión de Personal") already exists as a frontend-only scaffold (report 114): header, department pill filters, employee card grid + ghost "add role" card, bottom KPIs — built against a static `MOCK_STAFF` array. This spec makes it real: staff are the existing tenant-scoped `User` rows with `role != CUSTOMER`, extended with a handful of new HR-flavored columns, exposed through two new endpoints, and wired into the page in place of the mock.

This also fixes a UI bug found while reviewing the scaffold: the page rendered its own search bar + "add" button, duplicating ones that already exist globally in `TopNav.tsx` (which even had an unused route branch anticipating `/admin/employees`).

---

## Scope

### In Scope
- New nullable HR columns on `User` (`active`, `shift`, `contract_type`, `location`, `efficiency_percentage`, `pending_hours`).
- `GET /admin/staff` — list staff for the current tenant.
- `PATCH /admin/staff/{userId}` — update the new HR fields (gives the "active" toggle a real target).
- Idempotent boot-time backfill of `active = true` for existing non-CUSTOMER users.
- Frontend: `staffService` + `useQuery` replacing `MOCK_STAFF`; remove the duplicate search/button from the page; rename the route to `/admin/employees` to match `TopNav`'s existing special case; wire `TopNav`'s (currently decorative) search input to actually filter the page via a small addition to `useUIStore`.
- Simplify `StaffDepartment` away — department filter becomes the real `Role` values (`WAITER`/`KITCHEN`/`ADMIN`).

### Out of Scope (Deliberately, Not Oversight)
- **Staff creation.** No `POST` endpoint, no modal. There is no existing flow anywhere in the app for an ADMIN to create a WAITER/KITCHEN/ADMIN account (`AuthService.register` always mints `Role.CUSTOMER`) — building one means deciding a password/invite strategy, which is its own task. The "+ Nuevo empleado" button (now living in `TopNav`) stays visually present but inert.
- **Editing the new HR fields from the UI.** The `PATCH` endpoint exists; no edit form is built. "Perfil" and the `...` actions menu on each card stay inert.
- **A "Cleaning" department.** No `Role` equivalent exists. Dropped from the filter list entirely rather than faked; revisit if/when real cleaning staff need accounts.
- **Real presence/online tracking.** `active` is a manually-set flag (defaults `true` via backfill), not a live session/heartbeat signal.
- **`backend-types.ts` regeneration.** No live backend to run `pnpm run openapi` against right now — the new staff types are hand-written in `api.ts`, same approach `platformApi.ts` already uses for platform types. Regenerate for real next time the backend is up.

---

## Backend

### Data model

New nullable columns added directly to `users` (not a separate `StaffProfile` entity/module — this is always a strict 1:1 with `User`, so a second table would only add a join with no functional benefit):

| Column                  | Type      | Default | Notes                                          |
|--------------------------|-----------|---------|-------------------------------------------------|
| `active`                 | boolean   | `true`  | Drives the status dot + "Activos ahora" KPI     |
| `job_title`              | varchar   | `null`  | Free text: "Chef ejecutiva" — the mockup's "Role Label", distinct from the department badge (which stays derived from `role`) |
| `shift`                  | varchar   | `null`  | Free text: "Mañana" / "Tarde" / "Noche"         |
| `contract_type`          | varchar   | `null`  | Free text: "Tiempo completo" / "Medio tiempo"   |
| `location`               | varchar   | `null`  | Free text, e.g. "Piso principal"                |
| `efficiency_percentage`  | numeric   | `null`  |                                                  |
| `pending_hours`          | numeric   | `0`     | Summed for the "Horas pendientes" KPI           |

Migration: `V6__staff_profile_fields.sql`, `ALTER TABLE users ADD COLUMN ...` for all seven columns. `active`/`pending_hours` carry a literal `DEFAULT`, so Postgres backfills every pre-existing row in the same DDL statement — no separate runtime backfill job needed (unlike the Mongo `KitchenOrderActiveBackfill` precedent, which exists only because MongoDB has no `ALTER ... DEFAULT` equivalent).

### Backfill

Handled entirely by the migration's column `DEFAULT` (see above) — no `ApplicationRunner` needed. `User.builder()` gets `@Builder.Default private Boolean active = true;` and `@Builder.Default private BigDecimal pendingHours = BigDecimal.ZERO;` so every existing code path that builds a `User` without setting these (registration, tenant onboarding, test fixtures) still sends a non-null value — without `@Builder.Default`, Lombok's builder would send an explicit `NULL`, which fails the `NOT NULL` constraint despite the column's DDL default (a default only applies when the column is omitted from the `INSERT`, not when `NULL` is sent explicitly).

### Endpoints

Both live in `identity/controller/UserAdminController.java` (or a new `StaffController` in the same package if the file is getting crowded — implementer's call), gated the same way as `updateRole`: `@PreAuthorize("hasRole('ADMIN')")`, tenant always via `TenantContextHolder.requireTenantId()`.

- **`GET /admin/staff`**
  Returns all `User` rows where `restaurantId = current tenant` and `role != CUSTOMER`. No pagination (small roster per tenant; matches how `ProductPerformance`/`TableAnalytics` keep filtering client-side rather than server-side). Response is a flat list of staff DTOs: `id`, `name`, `email`, `role`, `createdAt`, `active`, `jobTitle`, `shift`, `contractType`, `location`, `efficiencyPercentage`, `pendingHours`.

- **`PATCH /admin/staff/{userId}`**
  Body carries any subset of the seven new fields (all optional). Same shape/spirit as `UpdateUserRoleRequest` + `UserAdminController.updateRole`, but ALSO verifies the target user belongs to the caller's tenant (404 if not, same as "doesn't exist" — `updateRole` itself doesn't do this today, a pre-existing gap this endpoint won't repeat but also won't fix). Add the route to `SecurityAuditTest`'s 401 matrix, per the existing convention for every new analytics/admin route.

---

## Frontend

- **`api.ts`**: new `StaffMemberResponse` type (mirrors the backend DTO exactly, becomes THE staff shape used everywhere in the frontend — no separate local `StaffMember` type) + `staffService.getAll(): Promise<StaffMemberResponse[]>` (`GET /admin/staff`) and `staffService.updateProfile(userId, payload): Promise<StaffMemberResponse>` (`PATCH /admin/staff/{userId}`). Hand-written (no live backend to regenerate `backend-types.ts` from right now).
- **`pages/admin/staff/types.ts`**: drop `StaffDepartment`/`DEPARTMENT_CONFIG`/`StaffMember`/`StaffMetadataItem` entirely. Filter becomes `'ALL' | StaffRole` directly over the real role values. `ROLE_LABELS`/`ROLE_BADGE_CLASSNAMES` replace the old made-up department mapping.
- **`StaffCard.tsx`**: metadata pills are built from whichever of `shift`/`contractType`/`location`/`efficiencyPercentage` are non-null (0-4 pills, not a fixed tuple); subtitle under the name is `jobTitle` (falls back to `email` when unset, so the line is never blank); status dot reads `active` instead of the old invented `status` field; drops `AvatarImage`/`avatarUrl` (no such field exists — initials-only fallback).
- **`pages/admin/staff/mock-data.ts`**: deleted.
- **`pages/admin/staff/Staff.tsx`**: `useQuery(['staff'], staffService.getAll)` replaces `MOCK_STAFF`; loading/error states styled like the rest of the admin app (centered, `text-muted-foreground`/`text-destructive`).
- **`StaffHeader.tsx`**: strips the search `Input` and "Nuevo empleado" `Button` — keeps only the title/subtitle, matching `Analytics.tsx`'s header.
- **`useUIStore.ts`**: add `searchTerm: string` + `setSearchTerm(value: string)`. `TopNav.tsx`'s existing (currently unwired) search `<input>` becomes controlled from this store, and clears `searchTerm` on every route change (`useEffect` keyed on the current path) so leftover text doesn't linger when navigating to a page that doesn't use it. `Staff.tsx` reads `searchTerm` instead of local state for its name filter. Every other admin page keeps ignoring the value — zero behavior change elsewhere.
- **Route rename**: `/admin/staff` → `/admin/employees` in `App.tsx` and the `FloatingNav.tsx` link, to match `TopNav.tsx`'s pre-existing (previously unused) `path.includes('/admin/employees')` branch (`buttonText = 'Nuevo empleado'`, `searchPlaceholder = 'Buscar empleados...'`). The `pages/admin/staff/` folder name is unaffected — only the URL and nav link change.

---

## Explicitly Deferred Follow-ups

- Staff creation flow (password/invite strategy — mirror the decision EMB-PC-08 made for tenant onboarding, or design fresh).
- Edit UI for the new HR fields (the `PATCH` endpoint is ready for it).
- A real "Cleaning" role/department once actual cleaning staff need accounts.
- `backend-types.ts` regeneration once the backend is next run live.
