# Report 345 — EMB-FEAT-10: `SetPinPrompt` post-login nudge + layout header entry

## 1. Identification
- **Report number:** 345
- **Task ID:** EMB-FEAT-10
- **Predecessor:** report 344 (EMB-FEAT-09 — `QuickLoginModal` real PIN modal)

## 2. Objective
Give a user who just logged in with their password a one-step "create a PIN" prompt, and a
permanent way to (re)configure that PIN from the Waiter/Admin shell header.

## 3. Modified Files
- `frontend/src/pages/auth/SetPinPrompt.tsx` (new)
- `frontend/src/pages/auth/SetPinPrompt.test.tsx` (new)
- `frontend/src/pages/auth/QuickLoginModal.tsx`
- `frontend/src/layouts/WaiterLayout.tsx`
- `frontend/src/layouts/AdminLayout.tsx`
- `frontend/src/locales/es/auth.ts`
- `frontend/src/locales/en/auth.ts`

## 4. What Changed?
### `SetPinPrompt.tsx` (new)
`Dialog`-based form, props `{ email: string; defaultPassword?: string; onDone: () => void }`.
Three controlled fields, each with a matching `aria-label`: current password (`type=password`,
prefilled from `defaultPassword`, `autoFocus`), new PIN and confirm PIN (both `type=text`
`inputMode=numeric` `maxLength=6`, non-digits stripped on change). `submit()` validates
`^\d{4,6}$` **and** `pin === confirmPin` client-side — on failure sets an inline `setPinMismatch`
error and does **not** call the API; on success `await authService.setPin({ currentPassword, pin })`
→ `toast.success(tAuth('setPinSavedToast'))` → `onDone()`. A rejected `setPin` shows the shared
`unauthorizedToast` string inline. "Ahora no" text button and the dialog's `onOpenChange(false)`
both call `onDone()`. The `email` prop is rendered as a muted subtitle in the header.

### `QuickLoginModal.tsx`
After a successful **password-path** login (`mode === 'password'`) — and only if
`useQuickAccessStore.getState().pinDismissed` does **not** already contain `profile.email` — the
modal now stores the `LoginResponse` in new `pendingRes` state and returns early instead of
calling `onClose()` + `navigateForRole()`. When `pendingRes` is set the component renders
`<SetPinPrompt email={profile.email} defaultPassword={value} onDone={…} />` in place of the PIN
form; its `onDone` runs `dismissPinPrompt(profile.email)` → `onClose()` → `navigateForRole(pendingRes, …)`.
`setAuth`/`remember`/`loginSuccessToast` still fire before the branch, so the user is fully
authenticated while the nudge is on screen. PIN-path success is unchanged (straight to navigate).

### `WaiterLayout.tsx` / `AdminLayout.tsx`
Each shell now reads `authStore.role` and looks up the most-recently-used `quickAccessStore`
profile whose `role` matches to recover the current user's email (`authStore` persists `name`,
not `email`). When such an email exists, a small right-aligned text button
(`tAuth('setPinMenuItem')`) is rendered just under `<TopNav />`; clicking it opens
`<SetPinPrompt email={pinEmail} onDone={close} />` with no `defaultPassword`. When no matching
profile is cached the button and dialog are not rendered. In `AdminLayout` the new hooks are
placed before the existing `isLoading` / `needsOnboarding` early returns to satisfy the Rules of
Hooks.

### i18n
9 new keys added to `locales/{es,en}/auth.ts` in parity (ES file is `satisfies typeof enAuth`):
`setPinCtaTitle`, `setPinCurrentPassword`, `setPinNewPin`, `setPinConfirm`, `setPinMismatch`,
`setPinSavedToast`, `setPinNotNow`, `setPinSave`, `setPinMenuItem`. No apostrophes in the EN
values, so no double-quote escaping needed.

### `SetPinPrompt.test.tsx` (new)
Mocks `@/lib/api` `authService: { setPin: vi.fn() }`. Two tests: mismatched PINs show
`setPinMismatch` and never call the API; matching PINs call
`setPin({ currentPassword: 'pw', pin: '1234' })` and then `onDone`. Assertions use the real
Spanish copy (`Contraseña actual` / `Nuevo PIN (4-6 dígitos)` / `Confirmar PIN` / `Guardar PIN` /
`Los PIN no coinciden`) because this repo's `useTranslation` returns ES strings in tests — same
plan deviation noted in reports 343/344; the plan's snippet asserted raw i18n keys.

## 5. Why It Changed?
EMB-FEAT-03/04/05 built PIN login end-to-end but nothing ever asks a user to create a PIN. On a
shared front-of-house device the fastest moment to capture one is right after a password login,
while the password is still typed (`defaultPassword` avoids re-entry). `pinDismissed` (EMB-FEAT-06)
makes the nudge a once-per-account event with no server round-trip. The header entry covers the
user who dismissed it or wants to rotate the PIN later, and lives in the shell rather than a
settings page to stay one tap away for waiters.

## 6. Verification
- `pnpm run test:run SetPinPrompt QuickLoginModal` → 2 files, **5/5 passed**.
- `pnpm run test:run` (full) → 17 files, **57/57 passed**.
- `pnpm run build` (`tsc -b && vite build`) → **PASS**, 0 TS errors.
- `pnpm run lint` → **0 errors**, 17 pre-existing warnings (none in touched files).
