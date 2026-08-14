# Report 37 — task-4.3: tenant onboarding UX (slug-based pre-login landing page)

## 1. Identification
- **Report number:** 37
- **Task ID:** task-4.3
- **Predecessor task:** task-4.2 (report 36 — `api.ts` tenant-id audit / `Page<T>` envelope)

## 2. Objective
Build a pre-login, slug-routed landing page that shows a tenant's public branding (business name,
theme color, hours) before a visitor authenticates, per task-3.4's dynamic-origin groundwork
(tenants are eventually served from `<slug>.ember.vanter.com`, but real subdomain routing isn't
practical in local dev on Windows, so this task lands the path-based `/t/:slug` shape now — see
§6 for the explicit design decisions confirmed with the user before implementation).

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/restaurant/model/dto/PublicBrandingResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/restaurant/controller/PublicRestaurantController.java` (new)
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- `backend/src/test/java/com/vanter/ember/restaurant/controller/PublicRestaurantControllerTest.java` (new)
- `frontend/src/lib/api.ts`
- `frontend/src/pages/public/TenantLanding.tsx` (new)
- `frontend/src/App.tsx`

## 4. What Changed?

### 4.1 Backend: `GET /public/restaurants/{slug}/branding`
New `PublicRestaurantController` resolves a `Restaurant` by slug via the existing
`RestaurantRepository.findBySlug`, then temporarily binds `TenantContextHolder` to that
restaurant's id (in a `try`/`finally`, mirroring the bind-then-clear pattern `jwtAuthFilter`
already uses) so it can call the existing `SettingService.getSettings(restaurantId)` — required
because `RestaurantSettings` carries `@TenantId` and is filtered off `TenantContextHolder`
regardless of caller. Unknown slugs raise the existing `ResourceNotFoundException` (→ 404 via
`GlobalExceptionHandler`, no new exception type). `SecurityConfig` adds `/public/**` to the
`permitAll` matcher list alongside `/auth/**`.

### 4.2 Backend: curated public DTO
`PublicBrandingResponse` (new) exposes only `slug`, `businessName`, `primaryThemeColor`,
`openingTime`, `closingTime` from `SettingsPayload.BrandingSettings` — `legalName`, `ruc`,
`phone`, `address`, and `wifiName` are deliberately excluded from this unauthenticated response
and stay behind the authenticated `GET /settings` endpoint. `businessName` falls back to
`Restaurant.name` when branding hasn't been configured yet, so a freshly-registered tenant still
gets a usable landing page.

### 4.3 Backend tests
`PublicRestaurantControllerTest` (`@WebMvcTest`, following the `DashboardControllerTest` pattern)
covers: unauthenticated 200 with the curated fields present and `ruc`/`phone` absent from the JSON
body, 404 on an unknown slug, and that `TenantContextHolder` is cleared after both a successful
response and a downstream exception (the `finally` block).

### 4.4 Frontend: `publicService.getBranding` and `Page<T>`-style hand-written type
Added `PublicBranding` interface and `publicService.getBranding(slug)` to `api.ts`, hand-written
(not generated) for the same reason as task-4.2's `Page<T>`: `pnpm run openapi` needs a live
backend, and this is one small, stable shape. Reuses the shared `api` axios instance as-is — no
Authorization header is attached when there's no token, which is the expected unauthenticated
case here.

### 4.5 Frontend: `TenantLanding.tsx` and routing
New `pages/public/TenantLanding.tsx` reads `:slug` from the URL, fetches branding via
`useQuery`/`publicService.getBranding`, and renders the business name (in the tenant's
`primaryThemeColor`, falling back to Ember's default red) with "Iniciar sesión"/"Registrarme"
buttons linking to the existing `/login`/`/register` routes — those forms are unchanged; the
landing page is purely a branded entry point in front of them. Added `/t/:slug` to `App.tsx`'s
route table; `/`'s existing `RoleRedirect` behavior for authenticated users is untouched.

## 5. Why It Changed?
Task-4.3 explicitly calls for tenant-onboarding UX ahead of login, and depends on task-3.4's
dynamic CORS/origin config, which already anticipated per-tenant subdomains
(`CorsProperties.allowedOriginPatterns`) but never got a corresponding public data endpoint or
frontend page.

## 6. Design decisions confirmed with the user before implementation
Presented as an `AskUserQuestion` before writing code, since each choice materially changes the
shape of the feature:
- **Path-based `/t/:slug` for now, not real subdomains** — Windows doesn't auto-resolve
  `*.localhost` without per-tenant hosts-file entries, so path-based routing was chosen for local
  dev; production subdomain routing can layer on top later without touching this endpoint.
- **Curated public DTO, not the full branding object** — avoids exposing RUC/legal name/address/
  phone/wifi name to anyone who knows or guesses a slug.
- **Bind/clear `TenantContextHolder` from the DB-verified slug lookup** (not a second
  filter-bypass mechanism) — keeps exactly one way the tenant filter ever gets bound, sourced
  either from a verified JWT claim or (here) a verified `RestaurantRepository` lookup, never raw
  client input.
- **New dedicated route, not replacing `/`** — keeps `RoleRedirect`'s authenticated-user behavior
  and `/login`/`/register` untouched.

## 7. Verification
- `./mvnw test` → **435/435 passing** (baseline 431; net +4, all in
  `PublicRestaurantControllerTest`).
- `cd frontend && pnpm run build` → `tsc -b` clean (0 errors), `vite build` succeeded.
