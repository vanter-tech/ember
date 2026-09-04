# Report 342 — EMB-FEAT-07: extract `navigateForRole` from `Login.tsx`

## Identification
- **Report number:** 342
- **Task ID:** EMB-FEAT-07
- **Predecessor Task:** EMB-FEAT-06 (report 341 — device-local quick-access profile store)

## Objective
Pull the post-login role routing out of `Login.tsx`'s `onSubmit` into a standalone
`navigateForRole()` helper so EMB-FEAT-09's `QuickLoginModal` can reuse the exact same
routing (including the CUSTOMER resume-session branch) after a PIN login. No behavior change.

## Modified Files
- `frontend/src/pages/auth/navigateForRole.ts` (new)
- `frontend/src/pages/auth/navigateForRole.test.tsx` (new)
- `frontend/src/pages/auth/Login.tsx`

## What Changed?
- **`navigateForRole.ts`** — `async navigateForRole(response, navigate, { tAuth })`.
  Holds `Login.tsx`'s former `switch (response.role)` body verbatim:
  - `ADMIN` → `/admin`, `WAITER` → `/waiter`, `KITCHEN` → `/kitchen` (all `{ replace: true }`).
  - `CUSTOMER` → if `useSessionStore.getState().id` is set, `SessionTableService.resumeSession`,
    swap in the re-scoped token via `useAuthStore.getState().setAuth`, rehydrate the session,
    `sessionResumedToast`, go to `/customer/menu`; on failure `clearSession()` and fall through
    to `/customer/home`.
  - `TAuth` is typed as `ReturnType<typeof useTranslation<'auth'>>['t']` (the plan's loose
    `(key: string) => string` did not typecheck against the narrow-keyed `t` returned by
    `useTranslation('auth')`).
- **`navigateForRole.test.tsx`** — 4 vitest cases: ADMIN/WAITER/KITCHEN redirect targets and
  CUSTOMER-with-no-open-session → `/customer/home` (`beforeEach` resets `useSessionStore.id`).
- **`Login.tsx`** — the `switch` block in `onSubmit` is replaced by
  `await navigateForRole(response, navigate, { tAuth })`; `setAuth(response)` and the
  `loginSuccessToast` that precede it are unchanged. Now-unused imports `useSessionStore` and
  `SessionTableService` removed; `navigateForRole` imported.

## Why It Changed?
EMB-FEAT-09 needs identical routing after a PIN login as after a password login — same role
switch, same CUSTOMER resume-session logic and toasts. Duplicating ~30 lines of routing into
`QuickLoginModal` would drift; a single exported helper keeps one source of truth. Extraction
is behavior-preserving: the code moved unchanged, `Login.tsx` still calls it on the same path.

## Verification
- `pnpm run test:run navigateForRole` — 4/4 PASS.
- `pnpm run build` — PASS (0 TS errors, 2848 modules). First run failed with `TS2322` on the
  `tAuth` param type; fixed by deriving `TAuth` from `useTranslation`.
- `pnpm run lint` — 0 errors (17 pre-existing warnings, none in the new files).
