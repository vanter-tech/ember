# Report 488

## 1. Identification
- **Report number:** 488
- **Task ID:** LIVE-BUG-BATCH 1/7 — QR join forces the name-entry screen even for a real account
- **Predecessor task:** report 487 (hotfix: waiter can't see the open cash shift)

## 2. Objective
Live user bug report: scanning a table's QR code and joining as an authenticated CUSTOMER (not a
guest) opened a name-assignment screen — that screen should only ever appear for guest entry, since
a real account already has a name.

## 3. Modified Files
- `frontend/src/pages/customer/MenuJoin.tsx`
- `frontend/src/pages/customer/MenuJoin.test.tsx`

## 4. What Changed?
`MenuJoin` (the `/menu/join?token=…` landing page reached by scanning the physical table QR) used
to render a name-input form for every authenticated `CUSTOMER`, pre-filled with the account's name
but still requiring the user to look at and confirm it before joining. It now auto-joins immediately
via a `useEffect` that calls `submit(accountName)` as soon as the account is known to be an
authenticated CUSTOMER with a name — no screen shown at all beyond a brief "Entrando..." state. The
name-input form only renders for two cases now: the guest chooser (unauthenticated), and the rare
edge case of an authenticated CUSTOMER whose account has no name on file (the backend requires a
non-blank `userName`, so this one case still has to ask).

Added a `joinFailed` state so a recoverable failure (e.g. a 409 "already seated elsewhere") shows a
retry button instead of leaving the user stuck on an unexplained "Entrando..." spinner forever — a
gap that didn't exist before since the old always-a-form design already had a visible screen to fall
back onto.

Two real Rules-of-Hooks/lint issues were caught and fixed before landing:
- `react-hooks/set-state-in-effect`: calling `submit` (which sets state synchronously before its
  first `await`) from inside a `useEffect` is flagged by this codebase's lint config. Fixed with the
  same targeted `eslint-disable-next-line` pattern already used elsewhere in this codebase (e.g.
  `RefundPaymentModal.tsx`) for a genuinely-intentional "seed on mount" effect, rather than
  restructuring away from `useEffect` entirely.
- `react-hooks/rules-of-hooks`: the new `useEffect` was initially placed after the existing
  `if (!qrToken || !sessionId) return` early return, which is illegal (hooks must run unconditionally
  every render). Fixed by moving all hook calls (both `useEffect`s) above every conditional return,
  and pushing the null-safety check for `sessionId`/`qrToken` into `submit`'s own guard clause
  instead of relying on the outer early return's type narrowing.

Test changes: replaced `'authenticated: pre-fills the name field with the account name'` (behavior
that no longer exists — there's no field to pre-fill once the account has a name, since the form
never renders) with two new tests: auto-join happens with no form shown, and a recoverable failure
offers a working retry button. The remaining 6 existing tests were already scoped to the
no-account-name case (`token`/`role` only, no `name`) and needed no changes.

## 5. Why It Changed?
Matches the user's explicit expectation: a name-assignment step only makes sense for a guest who has
no account name yet. For an already-authenticated diner, forcing them to look at and confirm their
own name before every table join is unnecessary friction with no upside.

## Verification
- `cd frontend && pnpm vitest run src/pages/customer/MenuJoin.test.tsx` → 8/8 pass.
- `cd frontend && pnpm run lint` → 0 errors, 16 pre-existing warnings (unchanged baseline).
- `cd frontend && pnpm run build` → clean.
