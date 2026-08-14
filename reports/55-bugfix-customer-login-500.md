# Report 55 — bugfix-customer-login-500

## Identification
- **Report:** 55
- **Task ID:** bugfix-customer-login-500
- **Predecessor Task:** task-5.11 (report 54)

## Objective
Fix `POST /auth/login` returning a 500 for `fer3@example.com` (and 5 other pre-existing CUSTOMER accounts), and align the backend with the intended design: a customer is not bound to one restaurant — they join whatever table/restaurant they scan a QR code or type a table code for.

## Modified Files
- `backend/src/main/java/com/vanter/ember/identity/model/User.java`
- `backend/src/main/java/com/vanter/ember/identity/model/dto/LoginRequest.java`
- `backend/src/main/java/com/vanter/ember/identity/model/dto/RegisterRequest.java`
- `backend/src/main/java/com/vanter/ember/identity/service/AuthService.java`
- `backend/src/main/java/com/vanter/ember/restaurant/service/RestaurantService.java`
- `backend/src/main/java/com/vanter/ember/session/service/SessionService.java`
- `backend/src/test/java/com/vanter/ember/E2EOrderFlowTest.java`
- `backend/src/test/java/com/vanter/ember/identity/controller/AuthControllerTest.java`
- `backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java`
- `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java`
- `frontend/src/lib/backend-types.ts` (regenerated)
- `frontend/src/pages/auth/Login.tsx`
- `frontend/src/pages/auth/Register.tsx`
- `frontend/src/pages/public/TenantLanding.tsx`

## What Changed?
- `AuthService.login` no longer dereferences `user.getRestaurantId()` unconditionally. For `CUSTOMER` role it now resolves the restaurant from a new `LoginRequest.restaurantSlug` field (409 if omitted); for `ADMIN`/`WAITER` it keeps using the stored `User.restaurantId` as before.
- `AuthService.register` no longer binds a `Restaurant` onto `User.restaurantId` for customers; it resolves the target restaurant purely from `RegisterRequest.restaurantSlug` (now `@NotBlank`, replacing the removed `restaurantName`/create-new-restaurant path, which never applied to the CUSTOMER-only public register endpoint).
- `RestaurantService.getBySlug` added (404 via `ResourceNotFoundException`) as the shared slug→restaurant lookup for both flows.
- `User.restaurantId`'s `@JoinColumn` is now `nullable = true`, matching the DB column (which had no `NOT NULL` constraint) and making null the documented, intentional state for customers.
- `SessionService.confirmDraftsForUser` no longer compares `requester.getRestaurantId()` against the table's restaurant — that check was redundant with (and now in conflict with) the tenant-scoped `findById` lookup that already 404s a session belonging to another restaurant.
- Frontend: `TenantLanding` (the `/t/:slug` QR/branding landing page) now links to `/login?restaurant={slug}` / `/register?restaurant={slug}`; `Login`/`Register` read that query param and send it as `restaurantSlug`; the two pages preserve it when linking to each other.
- `backend-types.ts` regenerated from the live OpenAPI spec to pick up the new/changed DTO fields.

## Why It Changed?
`fer3@example.com` registered on 2026-07-23, before commit `b994001` (2026-08-12) started binding `User.restaurantId` at registration. That commit's assumption — a customer belongs to exactly one restaurant — never matched the product's actual design (confirmed with the user): a customer scans a QR/table code and can order at any participating restaurant, not just the one they first registered at. `AuthService.login`'s unconditional `user.getRestaurantId().getId()` crashed with an NPE for the 6 accounts that predate that commit, surfaced by `GlobalExceptionHandler` as a generic 500.

Simply making `restaurantId` nullable and skipping it wasn't sufficient: `SecurityConfig`'s `jwtAuthFilter` resolves the tenant for *every* authenticated request from the JWT's `rid` claim, and `SessionService`'s join/confirm flows depend on that tenant context — so a customer JWT needs a `rid` scoped to whichever restaurant they're currently visiting. The fix threads that restaurant through explicitly: the `/t/:slug` landing page already knows it, so it's carried through login/register as `restaurantSlug` and resolved fresh each time, rather than being permanently stored on the user.
