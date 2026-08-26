# Report 212: "?" button to replay the waiter tour on demand

**Predecessor:** report 211

## Objective
Add a way for a waiter to re-watch the `Tables.tsx` tour after already dismissing/finishing it
once, without needing to clear `localStorage` — user-requested UX addition alongside two other
TopNav consistency fixes (see report 213).

## Modified Files
- `frontend/src/store/uiStore.ts`
- `frontend/src/pages/waiter/components/WaiterTour.tsx`
- `frontend/src/pages/waiter/components/WaiterTour.test.tsx`
- `frontend/src/components/TopNav.tsx`
- `frontend/src/locales/es/common.ts`
- `frontend/src/locales/en/common.ts`

## What Changed?
`useUIStore` gained `waiterTourRequested: boolean` plus `requestWaiterTour()`/
`clearWaiterTourRequest()` actions (plain in-memory state, not persisted — matches the store's
existing modal/search-term scope). `WaiterTour.tsx`'s render gate is now
`!hasSeenTour(userId) || waiterTourRequested`, and its `run` local state resets to `true` via a
`useEffect` on `waiterTourRequested` (needed because a prior finish/skip had already flipped `run`
to `false`, and passing `run={false}` to a freshly-visible `Joyride` would never start it).
`handleEvent`'s finish/skip branch now also calls `clearWaiterTourRequest()` so the flag doesn't
linger true and re-trigger the tour on an unrelated remount. `TopNav.tsx` renders a `HelpCircle`
icon button next to `LanguageSwitcher`, visible only on `isWaiterRoute` (`/waiter/tables`, the only
route that mounts `WaiterTour`), wired to `requestWaiterTour`. New `common.replayTourButtonLabel`
i18n key (ES "Ver tutorial" / EN "Show tutorial") for its `aria-label`/`title`.

## Why It Changed?
User request: once a waiter dismisses the tour it's gone forever (`waiterTourStore`'s per-user
`seenByUserId` flag has no UI to reset), with no way to re-learn the flow later. The fix reuses the
existing `WaiterTour`/`waiterTourStore` machinery rather than adding a second tour system — only the
gating condition and a manual trigger were needed.

## Verification
`cd frontend && pnpm run test:run` — 25/25 PASS (new `WaiterTour` replay test + all existing).
`cd frontend && pnpm run build` — PASS.
