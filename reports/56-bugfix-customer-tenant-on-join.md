# Report 56 — bugfix-customer-tenant-on-join

## Identification
- **Report:** 56
- **Task ID:** bugfix-customer-tenant-on-join
- **Predecessor Task:** bugfix-customer-login-500 (report 55)

## Objective
Correct report 55's fix. That change made customers pass a `restaurantSlug` at login, sourced from the `/t/:slug` landing page — but nothing in the app ever links to `/t/:slug`, so every customer login failed with `409 restaurantSlug is required`. Move tenant resolution to where the customer actually picks a restaurant: joining a table.

## Modified Files
**Backend**
- `identity/model/dto/LoginRequest.java`, `identity/model/dto/RegisterRequest.java`
- `identity/service/AuthService.java`
- `restaurant/service/RestaurantService.java`
- `config/SecurityConfig.java`
- `session/controller/SessionController.java`
- `session/service/SessionService.java`, `session/service/QrTokenService.java`
- `session/repository/SessionRepository.java`
- `session/dto/JoinSessionResponse.java` (new)
- Tests: `E2EOrderFlowTest`, `AuthServiceTest`, `AuthControllerTest`, `SessionServiceTest`, `SessionControllerTest`, `QrTokenServiceTest`

**Frontend**
- `pages/auth/Login.tsx`, `pages/auth/Register.tsx`, `pages/public/TenantLanding.tsx` (report 55's query-param wiring reverted)
- `pages/customer/components/JoinTableModal.tsx`
- `lib/api.ts`, `lib/backend-types.ts` (regenerated)

## What Changed?
- **Login/register carry no restaurant at all.** `restaurantSlug` is gone from both DTOs. `AuthService.tenantIdOf` returns `null` for `CUSTOMER` (even when a legacy row still has `User.restaurantId` set) and the stored restaurant for staff, so a customer's JWT has no `rid` claim.
- **`SecurityConfig.jwtAuthFilter` lets a tenant-less CUSTOMER through** instead of 403-ing them as an unknown tenant. Staff without a valid active tenant are still blocked exactly as before.
- **Joining a table is what resolves the restaurant.** `joinSessionCode` looks the code up across tenants via a new `SessionRepository.findByJoinCodeAndStatus`, then binds that session's own `tenantId`; `joinSession` (QR) takes the tenant from the server-signed QR token, which `QrTokenService.validateQrToken` now returns alongside the session id as a `QrTokenData` record. Both run through `SessionService.bindResolvedTenant`, which verifies the restaurant is `ACTIVE` before binding.
- **Both join endpoints return `JoinSessionResponse { session, token }`** — a token re-scoped to that restaurant, which the client swaps in (`JoinTableModal`) for every later call. `AuthService.issueTenantScopedToken` mints it and refuses to re-scope non-CUSTOMER users, and both endpoints are now `@PreAuthorize("hasRole('CUSTOMER')")`.
- Join codes are random, not unique, so a code open at two restaurants is rejected rather than guessed.
- Removed `RestaurantService.createOrJoin`/`getBySlug` and their private slug helpers — all dead once registration stopped creating or joining restaurants.

## Why It Changed?
Report 55 assumed the customer's restaurant was known before login. Checking the actual UI showed it isn't: `/t/:slug` has no inbound link, and the real entry point is `JoinTableModal` inside the already-logged-in customer app, where the diner types a 5-digit table code or scans a QR. A customer genuinely has no restaurant until that moment — which is exactly the model described for the product.

Simply dropping the tenant from customer tokens wasn't enough: `jwtAuthFilter` resolves a tenant on every authenticated request, and `MenuItem`/`Category` reads depend on Hibernate's discriminator picking up `TenantContextHolder`. Re-issuing the token at join keeps that whole architecture intact — one new response field instead of rewriting every customer-facing service to resolve tenants per resource. The `ACTIVE` check inside `bindResolvedTenant` preserves the suspended-tenant gate that the filter can no longer apply to these requests, and pinning staff to their own tenant in `issueTenantScopedToken` stops the new join path from ever widening a waiter's access.

## Verification
- `./mvnw clean test` — 438/438 passing (was 433; net +5 covering tenant-less login, join-time scoping, cross-restaurant code collisions, suspended-tenant joins, and staff re-scoping refusal). `E2EOrderFlowTest` exercises the whole corrected path: tenant-less customer login → QR join → token swap → add item → confirm → kitchen transitions → bill → payment → session closed.
- `pnpm run build` — PASSING, 0 TS errors, after regenerating `backend-types.ts` against the restarted backend.
- Live against the running stack: `POST /auth/login` for `fer3@example.com` now returns 200 with `restaurantId: null` and a JWT carrying no `rid` (was 500, then 409 after report 55). That tenant-less token is accepted by `jwtAuthFilter` and reaches the join controller — `POST /sessions/join` with an unknown code returns `404 Code not found`, not the old `403 Tenant account not found`.
