# Waiter Quick-Login (device-cached profile chips + PIN) — Design

- **Date:** 2026-09-03
- **Branch:** `feat/waiter-quick-login-table-actions` off `main`
- **Status:** design approved in chat 2026-09-03, pending spec review → `writing-plans`
- **Related:** `[[2026-09-03-waiter-table-detail-actions-design]]` (sibling workstream, same branch)

## 1. Objective

Speed up repeated logins on shared floor devices (tablets at `app.ember.vanter.net`). After
anyone logs in once on a device, their profile is remembered locally and shown as a tappable
"chip" on the login screen. Tapping a chip opens a small modal that asks only for a **PIN**
(4–6 digits); users who have not set a PIN, or who fail it, fall back to their normal password.

Non-goals: no passwordless / long-lived device tokens; no server-side "trusted device" registry;
no biometric; no cross-device sync of the chip list (it is per-browser only).

## 2. Approach (chosen)

PIN hash stored on the `User` row, a dedicated PIN-login endpoint that issues the exact same JWT
as password login, and a **device-local** (localStorage) profile cache that renders the chips.
Everything else (JWT issuance, role routing, rate-limit response handling) reuses what exists.

Rejected:
- *Long-lived per-device refresh token* — bigger surface, weaker on shared tablets; the user
  explicitly wants a credential prompt on every entry.
- *Frontend-only remembered password* — the user asked for a PIN.

## 3. Scope of where chips appear

Chips render on the shared `/login` route (`pages/auth/Login.tsx`) for **every build and every
role** that has successfully authenticated on that device. Rationale: the cache is the source of
truth ("si se encuentra en cache, mostrar inicio rápido"); gating by role or by Hub-build adds
config with no user benefit. An admin who logs in on a floor tablet gets a chip too.

## 4. Backend

### 4.1 Data

Flyway `V6__user_pin.sql`:

```sql
ALTER TABLE users ADD COLUMN pin_hash VARCHAR(60);          -- BCrypt, nullable
ALTER TABLE users ADD COLUMN pin_updated_at TIMESTAMP;      -- nullable
```

`User.java`: add `String pinHash` (`@JsonIgnore`, nullable) and `Instant pinUpdatedAt` (nullable).
`ddl-auto=validate` must still pass — columns are nullable, no default needed.

### 4.2 Endpoints (all under the existing `AuthController`, `/auth`)

| Method & path | Auth | Body | Behavior |
|---|---|---|---|
| `POST /auth/login/pin` | public | `{ email, pin }` | Look up user by email. `409 PIN_NOT_SET` if `pinHash == null`. `401` if inactive or PIN mismatch. On success return the **same** `AuthResponse` as `POST /auth/login`. |
| `POST /auth/pin` | authenticated (any role) | `{ currentPassword, pin }` | Verify `currentPassword` against `passwordHash` (`401` if wrong). Validate `pin` is 4–6 digits (`400`). `pinHash = bcrypt(pin)`, `pinUpdatedAt = now()`. `204`. |
| `DELETE /auth/pin` | authenticated (any role) | — | `pinHash = null`, `pinUpdatedAt = null`. `204`. |

Handled in a new `AuthService` method group (`loginWithPin`, `setPin`, `clearPin`). `AuthResponse`
is unchanged. PIN validation regex: `^\d{4,6}$`.

### 4.3 Brute-force guard

Low-entropy secret ⇒ needs throttling. A small in-memory counter inside the PIN-login path
(e.g. a `ConcurrentHashMap<String,Attempt>` keyed by lowercased email, `Attempt` = count +
window-start):

- 5 failed PIN attempts within 15 min ⇒ `423 PIN_LOCKED` for that email until the window rolls.
- Any successful PIN or password login for that email clears its counter.
- Documented as in-memory: resets on app restart, not shared across nodes. Acceptable — this is a
  single-node modular monolith, and the fallback (password + its own existing protections) still
  gates access. If the deployment ever goes multi-node this moves to a shared store; noted, not
  built now.

Password login (`POST /auth/login`) is **not** modified.

### 4.4 Error contract

`GlobalExceptionHandler` maps the new cases to bodies shaped `{ "code": "...", "message": "..." }`
(matches the existing `code`-carrying errors the frontend already reads, e.g. `CASH_SHIFT_OVERDUE`):
`PIN_NOT_SET` → 409, `PIN_LOCKED` → 423, bad PIN / bad `currentPassword` → 401
(`code: "INVALID_CREDENTIALS"`), malformed PIN → 400 (`code: "INVALID_PIN_FORMAT"`).

## 5. Frontend

### 5.1 `quickAccessStore.ts` (new, `src/store/`)

Zustand + `persist`, storage key `ember-quick-access`.

```ts
interface QuickAccessProfile {
  email: string
  name: string
  role: Role
  initials: string       // derived from name at save time
  colorSeed: number       // stable hash of email → avatar bg
  lastUsedAt: number      // epoch ms, for LRU ordering + eviction
}
interface QuickAccessState {
  profiles: QuickAccessProfile[]
  remember: (p: Omit<QuickAccessProfile,'lastUsedAt'>) => void  // upsert by email, set lastUsedAt=now
  forget: (email: string) => void
  clear: () => void
}
```

Rules: dedupe by `email`; cap at **6**; on overflow evict the oldest `lastUsedAt`. `remember` is
called after **every** successful login (password or PIN), from both `Login.tsx` and the modal.
The store never holds a token, a password, or the PIN.

### 5.2 `navigateForRole()` helper (extracted)

