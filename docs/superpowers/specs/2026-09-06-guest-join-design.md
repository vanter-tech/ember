# Guest table-join — Design

**Status:** approved decisions captured, ready for implementation plan
**Audience:** Cloud build only (`app.ember.vanter.net`). The Ember Hub build is a separate effort and strips the whole customer flow.
**Date:** 2026-09-06

## 1. Problem

Joining a table today requires a real account: `POST /sessions/join` and `POST /sessions/{id}/join`
both look the caller up by email (`userRepository.findByEmail(...).orElseThrow`) and are
`@PreAuthorize("hasRole('CUSTOMER')")`. A person who scans the table QR or gets the 5‑digit code
must first `register` or `login`. Most walk‑in diners will not create an account to order lunch,
so the QR/code flow (reports 392–393) has a hard wall in front of it.

## 2. Goal

Let a diner join a table with **one tap, no account**:

- They get a **guest identity** — a real `User` row flagged `guest = true`, with a randomly
  generated display name they can edit.
- From then on everything works exactly as a normal customer: collaborative cart, add items,
  confirm to kitchen, view bill, pay their share, "pago por consumo" / "pagos iguales",
  leave‑table redistribution.
- **No external record is written for guests:** no loyalty account, no points, no visits.

Non‑goals: guest login by email/password later (guests are resumed only via the stored token),
guest data export, merging a guest into a real account.

## 3. Approved decisions

| Decision | Choice |
| --- | --- |
| Identity acquisition | **Folded into the join** — `POST /sessions/join-as-guest`, gated on a valid open table. No standalone `/auth/guest`. |
| Guest name | **Random, editable** — the join form has an optional name field. If the diner types one, that is their display name; if they leave it blank, the server assigns a random one. |
| Old guest `User` rows | **Leave them** in v1 (cheap rows). A periodic purge is a noted follow‑up. |
| Resume after closing the browser mid‑meal | Kept — the guest token persists in `localStorage` like any customer token, so they come back to their cart. |
| Where the "Entrar como invitado" button appears | Only where there is table context: the `JoinTableModal` code screen and the `/menu/join` page. Never on bare `/login`. |

## 4. Architecture

### 4.1 New endpoint — `POST /sessions/join-as-guest` (public)

Request (exactly one of `joinCode` / `qrToken`):

```java
public record JoinAsGuestRequest(
        String joinCode,          // 5-digit table code, OR
        String qrToken,           // JWT from the table QR
        @Size(max = 50) String name   // optional; server generates one if blank
) {}
```

Response: the existing `JoinSessionResponse { Session session, String token }` — identical shape
to `POST /sessions/join`, so the frontend handles it with the same code.

Controller flow (`SessionController`):

1. Reject if both or neither of `joinCode` / `qrToken` are present → 400.
2. `GuestUserService.createGuest(name)` → persists a `User`:
   - `role = CUSTOMER`, `guest = true`, `active = true`, `restaurantId = null`
   - `name` = the provided name if non‑blank, else `GuestNameGenerator.next()`
   - `email` = `"guest+" + userId + "@guests.ember.local"` (synthetic, unique, never used to log in)
   - `passwordHash` = `passwordEncoder.encode(UUID.randomUUID().toString())` (satisfies the
     `NOT NULL` column; unknowable, so `POST /auth/login` can never succeed for a guest)
3. Delegate to the **existing** join internals with the guest's email + name:
   - `joinCode` present → `sessionService.joinSessionCode(joinCode, guest.getEmail())`
   - `qrToken` present → `sessionService.joinSession(qrToken, guest.getEmail(), guest.getName())`
4. Return `withRescopedToken(session, <guest principal>)` — same tenant‑scoped token swap the
   code/QR joins already do.

