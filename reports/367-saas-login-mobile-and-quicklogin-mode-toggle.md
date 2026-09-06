# Report 367 — SaaS login: mobile spacing + Quick Login PIN/password toggle

## 1. Identification
- **Report number:** 367
- **Current Task ID:** UI polish — SaaS login on mobile + Quick Login auth-method choice
- **Predecessor Task:** report 366 (hotfix: Cloudflare Pages `env-config.js` absolute path)

## 2. Objective
Three UX fixes on the tenant login screen:
1. The login card sat edge-to-edge on narrow phones (iPhone 11) — no page gutter.
2. In Quick Start, the saved-profile chips were forced to two cramped columns on mobile.
3. The Quick Login modal opened straight into PIN entry; the user wants both auth
   methods offered up front so staff pick the one they use.

## 3. Modified Files
- `frontend/src/pages/auth/Login.tsx`
- `frontend/src/pages/auth/QuickLoginModal.tsx`
- `frontend/src/pages/auth/QuickLoginModal.test.tsx`
- `frontend/src/locales/es/auth.ts`
- `frontend/src/locales/en/auth.ts`

## 4. What Changed?
**`Login.tsx`**
- Wrapper `min-h-screen` flex container gained `p-4`, so the `max-w-md` card keeps a
  16px gutter on every screen instead of touching the viewport edges.
- Saved-profile chip grid `grid-cols-2` → `grid-cols-1 sm:grid-cols-2`: one full-width
  chip per row on mobile, unchanged two-up layout from the `sm` breakpoint (desktop).

**`QuickLoginModal.tsx`**
- `mode` state is now `'pin' | 'password' | null`, initialised to `null` — no method
  is preselected.
- Added an always-visible segmented toggle (`Contraseña` | `PIN`) above the form.
  Selecting one sets the mode, clears the field/error/hint (`pickMode` helper).
- The input, its label and the inline error render only once `mode !== null`; the
  submit button is disabled while `mode === null`.
- Removed the old bottom "Prefiero mi contraseña" text link — the toggle replaces it.
- The server-driven fallback (`PIN_NOT_SET` / `PIN_LOCKED` → switch to password + amber
  hint) is unchanged.

**i18n** — added `quickLoginPasswordLabel` (`Contraseña` / `Password`) for the toggle
pill; removed the now-unused `quickLoginPreferPassword`. The PIN pill reuses
`quickLoginPinLabel`.

**`QuickLoginModal.test.tsx`** — updated for the new flow: added a case asserting no
field shows and submit is disabled before a mode is picked; the PIN-submit and
`PIN_NOT_SET` cases now click the `PIN` toggle first; the removed-link test became a
`Contraseña` toggle test.

## 5. Why It Changed?
1/2 are pure responsive spacing — the container had no gutter and the chip grid never
had a mobile breakpoint, so both were cramped on a ~390–414px viewport.

For 3, PIN-first meant a user who never set a PIN always hit a failed attempt before
the modal fell back to password. Showing both methods with nothing preselected lets
each staff member go straight to the credential they actually have, and keeps the
automatic fallback only as a safety net.

## 6. Verification
- `pnpm run build` — clean (`tsc -b` + `vite build`), no TS errors.
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in touched files).
- `pnpm run test:run` — 24 files, 78 tests pass (was 77; +1 new Quick Login case).