The `switch (response.role)` block currently inside `Login.tsx`'s `onSubmit` (including the
CUSTOMER resume-session branch) moves to `src/pages/auth/navigateForRole.ts` as
`navigateForRole(response, navigate, { tAuth })`. Both `Login.tsx` and `QuickLoginModal` call it.
No behavior change for the password path.

### 5.3 `Login.tsx` changes

- Read `quickAccessStore.profiles`. If non-empty, render a **"Inicio rápido"** section above the
  form: a wrap/grid of chips (avatar circle with `initials` on `colorSeed` background, `name`,
  small role badge), sorted by `lastUsedAt` desc.
- A **"Usar otra cuenta"** link/toggle collapses the chips and shows the normal email+password
  form (the form is hidden by default when chips exist; always shown when the list is empty).
- An **"Editar"** toggle puts chips into a remove mode (small `×` per chip → `forget(email)`).
- Tapping a chip opens `<QuickLoginModal profile={chip} />`.
- After a successful password submit, call `remember({ email, name, role, ... })` before navigating.

### 5.4 `QuickLoginModal.tsx` (new, `src/pages/auth/`)

- Header: the chip's avatar + `name` + `email`.
- Default input: a numeric PIN field (`inputMode="numeric"`, `maxLength=6`, masked), 4–6 digits.
- Primary button submits `authService.loginPin({ email, pin })`.
  - Success → `remember(...)` then `navigateForRole(...)`.
  - `409 PIN_NOT_SET` or `423 PIN_LOCKED` → auto-switch to password mode with an inline hint
    (`t('pinNotSetHint')` / `t('pinLockedHint')`).
  - `401` → inline "PIN incorrecto", stay in PIN mode, let the guard count.
- A **"Prefiero mi contraseña"** link switches to a password field → `authService.login({ email, password })`.
- **"Crear un PIN" nudge:** if the login that just succeeded came through the **password** field
  in this modal (or in `Login.tsx` from a chip) and the profile has no locally-known PIN, show a
  dismissible follow-up (`<SetPinPrompt />`) offering to set one now. "Ahora no" just closes.
  Because "has a PIN" is server state the client can't cheaply read, track a local
  `pinDismissed: string[]` (emails) in the store so the nudge isn't shown every time.

### 5.5 `SetPinPrompt.tsx` (new)

Fields: `currentPassword` (pre-fillable if we still hold it in memory from the just-submitted
form — never persisted), `pin`, `confirmPin`. Submits `authService.setPin`. Also reachable later
from the waiter layout header menu ("Configurar PIN de acceso rápido") and from an equivalent
admin entry point — a single shared component, mounted in `WaiterLayout` / `AdminLayout` header.

### 5.6 `api.ts` additions

```ts
authService.loginPin  = (body: { email: string; pin: string }) => POST /auth/login/pin  -> LoginResponse
authService.setPin    = (body: { currentPassword: string; pin: string }) => POST /auth/pin   -> void
authService.clearPin  = () => DELETE /auth/pin -> void
```

## 6. Data flow

```
[chip tap] → QuickLoginModal
   PIN path:   POST /auth/login/pin {email,pin}
                 ├ 200 → setAuth(jwt) → remember() → navigateForRole()
                 ├ 409/423 → swap to password field (+hint)
                 └ 401 → inline error, server-side guard++ (lock at 5/15min)
   pwd path:   POST /auth/login {email,password} → setAuth → remember → navigateForRole
                 └ then maybe <SetPinPrompt> → POST /auth/pin {currentPassword,pin}
```

## 7. Testing

**Backend**
- `AuthServiceTest`: `loginWithPin` happy path returns same claims as password login; `PIN_NOT_SET`
  when unset; `401` on wrong PIN / inactive user; `setPin` rejects non-4–6-digit; `setPin` requires
  correct current password; `clearPin` nulls both columns.
- Guard test: 5 wrong PINs in-window → `PIN_LOCKED`; a correct password login mid-window clears it.
- `AuthControllerTest` (`@WebMvcTest`): status codes + `code` bodies for each branch; `POST /auth/pin`
  / `DELETE /auth/pin` are `401` without a JWT.
- `V6` migration applies on an empty DB; `ddl-auto=validate` passes.

**Frontend**
- `quickAccessStore.test.ts`: upsert-by-email, LRU cap at 6, `forget`, `clear`, `pinDismissed`.
- `navigateForRole` unit test: each role → route, CUSTOMER resume-session branch preserved.
- `QuickLoginModal` RTL: renders profile; PIN submit calls `loginPin`; `409`/`423` swaps to
  password; "Prefiero mi contraseña" swaps; success calls `remember` + navigate.
- `pnpm run build` + `pnpm run lint` clean; `./mvnw test` green.

## 8. i18n

New keys in `locales/{es,en}/auth.ts` (parity enforced by `satisfies`): `quickStartTitle`,
`useAnotherAccount`, `editChips`, `removeChip`, `pinLabel`, `pinPlaceholder`, `pinIncorrect`,
`preferPassword`, `pinNotSetHint`, `pinLockedHint`, `createPinCta`, `createPinTitle`,
`currentPasswordLabel`, `confirmPinLabel`, `pinMismatch`, `pinSavedToast`, `notNow`.

## 9. Security notes

- PIN is BCrypt-hashed, `@JsonIgnore`, never returned by any endpoint.
- `POST /auth/login/pin` is added to `RateLimitProperties.paths` alongside `/auth/login`.
- The device chip list is a convenience cache: it reveals which staff emails have used a given
  tablet. Acceptable for a back-of-house device; the "Editar → remove" affordance and `clear()`
  let staff prune it. No token or secret is ever placed in localStorage by this feature.
- Setting/replacing a PIN always requires the current password.
</content>
