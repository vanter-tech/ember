# Platform console redesign (piece D)

**Date:** 2026-09-06
**Status:** design approved, pending implementation plan
**Scope:** piece **D** of the platform-console effort — bring the `/console` operator UI up to
the tenant SaaS app's visual quality. Pieces **A** (`updateRole` guard, PR #80), **B**
(soft-delete tenants) and **C** (Hub liveness) are done — **D builds on B+C**
(`spec/platform-console-retire-liveness`, PR #81), which redesign task 4/5 re-skins.

---

## 1. Problem

`/console` is a functional but visually raw operator tool. `PlatformLayout` is a bare top bar
with no navigation; `ConsoleDashboard` is a 15-line stub (a heading + one text link);
`ConsoleRestaurants` / `ConsoleRestaurantDetail` hand-roll `<table>` markup and a `fixed inset-0`
modal instead of using the shadcn primitives the rest of the app uses. Strings are inconsistent
(mixed English/Spanish). Brand colour is `#920703` where the tenant app uses `#8c1717`.

## 2. Goals

- A real console **shell**: a fixed left sidebar with navigation, matching the tenant app's
  visual language (`#8c1717`, `bg-zinc-50/50`, white `rounded-2xl` cards, `lucide` icons).
- A real **dashboard**: tenant + Hub KPIs and a platform-wide recent-activity feed.
- Every console screen rebuilt on the shadcn primitives (`Table`, `Badge`, `Dialog`, `Switch`,
  `Card`) with consistent loading / empty / error states.
- All user-facing strings in **Spanish**, inline (no i18n layer).

## 3. Non-goals

- No i18n layer (`useTranslation` / `locales/console.*`). The i18n specs deliberately exclude
  `/console`; D keeps that and only makes the inline strings consistently Spanish.
- No change to auth logic: `platformAuthStore`, `PlatformProtectedRoute`, the login/token flow
  are untouched (visual polish of the login screen only).
- No new operator capabilities — no RBAC, no operator management, no per-operator settings.
- No dark mode. No mobile-first `FloatingNav` (the console is a desktop tool; the sidebar
  collapses to a drawer under `md`, that is all).
- No backend change beyond the single `GET /platform/stats` endpoint in §4.3.

---

## 4. Design

### 4.1 Shell — `PlatformLayout` + `ConsoleSidebar`

`PlatformLayout` becomes: **fixed left sidebar** (`w-60`, full height, white, `border-r`) +
**thin top header** (operator name, "Cambiar contraseña" link, "Cerrar sesión" button) +
**content region** (`bg-zinc-50/50`, `p-6`, `<Outlet/>`).

New `frontend/src/components/console/ConsoleSidebar.tsx`:
- Top: the Ember wordmark in `#8c1717` + the operator's name/email (from `usePlatformAuthStore`).
- Nav: a list of `{ to, label, icon }` — **Dashboard** (`LayoutDashboard`), **Restaurantes**
  (`Store`). Active item resolved with `NavLink` / `useLocation` — active = `bg-[#8c1717]/10
  text-[#8c1717] font-medium`, idle = `text-zinc-600 hover:bg-zinc-100`.
- Bottom: `Cambiar contraseña` (link to `/console/password`) + `Cerrar sesión` (calls
  `logout()` then `navigate('/console/login')`).

Responsive: under `md` the sidebar is hidden and a hamburger in the header toggles it as an
overlay drawer (`fixed`, `bg-black/40` scrim). State is local `useState` in `PlatformLayout`.

New `frontend/src/components/console/ConsolePageHeader.tsx`: `{ title: string; action?: ReactNode }`
— an `<h1 className="text-2xl font-semibold">` row with the optional action button on the right.
Used by every page for a consistent header.

New `frontend/src/components/console/HubBadge.tsx`: `{ status: HubStatus }` → a coloured dot +
label. `ONLINE` green, `STALE` amber, `OFFLINE` zinc-400, `NEVER` "—" (transparent dot). Used by
the list, the detail Hub panel, and the dashboard KPI card. (The ad-hoc `hubDot` helper added to
`ConsoleRestaurants` in piece C is deleted and replaced by this.)

### 4.2 Dashboard — `ConsoleDashboard`

Replaces the stub. Three stacked blocks inside the content region:

1. **KPI row** — a responsive grid of small stat cards (`Card`, `rounded-2xl`, icon + big number
   + label):
   - Tenants: **Activos**, **Suspendidos**, **Eliminados** (icons `Store`, `PauseCircle`,
     `Trash2`).
   - Hubs: **Online**, **Stale**, **Offline**, **Nunca** — each with the matching `HubBadge`
     colour dot.
   - Data from `platformStatsService.get()` (§4.3). Skeleton (`animate-pulse` grey blocks) while
     loading; on error a small inline "No se pudieron cargar las métricas".

2. **Actividad reciente** — a `Card` with a `Table` of the last 10 platform-wide audit entries:
   columns *Fecha* (relative — "hace 3 min", plus absolute title), *Operador*, *Acción*
   (`Badge`), *Restaurante* (a `Link` to `/console/restaurants/{restaurantId}` when
   `restaurantId` is set, else "—"). Data:
   `platformAuditLogService.getRecent(0, 10)` → the **existing** `GET /platform/audit-log`
   with no `restaurantId` (already returns platform-wide, newest first). Empty state: "Sin
   actividad registrada."

3. **Accesos rápidos** — two `Button`s: "Nuevo restaurante" (`→ /console/restaurants/new`),
   "Ver restaurantes" (`→ /console/restaurants`).

`platformAuditLogService` gains `getRecent(page = 0, size = 10)` — a thin wrapper calling
`/platform/audit-log` with `{ page, size }` and no `restaurantId`.

### 4.3 Backend — `GET /platform/stats`

The only backend change. New:

- `platform/model/dto/PlatformStatsResponse.java`:
  ```
  record PlatformStatsResponse(TenantCounts tenants, HubCounts hubs) {
      record TenantCounts(long active, long suspended, long deleted) {}
      record HubCounts(long online, long stale, long offline, long never) {}
  }
  ```
- `platform/service/PlatformStatsService.java` — `PlatformStatsResponse get()`:
  - `tenants`: `restaurantRepository.countByStatus(ACTIVE|SUSPENDED|DELETED)` (new derived
    method `long countByStatus(RestaurantStatus)`).
  - `hubs`: `hubActivationRepository.findAll()` (rows are few — one per licensed Hub), bucket
    each by `HubStatus.from(row.getLastHeartbeatAt(), Instant.now())`. A row with a null
    `lastHeartbeatAt` counts as `never`; a restaurant with no activation row is **not** a Hub
    and is not counted here.
- `platform/controller/PlatformStatsController.java` — `@GetMapping("/platform/stats")` →
  `ResponseEntity.ok(service.get())`. Operator-authenticated like every other `/platform/**`
  route (covered by `PlatformSecurityConfig`).
- `frontend/src/lib/platformApi.ts` — `PlatformStats` interface mirroring the DTO +
  `platformStatsService.get()`.

### 4.4 Restaurantes list — `ConsoleRestaurants` re-skin

Behaviour is **unchanged** (piece C's Hub column, "Ver eliminados", muted DELETED rows). Only
the markup changes:

- `<ConsolePageHeader title="Restaurantes" action={<Button>Nuevo restaurante</Button>} />`.
- The hand-rolled `<table>` → shadcn `<Table>` / `<TableHeader>` / `<TableRow>` / `<TableCell>`.
- Status cell → `<Badge>` with a `variant`/class per status (`ACTIVE` green, `SUSPENDED` red,
  `DELETED` zinc). Hub cell → `<HubBadge status={r.hubStatus} />`.
- "Ver eliminados" raw `<input type="checkbox">` → shadcn `<Switch>` + `<Label>`.
- Loading → a `<Table>` body of ~6 skeleton rows (not the current bare "Cargando…" text).
- Empty → a single centered `<TableRow>` "Sin restaurantes registrados."
- Pagination control unchanged.

### 4.5 Restaurante detalle — `ConsoleRestaurantDetail` re-skin

Behaviour **unchanged** (piece B/C: delete only when SUSPENDED with slug-confirm, restore when
DELETED, Hub panel). Markup:

- `<ConsolePageHeader>` with the back-link above the title and the action buttons as its `action`.
- The two info grids and the Hub panel → `<Card>` with `<CardHeader><CardTitle>` + a
  `<dl>`/grid body. Statuses → `<Badge>`; Hub status → `<HubBadge>`.
- Admins table + audit-log table → shadcn `<Table>`.
- The delete-confirm `fixed inset-0` modal → shadcn **`<Dialog>`** (`DialogContent`,
  `DialogHeader`, `DialogTitle`, `DialogFooter`). The type-the-slug input and the
  disabled-until-match "Confirmar eliminación" button move inside it unchanged.
- The license-key download flow (`issueHubLicense` blob → `<a download>`) is unchanged.

### 4.6 Login / Create / PasswordChange — polish

These already use `Card` / `Form` / `Input`. Changes only:

- Primary colour `#920703` → `#8c1717` (and the login title).
- All strings → Spanish ("Login" → "Iniciar sesión", "Log out" → "Cerrar sesión", "Login
  successful!" → "Sesión iniciada", "Sign in with your operator account." → "Ingresá con tu
  cuenta de operador.", etc. — full sweep, listed in the plan).
- `ConsoleLogin`: add the Ember wordmark above the card.
- `ConsoleRestaurantCreate` / `ConsolePasswordChange`: back-link + title via `ConsolePageHeader`
  for consistency.

### 4.7 Shared cleanup

- Delete the `hubDot` helper from `ConsoleRestaurants` (piece C) — replaced by `HubBadge`.
- One primary-button colour across the console: `#8c1717`.
- `frontend/src/components/console/` is the new home for console-only components
  (`ConsoleSidebar`, `ConsolePageHeader`, `HubBadge`).

---

## 5. Testing

**Backend**
- `PlatformStatsServiceTest` (Mockito): tenant counts read from `countByStatus`; hub bucketing
  over a mixed set of `hub_activations` rows (online / stale / offline / null→never); empty →
  all zeros.
- `PlatformStatsControllerTest` (`@WebMvcTest` + `PlatformSecurityConfig`): 401 without an
  operator token; 200 with the expected JSON shape.
- `@DataJpaTest` for `RestaurantRepository.countByStatus` (with
  `@Import(TenantIdentifierResolver.class)` — the project-wide `@TenantId` scan gotcha).

**Frontend (Vitest)**
- `PlatformLayout` / `ConsoleSidebar`: renders the nav items; the active item reflects the
  current route; "Cerrar sesión" calls `logout` and navigates; the `md` drawer toggles.
- `ConsoleDashboard`: KPI cards render the numbers from a mocked `platformStatsService.get`; the
  activity feed renders mocked audit rows and links to a restaurant; the loading skeleton shows
  before data resolves; the stats-error message shows on rejection.
- `ConsoleRestaurants` / `ConsoleRestaurantDetail`: the piece-B/C tests are updated to the new
  markup — assertions stay behaviour-level (`getByRole('button', …)`, visible text, the
  slug-confirm `Dialog`, `deleteRestaurant`/`restoreRestaurant`/`getAll(…, true)` calls) so the
  re-skin is proven not to regress B/C.

**Full verification:** `cd backend && ./mvnw test`; `cd frontend && pnpm run build` +
`pnpm run lint` + `pnpm run test:run`.

---

## 6. Delivery

One branch, one PR, targeting whatever `spec/platform-console-retire-liveness` (PR #81) merges
into — D is cut from that branch because it re-skins C's `ConsoleRestaurants` /
`ConsoleRestaurantDetail`. If #81 merges to `main` first, rebase D onto `main`; the PR then
stands alone.

## 7. Open items for the implementation plan

- Confirm the exact shadcn `<Table>` / `<Dialog>` / `<Switch>` import paths and prop shapes from
  an existing tenant-app usage (e.g. a staff table, an `EditStaffModal` dialog).
- Decide whether `ConsoleSidebar`'s nav list is a local `const` or a tiny module export (only
  two items today — a local `const` is fine).
- The `Badge` component's API: does it take a `variant`, or is it class-only? Match how the
  tenant app styles status badges.
