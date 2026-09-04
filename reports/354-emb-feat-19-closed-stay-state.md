# Report 354 — EMB-FEAT-19: TableInformation closed stay-state + remove auto-redirect

## Identification
- **Report number:** 354
- **Current Task ID:** EMB-FEAT-19 (Plan C — `docs/superpowers/plans/2026-09-03-waiter-table-detail-actions.md`, Task 8)
- **Predecessor Task:** EMB-FEAT-18 (report 353 — `GET /identity/waiters` + `POST /sessions/{id}/transfer`)

## Objective
On the waiter table-detail view, stop bouncing the waiter to the table list the instant a
session goes `CLOSED`. Keep them on the page with a "mesa pagada y cerrada" stay-state banner
and every mutating action disabled (so they can still print the bill — Task 9/10), while still
blocking re-entry: landing on an already-`CLOSED` session on a fresh mount redirects to
`/waiter/tables`.

## Modified Files
- `frontend/src/pages/waiter/TableInformation.tsx`
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`
- `frontend/src/pages/waiter/TableInformation.closedstate.test.tsx` (new)

## What Changed?
- **`locales/{es,en}/waiter.ts`** — one new parity key `tablePaidClosedBanner`
  (ES: `'Mesa pagada y cerrada. Puedes imprimir la cuenta antes de salir.'`,
  EN: `'Table paid and closed. You can print the bill before leaving.'`).
- **`TableInformation.tsx`**
  - `import { useEffect, useState }` (was `useEffect` only).
  - New `wasOpen` state, set via a React-sanctioned render-phase conditional
    (`if (sessionData?.status === 'OPEN' && !wasOpen) setWasOpen(true)`) — tracks whether the
    session was ever seen `OPEN` while this component was mounted.
  - **Replaced** the old redirect effect (which unconditionally fired
    `toast.success(t('tableClosedPaidToast'))` + `navigate('/waiter/tables')` on any `CLOSED`):
    it now navigates **only** when `sessionData?.status === 'CLOSED' && !wasOpen` (fresh mount
    on an already-closed session), with `{ replace: true }`, and no toast.
  - New derived flags: `isClosedStayState = status === 'CLOSED' && wasOpen`,
    `actionsDisabled = status !== 'OPEN'`.
  - Amber banner (`bg-amber-50 border-amber-200 text-amber-800`) rendered above the header
    actions when `isClosedStayState`.
  - `disabled={actionsDisabled}` applied to: "Transferir" and "Agregar platillo" header
    buttons, per-item trash button (OR'd with the existing `isSentToKitchen`), redistribute-split
    button, confirm-digital-payment button, mark-cash-paid button, "Cobrar y cerrar mesa"
    (settle) button, "Cobrar mesa" (charge) button (OR'd with `!hasBillableItems`), and the
    empty-session "Cerrar mesa" button. "Imprimir cuenta" is intentionally left untouched
    (wired in Task 9).
- **`TableInformation.closedstate.test.tsx`** (new) — 2 tests, wrapper adapted from
  `AddItemModal.test.tsx` (`QueryClientProvider` + `MemoryRouter`/`Routes` for the `:id` param):
  mocks `@/lib/api` (`SessionTableService.sessionInformation`, `billingService.getBillState`),
  stubs `react-router-dom`'s `useNavigate` to a spy, stubs `@/components/tours/SectionTour` to a
  no-op. Test 1: `CLOSED` on mount → `navigate('/waiter/tables', { replace: true })`, no banner.
  Test 2: `OPEN` first render then `qc.setQueryData(['sessionDetails','s1'], …CLOSED)` → banner
  visible, `navigate` never called, "Agregar platillo" + "Transferir" disabled.

## Why It Changed?
The old effect made a paid-and-closed table unusable: the waiter was redirected away before
they could print the customer's bill or review the final split. Keeping them on a read-only
stay-state view (banner + disabled actions) preserves that last step while the `!wasOpen` guard
still prevents anyone from deep-linking back into a finished session.

## Plan Deviations
- The plan specified `const wasOpenRef = useRef(false)` read during render. This repo's
  `react-hooks` lint rules reject both `ref.current` access during render
  (`react-hooks/refs`) and `setState` inside an effect (`react-hooks/set-state-in-effect`).
  Used the officially-supported render-phase conditional `setState` pattern instead
  (React "You Might Not Need an Effect" — storing info from previous renders); behavior is
  identical.
- Plan left the choice of "drop the toast vs. fire one success toast on the transition" open —
  chose to drop it entirely (the banner already communicates the state; a toast on every
  refetch tick would be noisy).
- Test wrapper copied from `AddItemModal.test.tsx` rather than `WaiterTour.test.tsx` (closer
  fit — same `@/lib/api` mock shape); assertions use real ES copy (repo does not mock i18n).

## Verification
- `pnpm run test:run TableInformation` → **2/2 PASS** (RED confirmed first: both failed before
  the implementation — no `{ replace: true }`, no banner)
- `pnpm run test:run` (full) → **61/61 PASS** (19 files; was 59/59 + 1 new file / 2 new tests)
- `pnpm run build` → **PASS**, 0 TypeScript errors
- `pnpm run lint` → **0 errors** (16 pre-existing warnings, none in the touched files)
