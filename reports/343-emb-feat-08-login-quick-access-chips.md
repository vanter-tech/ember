# Report 343 — EMB-FEAT-08: quick-access chips on the login screen

## Identification
- **Report number:** 343
- **Task ID:** EMB-FEAT-08
- **Predecessor Task:** EMB-FEAT-07 (report 342 — extract `navigateForRole` from `Login.tsx`)

## Objective
Render the device-local quick-access profiles (EMB-FEAT-06 store) as tappable chips on
`/login`: a chip grid replaces the email/password form when profiles exist, with an "use
another account" escape hatch and an edit mode to remove chips. Record the signed-in
profile via `remember()` after every successful password login.

## Modified Files
- `frontend/src/pages/auth/Login.tsx`
- `frontend/src/pages/auth/QuickLoginModal.tsx` (new — placeholder)
- `frontend/src/pages/auth/Login.quickaccess.test.tsx` (new)
- `frontend/src/locales/es/auth.ts`
- `frontend/src/locales/en/auth.ts`

## What Changed?
- **`Login.tsx`**
  - Imports `useQuickAccessStore` + `QuickAccessProfile` type, `QuickLoginModal`, and
    `useState`.
  - New local state: `showForm`, `editing`, `activeChip`; derived `chipsVisible =
    profiles.length > 0 && !showForm`.
  - `onSubmit` now calls `remember({ email: data.email, name: response.name ?? data.email,
    role: response.role ?? '' })` right after `setAuth(response)` (both `name`/`role` are
    optional on `AuthResponse`).
  - JSX: when `profiles.length > 0`, a chips block renders above the form — a 2-col grid of
    avatar (initials on an `hsl(colorSeed …)` disc) + name + role buttons that set
    `activeChip`; an Edit/Done toggle reveals a per-chip `×` calling `forget(p.email)`; a
    "use another account" link sets `showForm`. The block is `hidden` while `showForm`.
  - The `<form>` element gains `hidden={chipsVisible}` so the password path stays mounted
    (react-hook-form state intact) but hidden behind the chips.
  - `{activeChip && <QuickLoginModal profile={activeChip} onClose={() => setActiveChip(null)} />}`
    rendered inside `CardContent`.
- **`QuickLoginModal.tsx`** — placeholder that renders `null`, typed `{ profile:
  QuickAccessProfile; onClose: () => void }`. Full PIN-entry + password-fallback
  implementation is EMB-FEAT-09; this only satisfies the import + call site now.
- **`Login.quickaccess.test.tsx`** — 2 vitest cases (`QuickLoginModal` mocked, `MemoryRouter`
  wrapper, `beforeEach` resets the store): empty store → no "Inicio rápido" heading and the
  email field is visible; one stored profile → its name renders, the email field is
  `not.toBeVisible()`, and "Usar otra cuenta" shows.
- **`locales/{es,en}/auth.ts`** — 5 new keys in parity: `quickStartTitle`,
  `useAnotherAccount`, `editChips`, `doneEditingChips`, `removeChipAria` (`{{name}}`
  interpolation token — this repo's i18n uses double braces, not the plan's `{name}`).

## Why It Changed?
The quick-access store shipped in EMB-FEAT-06 with no UI. This task is the entry point:
a returning waiter on a shared floor device taps their chip instead of typing credentials.
The form is hidden rather than unmounted so switching back via "use another account" keeps
the react-hook-form instance and its validation state. Chip selection only opens the (stub)
modal here; the PIN confirmation flow is deliberately deferred to EMB-FEAT-09, which
replaces `QuickLoginModal.tsx` wholesale.

Deviation from the plan: the plan's test snippet assumed `useTranslation` returns raw keys
in tests. It does not in this codebase (`GlobalSearchResults.test.tsx` et al. assert real ES
strings), so the test asserts the actual Spanish copy.

## Verification
- `pnpm run test:run Login` — 2/2 PASS.
- `pnpm run test:run` (full) — 52/52 PASS, 15 files, no regression.
- `pnpm run build` — PASS (0 TS errors).
- `pnpm run lint` — 0 errors (17 pre-existing warnings, none in touched files).
