# Report 506

## 1. Identification
- **Report number:** 506
- **Task ID:** ad-hoc live bug — guest QR/code join redirects to /login
- **Predecessor task:** report 505 (full `openapi-typescript` regen of `backend-types.ts`)

## 2. Objective
A diner who scans the table QR with the phone camera and taps "Entrar como invitado" is added to
the table (admin view and the participants list show them) but lands on `/login` instead of the
menu. Joining from inside a logged-in account worked.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/session/dto/GuestJoinResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java`
- `backend/src/test/java/com/vanter/ember/session/controller/GuestJoinFlowIntegrationTest.java`
- `frontend/src/lib/api.ts`
- `frontend/src/pages/customer/MenuJoin.tsx`
- `frontend/src/pages/customer/JoinByCode.tsx`
- `frontend/src/pages/customer/MenuJoin.test.tsx`

## 4. What Changed?
- `POST /sessions/join-as-guest` now returns `GuestJoinResponse` (session, token, userId,
  restaurantId, name, role), built from the `AuthResponse` that `issueTenantScopedToken` already
  produced. `/join` and `/{id}/join` keep `JoinSessionResponse` untouched.
- `MenuJoin.finishJoin` and `JoinByCode.submit` store the whole identity in the auth store. For an
  account holder (no `role` in the response) `finishJoin` still swaps only the token, so their
  stored identity is not overwritten with `undefined`.
- `api.ts`: `joinAsGuest` is typed `joinSessionResponse & LoginResponse`. `backend-types.ts` was not
  regenerated (needs a live backend); the intersection type covers the new fields until the next regen.
- Tests: the guest integration test asserts `userId`/`restaurantId`/`name`/`role`; the MenuJoin
  guest test asserts the auth store ends with `role: CUSTOMER` and the guest `userId`.

## 5. Why It Changed?
Root cause: the guest flow called `setAuth({ token })` only. The auth store never got `role`, so
`ProtectedRoute allowedRoles=['CUSTOMER']` (`App.tsx`) saw no role and redirected to `/login`. A
logged-in user kept the `role` from their earlier login, which is why that path worked. `userId`
is also needed by `FloatingNav` (`amiIn`), `Bill` and `ItemsFloatingIsland` to find the user's own
participant entry.

Verification: backend `./mvnw test` 1314/1314; frontend `pnpm run test:run` 164/164, `pnpm run build`
clean, `pnpm run lint` 0 errors (16 pre-existing warnings).
