# Report 385 — feat(console): platform console redesign (piece D)

## Identification
- **Report:** 385
- **Task:** piece **D** of the platform-console effort — redesign the `/console` operator UI to
  the tenant SaaS app's visual quality. Executed from
  `docs/superpowers/plans/2026-09-06-platform-console-redesign.md`.
- **Predecessor:** report 384 (retire tenants + Hub liveness, pieces B+C).
- **Branch:** `spec/platform-console-redesign`, cut from `spec/platform-console-retire-liveness`
  (PR #81) — D re-skins pieces C's `ConsoleRestaurants` / `ConsoleRestaurantDetail`. 8
  implementation commits (`6b526161` … `cfe08af7`) + spec + plan.

## Objective
`/console` was functional but visually raw: `PlatformLayout` a bare top bar with no navigation,
`ConsoleDashboard` a 15-line stub, `ConsoleRestaurants` / `ConsoleRestaurantDetail` hand-rolled
`<table>` markup and a `fixed inset-0` modal, strings mixed English/Spanish, brand colour
`#920703` where the tenant app uses `#8c1717`. Give it a sidebar shell, a real dashboard, and
every screen on the shadcn primitives — with **no behaviour change** to pieces B/C.

## Modified Files

**Backend — created**
- `platform/model/dto/PlatformStatsResponse.java`, `platform/service/PlatformStatsService.java`,
  `platform/controller/PlatformStatsController.java`
- Tests: `PlatformStatsServiceTest`, `PlatformStatsControllerTest`,
  `RestaurantRepositoryCountByStatusTest`

**Backend — modified**
- `restaurant/repository/RestaurantRepository.java` (+`countByStatus`)

**Frontend — created**
- `components/console/HubBadge.tsx` (+ `HubBadge.test.tsx`),
  `components/console/ConsolePageHeader.tsx`, `components/console/ConsoleSidebar.tsx`
- `layouts/PlatformLayout.test.tsx`, `pages/console/ConsoleDashboard.test.tsx`,
  `pages/console/ConsoleLogin.test.tsx`

**Frontend — modified**
- `lib/platformApi.ts` (`PlatformStats` type, `platformStatsService.get`,
  `platformAuditLogService.getRecent`)
- `layouts/PlatformLayout.tsx`, `pages/console/ConsoleDashboard.tsx`,
  `pages/console/ConsoleRestaurants.tsx` (+ `.test.tsx`),
  `pages/console/ConsoleRestaurantDetail.tsx`, `pages/console/ConsoleLogin.tsx`,
  `pages/console/ConsoleRestaurantCreate.tsx`, `pages/console/ConsolePasswordChange.tsx`

## What Changed?

**Backend — `GET /platform/stats` (the only backend addition).**
`PlatformStatsResponse` = `TenantCounts(active, suspended, deleted)` + `HubCounts(online, stale,
offline, never)`. `PlatformStatsService` reads tenant counts via a derived
`RestaurantRepository.countByStatus` and buckets `hub_activations` rows with `HubStatus.from`
(a row with a null `last_heartbeat_at` → `never`; restaurants with no activation row are not
Hubs and are not counted). Operator-authenticated like the rest of `/platform/**`.

**Shell (§4.1).** `PlatformLayout` → a fixed left sidebar (`ConsoleSidebar`: Ember wordmark +
operator name, `NavLink` items *Dashboard* / *Restaurantes* with `lucide` icons and an active
state in `#8c1717`) + a thin header (operator, *Cambiar contraseña*, *Cerrar sesión*) + the
content region. Under `md` the sidebar is a hamburger-toggled overlay drawer. New shared
`ConsolePageHeader` (title + optional action) and `HubBadge` (dot + label — replaces the ad-hoc
`hubDot` helper piece C added to `ConsoleRestaurants`).

**Dashboard (§4.2).** `ConsoleDashboard` rebuilt: a KPI row of `StatCard`s (tenants by status,
Hubs by liveness, each with its `HubBadge`), an "Actividad reciente" `Table` of the last 10
platform-wide audit entries (`platformAuditLogService.getRecent` → the existing
`GET /platform/audit-log` with no `restaurantId`), skeleton + error states, and two quick-action
buttons.

**Re-skins (§4.4–4.6).** `ConsoleRestaurants` and `ConsoleRestaurantDetail` moved onto shadcn
`<Table>` / `<Badge>` / `<Switch>` / `<Card>`; the detail's delete-confirm `fixed inset-0` modal
became a shadcn `<Dialog>`. All piece-B/C behaviour is preserved — delete only when `SUSPENDED`
with a type-the-slug confirm, restore when `DELETED`, `?includeDeleted`, the Hub panel data, the
license-key download. `ConsoleLogin` / `ConsoleRestaurantCreate` / `ConsolePasswordChange` got
`#8c1717` and a full sweep of strings (labels, zod messages, toasts) to Spanish, plus an Ember
wordmark on the login screen.

## Why It Changed?
The console is an internal operator tool; matching the tenant app's design language (not its
mobile-first `FloatingNav`) makes it navigable and legible without inventing a new system. A
sidebar scales as operator features grow. The dashboard needed one small read endpoint —
everything else the console already had. i18n was deliberately skipped (the i18n specs exclude
`/console`); the strings are just made consistently Spanish.

## Plan deviations
None of substance. `ConsoleRestaurantCreate` / `ConsolePasswordChange` dropped their
`<CardHeader><CardTitle>` in favour of `ConsolePageHeader`, so their `card` imports were trimmed
to avoid unused-import lint errors.

## Verification
- `cd backend && ./mvnw test` — full suite green.
- `cd frontend && pnpm run build` clean, `pnpm run lint` 0 errors (16 pre-existing warnings in
  unrelated files), `pnpm run test:run` **90/90** (30 files, +7 new console tests).
- Grep for `#920703` / `Log out` / `Login successful` / `Enter your` across the console — no
  matches.

## Delivery
Depends on PR #81 (pieces B/C). If #81 has merged to `main`, rebase this branch onto `main`
before merging so the PR is a clean diff.
