# Report 397 — guest table-join (no account required)

## 1. Identification
- **Report:** 397
- **Task ID:** Guest table-join (Cloud)
- **Predecessor:** report 396 — fix(customer): horizontal-scroll on the menu view
- **Spec / Plan:** `docs/superpowers/specs/2026-09-06-guest-join-design.md` /
  `docs/superpowers/plans/2026-09-06-guest-join.md`

## 2. Objective
Let a walk-in diner join a table with one tap and no account. The guest behaves like a normal
customer for the session (collaborative cart, order, pay, split methods, leave-table
redistribution) but earns no loyalty points and records no visits.

## 3. Modified Files
**New — backend**
- `backend/src/main/java/com/vanter/ember/identity/service/GuestNameGenerator.java`
- `backend/src/main/java/com/vanter/ember/identity/service/GuestUserService.java`
- `backend/src/main/java/com/vanter/ember/session/dto/JoinAsGuestRequest.java`
- `backend/src/main/resources/db/migration/V9__user_guest_flag.sql`
- Tests: `GuestNameGeneratorTest`, `GuestUserServiceTest`, `GuestJoinFlowIntegrationTest`

**Modified — backend**
- `identity/model/User.java` — `guest` field
- `session/event/ParticipantJoined.java` — `guest` component (+ a 4-arg convenience ctor kept)
- `session/service/SessionService.java` — both `ParticipantJoined` publish sites pass `user.getGuest()`
- `loyalty/listener/LoyaltyAccountJoinListener.java` — return early for a guest join
- `loyalty/listener/LoyaltyAccrualListener.java` — inject `UserRepository`; skip a participant with a null `userId` or `guest == true`
- `session/controller/SessionController.java` — `POST /sessions/join-as-guest` + `withRescopedToken(Session, String)` overload
- `config/SecurityConfig.java` — `permitAll` `POST /sessions/join-as-guest`
- `config/RateLimitProperties.java` — `/sessions/join-as-guest` in the default guarded paths
- Tests: `SessionControllerTest` (`@MockBean GuestUserService`), `LoyaltyAccountJoinListenerTest`, `LoyaltyAccrualListenerTest`

**Modified — frontend**
- `lib/api.ts` — `SessionTableService.joinAsGuest`
- `pages/customer/MenuJoin.tsx` — unauthenticated → "Iniciar sesión" vs "Entrar como invitado"
- `locales/es/customer.ts`, `locales/en/customer.ts` — `qrJoin*` guest strings
- `pages/customer/MenuJoin.test.tsx`

## 4. What Changed?
- **`POST /sessions/join-as-guest`** (public, rate-limited, `@Transactional`). Body
  `{ joinCode?, qrToken?, name? }`; an `@AssertTrue` on the record makes "neither / both
  identifier" a 400 (not the 409 an `IllegalArgumentException` gives in this codebase). It calls
  `GuestUserService.createGuest(name)` — a `users` row with `role = CUSTOMER`, `guest = true`,
  `active = true`, `restaurantId = null`, synthetic unique email
  `guest+<uuid>@guests.ember.local`, and a `passwordHash` of a random UUID (so `POST /auth/login`
  can never authenticate a guest) — then delegates to the **existing** `joinSessionCode` /
  `joinSession` and returns the same `JoinSessionResponse { session, token }`. Because the method
  is transactional, a failed join rolls the guest row back.
- **`V9__user_guest_flag.sql`**: `ALTER TABLE users ADD COLUMN guest boolean NOT NULL DEFAULT
  false;`. Prod Flyway runs it on the next tagged release; the local dev DB (baselined at v15)
  had the column added by hand.
- **Loyalty suppression:** `ParticipantJoined` carries `guest`; `LoyaltyAccountJoinListener`
  returns early for a guest, so no `LoyaltyAccount` is ever created. `LoyaltyAccrualListener`
  skips any participant whose `userId` is null (also covers the Hub's future name-only seats) or
  whose `User.guest` is true. `GET /loyalty/accounts/me` and `/me/visits` already 404 with no
  account, so the customer Home / Bill loyalty cards stay hidden unchanged.
- **`GuestNameGenerator`** — "Animal Adjetivo" (e.g. `Puma Veloz`) from small ES word lists.
- **Frontend:** an unauthenticated visitor at `/menu/join?token=…` now chooses "Iniciar sesión"
  (parks the token, `/login`, as before) or "Entrar como invitado" (optional name →
  `joinAsGuest({ qrToken, name })` → `/customer/menu`). The authenticated path is unchanged.
- **Not done:** the plan's "Entrar como invitado" on `JoinTableModal`'s code screen was
  dropped — that modal opens only from behind `ProtectedRoute[CUSTOMER]`, so a guest can't reach
  it and a logged-in customer has no use for a second guest identity. A public
  code-entry-as-guest page is a noted follow-up.

Verification: `cd backend && ./mvnw test` → **1167/1167**. `cd frontend && pnpm run build` +
`lint` clean (16 pre-existing warnings), `pnpm run test:run` → **104/104**.

## 5. Why It Changed?
Joining a table required a real account (`joinSessionCode` / `joinSession` look the caller up by
email and are `@PreAuthorize("hasRole('CUSTOMER')")`), so the QR/code flow had a registration
wall in front of it and most walk-in diners will not create an account to order lunch. The guest
identity removes that wall while keeping every downstream path (cart, billing, split methods)
untouched — a guest is just a `CUSTOMER` `User` with `guest = true`. Folding the mint into the
join call (rather than a standalone `/auth/guest`) means a guest identity can only be created in
the context of a real open table, which removes the anonymous-account-creation abuse surface;
the rate-limiter is the second layer.

## 6. Follow-ups (not in this effort)
- Public code-entry-as-guest page (5-digit code without an account).
- Periodic purge of `guest` `User` rows with no active session older than N days.
- Letting a guest upgrade to a real account, carrying the session over.
- Editing the display name after joining (needs a participant-rename endpoint + UI).
