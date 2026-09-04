# Report 344 — EMB-FEAT-09: QuickLoginModal (PIN entry + password fallback)

## 1. Identification
- **Report number:** 344
- **Current Task ID:** EMB-FEAT-09
- **Predecessor Task:** EMB-FEAT-08 (report 343 — quick-access chips on `/login`)

## 2. Objective
Replace the `null` `QuickLoginModal.tsx` placeholder with a working PIN-entry modal: a
tapped quick-access chip opens it, the user confirms with their 4–6 digit PIN, and the
modal falls back to password entry on `PIN_NOT_SET` (409), `PIN_LOCKED` (423), or an
explicit "prefer password" choice. On success it authenticates, refreshes the device
chip, and routes by role via the existing `navigateForRole`.

## 3. Modified Files
- `frontend/src/pages/auth/QuickLoginModal.tsx` (placeholder → real implementation)
- `frontend/src/pages/auth/QuickLoginModal.test.tsx` (new, +3 tests)
- `frontend/src/locales/es/auth.ts` (+7 keys)
- `frontend/src/locales/en/auth.ts` (+7 keys)

## 4. What Changed?
- **`QuickLoginModal.tsx`** — now a `Dialog`-based modal (same shared `@/components/ui/dialog`
  primitive the admin/waiter modals use). Props unchanged: `{ profile: QuickAccessProfile;
  onClose: () => void }`. Local state: `mode: 'pin' | 'password'`, `value`, `hint`, `error`,
  `busy`. Header shows the profile avatar disc (`hsl(colorSeed 55% 45%)` + initials), name,
  and email. A single controlled `<Input>` is either the PIN field (`type=text`,
  `inputMode=numeric`, `maxLength=6`, non-digits stripped on change) or the password field
  (`type=password`), keyed off `mode`; its `<label htmlFor>` and `aria-label` both track the
  active mode's label. `submit()`:
  - `mode === 'pin'` → `authService.loginPin({ email, pin })`, else
    `authService.login({ email, password })`.
  - On resolve: `useAuthStore.setAuth(res)` → `useQuickAccessStore.remember({ email,
    name: res.name ?? profile.name, role: res.role ?? profile.role })` → success toast →
    `onClose()` → `await navigateForRole(res, navigate, { tAuth })`.
  - On reject: reads `status`/`code` via `axios.isAxiosError`. In PIN mode, `PIN_NOT_SET`
    or 409 → switch to password mode + `quickLoginPinNotSetHint`; `PIN_LOCKED` or 423 →
    password mode + `quickLoginPinLockedHint`; any other error → inline
    `quickLoginPinIncorrect`. In password mode, any error → inline `unauthorizedToast`.
  - Submit button is disabled while `busy` or `value.length < 4`.
  - A "prefer password" text button (PIN mode only) switches to password mode and clears
    field/hint/error.
- **`QuickLoginModal.test.tsx`** (new) — mocks `@/lib/api` with `authService: { login, loginPin }`,
  renders inside `MemoryRouter`. 3 tests: (1) typing a PIN + submit calls
  `loginPin({ email, pin })`; (2) a 409/`PIN_NOT_SET` rejection reveals the not-set hint and
  the password field; (3) the "prefer password" link swaps the input to the password field.
  **Plan deviation:** the plan's test snippet asserted raw i18n keys
  (`getByLabelText('quickLoginPinLabel')` etc.); this repo's `useTranslation` returns real
  copy in tests (same finding as report 343), so assertions use the Spanish strings
  (`'PIN'`, `'Entrar'`, `'Prefiero mi contraseña'`, `'No tienes un PIN configurado. Ingresa
  tu contraseña.'`, `'Ingresa tu contraseña'`).
- **`locales/{es,en}/auth.ts`** — 7 new keys in `{es,en}` parity (guarded by
  `satisfies typeof enAuth`): `quickLoginPinLabel`, `quickLoginPinPlaceholder`,
  `quickLoginPinIncorrect`, `quickLoginPreferPassword`, `quickLoginPinNotSetHint`,
  `quickLoginPinLockedHint`, `quickLoginSubmit`. EN's `quickLoginPinNotSetHint` is
  double-quoted (contains an apostrophe).

## 5. Why It Changed?
EMB-FEAT-08 shipped the chip grid on `/login` and a `null` `QuickLoginModal` so the wiring
(`activeChip` → `<QuickLoginModal>`) could land without the PIN UI. This task fills that
placeholder. The single-input, mode-switching design keeps the modal small on a shared
floor tablet while still guaranteeing a way in when a PIN is unset or locked — the password
path is always reachable. `remember()` is re-called on success so a PIN login also bumps
the chip's `lastUsedAt` / refreshes name/role. Routing is delegated to `navigateForRole`
(EMB-FEAT-07) so PIN login and password login share one post-auth path, including the
CUSTOMER resume-session branch.

## 6. Verification
- `pnpm run test:run QuickLoginModal` → 3/3 PASS
- `pnpm run test:run` (full) → 55/55 PASS (was 52/52; +3)
- `pnpm run build` → PASS (0 TS errors)
- `pnpm run lint` → 0 errors (17 pre-existing warnings, none in touched files)