Security: add `.requestMatchers(HttpMethod.POST, "/sessions/join-as-guest").permitAll()` to
`SecurityConfig` (it's the entry point, called with no token). Rate‑limit it — reuse the
existing auth rate‑limiter path list / bucket so a loop can't mass‑create `User` rows; the
"must reference a real open table" gate is the primary limiter.

If `joinSessionCode` / `joinSession` throws (bad code, expired QR, table full, already seated)
the just‑created guest `User` is an orphan. The whole controller method is `@Transactional`, so
a thrown exception rolls the guest row back too.

### 4.2 Data model — `users.guest`

`V9__user_guest_flag.sql`:

```sql
ALTER TABLE users ADD COLUMN guest boolean NOT NULL DEFAULT false;
```

`User` entity: `@Column(nullable = false) @Builder.Default private Boolean guest = false;`

Prod Flyway runs migrations (it is **not** baselined — see PROGRESS.md's deploy‑incident note),
so `V9` applies on deploy. The local dev DB's baseline‑at‑15 means it is skipped there → add the
column by hand for local dev (same as `V7`/`V8`).

### 4.3 Suppressing loyalty / visits for guests

- **`ParticipantJoined`** event gains `boolean guest`. `SessionService.joinSession` /
  `joinSessionCode` set it from `user.getGuest()`.
- **`LoyaltyAccountJoinListener`** — `if (event.guest()) return;` before `findOrCreate`. So a
  guest never gets a `LoyaltyAccount`.
- **`LoyaltyAccrualListener`** (fires on `PaymentCompleted`, iterates `session.getParticipants()`
  and resolves each `participant.getUserId()`): inject `UserRepository`, and skip a participant
  whose `User.guest` is true. One `findById` per participant at bill settlement — negligible.
- `GET /loyalty/accounts/me` and `/loyalty/accounts/me/visits` already 404 when the caller has no
  account; since guests never get one, the customer Home / Bill loyalty cards stay hidden with
  no change.

### 4.4 Random name generator

`GuestNameGenerator` — an adjective + animal from small curated Spanish word lists
(e.g. "Puma Veloz", "Zorro Sereno", "Búho Curioso"). ~30×30 combos is plenty; collisions are
harmless (the participant key is the name string but two "Puma Veloz" at one table is a
pre‑existing edge case for the code flow too — out of scope here).

### 4.5 Frontend

- **`SessionTableService.joinAsGuest(payload)`** in `lib/api.ts` → `POST /sessions/join-as-guest`.
- **`JoinTableModal`** code screen: below the "Confirmar" button, a secondary "Entrar como
  invitado" link/button. It reveals an **optional** name field (placeholder "Tu nombre
  (opcional)", empty by default); on submit → `joinAsGuest({ joinCode, name: name || undefined })`
  → `setAuth({ token })` + `setSession(session)` → `navigate('/customer/menu')`. Same success
  path as the existing code mutation. The diner's actual display name (theirs or the
  server‑generated one) is on `session.participants` in the response.
- **`MenuJoin.tsx`** (`/menu/join`): when the visitor is **not** authenticated, instead of
  `<Navigate to="/login">`, render two choices — "Iniciar sesión" (parks the token, goes to
  `/login` as today) and "Entrar como invitado" (optional name field → `joinAsGuest({ qrToken,
  name })` → straight to `/customer/menu`). An already‑authenticated visitor keeps the current
  name‑prompt‑then‑join flow.
- Changing the display name **after** joining is out of scope (no participant‑rename endpoint or
  UI exists today) — the name is set once, at join.
- i18n: `qrJoinGuestCta`, `qrJoinGuestNameLabel`, `qrJoinGuestNamePlaceholder`,
  `joinModalGuestCta` (es/en, `customer` namespace).

## 5. Error handling

| Case | Behaviour |
| --- | --- |
| Both / neither of `joinCode`, `qrToken` | 400, no `User` created |
| Bad code / expired QR / table full / already seated | The underlying join throws its existing status; the `@Transactional` method rolls the guest `User` back; frontend shows the existing toast (`joinCodeInvalidToast` / `qrJoinExpiredToast` / `joinBlockedOtherTableToast`) |
| Rate limit hit | 429, existing rate‑limit response |
| Name > 50 chars | 400 validation |

## 6. Testing

**Backend**
- `SessionControllerTest` / a new `GuestJoinFlowIntegrationTest` (`@SpringBootTest`): guest join
  by code creates a `guest` `User`, adds a named participant, returns a tenant‑scoped token;
  guest join by QR likewise; bad code → 400/404 and **no** `User` row persisted; both/neither
  identifier → 400.
- `GuestUserServiceTest`: synthetic email uniqueness, `guest = true`, unusable password.
- `LoyaltyAccountJoinListenerTest`: `guest = true` event → `findOrCreate` never called.
- `LoyaltyAccrualListenerTest`: a guest participant on a settled bill → not credited; a real
  participant on the same bill → still credited.
- `GuestNameGeneratorTest`: returns "Word Word", non‑blank, within length.
- Full suite green (`cd backend && ./mvnw test`).

**Frontend**
- `MenuJoin.test.tsx`: unauthenticated → both "Iniciar sesión" and "Entrar como invitado" render;
  clicking guest + submitting calls `joinAsGuest` with the qr token and navigates to the menu.
- `JoinTableModal` test (if one exists / lightweight new one): guest CTA calls `joinAsGuest` with
  the code.
- `pnpm run build` + `lint` + `test:run` green.

## 7. Global constraints

- Backend build/test: `cd backend && ./mvnw test`. Frontend: `cd frontend && pnpm run build` /
  `lint` / `test:run`. Never `mvn` or bare `tsc`.
- `@DataJpaTest` (if any is added) must `@Import(com.vanter.ember.config.TenantIdentifierResolver.class)`.
- One squashed atomic commit; report in `ember/reports/NN-...md`; PROGRESS.md updated. No
  `Co-Authored-By` / AI‑signature / "Generated with Claude Code" lines anywhere (commit or PR).
- Prod deploy of the `V9` column is automatic on the next tagged backend release; the frontend
  auto‑deploys from `main` via Cloudflare Pages.

## 8. Follow-ups (not in this effort)

- Periodic purge of `guest` `User` rows with no active session older than N days.
- Letting a guest upgrade to a real account (carry the participant/session over).
- Editing the display name after joining (needs a participant‑rename endpoint + UI).
