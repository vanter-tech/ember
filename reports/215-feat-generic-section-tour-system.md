# Report 215: Generic reusable `SectionTour` mechanism

**Predecessor:** report 214

## Objective
User request: add replayable tutorials to more admin sections (Catálogo/Inventario, Analíticas,
Personal, Caja, Configuración per tab). Before writing per-section content, generalize
`WaiterTour.tsx`'s one-off `react-joyride`+store+"?" button plumbing into a reusable mechanism, per
the user's explicit choice over copy-pasting a near-identical component per section.

## Modified Files
- `frontend/src/components/tours/SectionTour.tsx` (new)
- `frontend/src/components/tours/SectionTour.test.tsx` (new)
- `frontend/src/store/sectionTourStore.ts` (new, replaces `waiterTourStore.ts`)
- `frontend/src/store/sectionTourStore.test.ts` (new)
- `frontend/src/store/waiterTourStore.ts`, `.test.ts` (deleted)
- `frontend/src/store/uiStore.ts`
- `frontend/src/components/TopNav.tsx`
- `frontend/src/pages/waiter/components/WaiterTour.tsx`, `.test.tsx`
- `frontend/src/locales/{es,en}/common.ts`, `frontend/src/locales/{es,en}/waiter.ts`

## What Changed?
`sectionTourStore.ts` generalizes `waiterTourStore`'s per-user "seen" flag to per-`(sectionId,
userId)` (`hasSeenTour`/`markTourSeen`, both now take a `sectionId`), localStorage key
`ember-section-tour-storage` (old `ember-waiter-tour-storage` key is abandoned — one-time reset of
the waiter tour's seen state for existing users, accepted as a minor side effect of the migration).

`SectionTour.tsx` is now the ONE `react-joyride` wrapper for the whole app: takes `sectionId`,
`steps`, optional `ready` (mirrors the old `tableIds.length === 0` guard) and `onStepAfter` (the
escape hatch `WaiterTour` needs to reveal its detail panel mid-tour). On mount it announces its own
`sectionId` into `useUIStore.activeTourSection` (cleared on unmount) — this is how `TopNav`'s "?"
button, which lives outside the page's own subtree, knows a tour exists on the current page and
which id to request. `uiStore.ts`'s waiter-only `waiterTourRequested`/`requestWaiterTour`/
`clearWaiterTourRequest` became generic `activeTourSection`/`requestedTourSection`/
`requestTour(sectionId)`/`clearTourRequest`. `TopNav.tsx`'s "?" button visibility changed from
`isWaiterRoute` to `activeTourSection !== null` — any current or future page that mounts a
`SectionTour` gets the button automatically, no `TopNav` changes needed per new section. Also added
`id="topnav-create-button"` to `TopNav`'s "+" action button so a section's own tour steps can point
at it directly (steps for Catálogo/Personal will reuse this in the next report rather than needing
their own duplicate button).

`WaiterTour.tsx` shrank to a thin wrapper: builds its 4-step array (unchanged content,
`sectionId="waiter-tables"`) and delegates everything else to `SectionTour`. The 4 generic button
labels (`tourNextButton`/`tourBackButton`/`tourSkipButton`/`tourLastButton`) moved from the
`waiter` i18n namespace to `common` (used by every future tour regardless of page namespace); the
4 step-content keys (`tourGridTitle` etc., genuinely waiter-specific) stayed in `waiter`.

## Why It Changed?
Copy-pasting `WaiterTour`'s ~75 lines of Joyride/store/effect plumbing into 5+ more section
components would duplicate the exact same logic (including the two non-obvious bugfixes it already
required — `skipBeacon` on the first step, and resetting `run` to `true` on a replay request). One
component now owns that logic once; every future section only supplies step content.

## Verification
`cd frontend && pnpm run test:run` — 31/31 PASS (6 new `SectionTour` tests, `WaiterTour` tests
updated for the new store/store-keys, `sectionTourStore` tests replacing `waiterTourStore`'s).
`cd frontend && pnpm run build` — PASS.
