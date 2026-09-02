# Report 334 — feat: section tour + "?" help button for the waiter table-detail view

## 1. Identification
- **Report number:** 334
- **Current Task:** feat-waiter-table-detail-section-tour
- **Predecessor Task:** report 333 (fix-waiter-table-view-action-button-width)

## 2. Objective
On `/waiter/tables/:id` (`TableInformation.tsx`) the "?" tutorial icon next to
the language switcher never showed. `WaiterLayout` renders `TopNav`, but the "?"
button is gated on `activeTourSection` (`TopNav.tsx:94`), which is only set when a
mounted `<SectionTour>` announces itself (`SectionTour.tsx:36`). This view mounted
none. Give it its own section tour, matching the pattern used by every admin
list page and the waiter cash-register view.

## 3. Modified Files
- `frontend/src/pages/waiter/TableInformation.tsx`
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
- `TableInformation.tsx`: imported `SectionTour` + `Step`; added a `tourSteps:
  Step[]` array (5 steps) and rendered
  `<SectionTour sectionId="waiter-table-detail" steps={tourSteps} ready={!!sessionData} />`
  at the top of the returned fragment. Added 5 stable anchor ids:
  `#table-tour-actions` (header action-button row), `#table-tour-orders` (order
  details card), `#table-tour-participants` (participants card),
  `#table-tour-activity` (activity card), `#table-tour-bill` (right-hand
  bill/summary column).
- `locales/es/waiter.ts` / `locales/en/waiter.ts`: 10 new keys
  (`tourTableActionsTitle/Content`, `tourTableOrdersTitle/Content`,
  `tourTableParticipantsTitle/Content`, `tourTableActivityTitle/Content`,
  `tourTableBillTitle/Content`), inserted next to the existing `tour*` keys.

No changes to `SectionTour`, `TopNav`, `uiStore`, or `sectionTourStore` — the
generalized machinery from reports 215–218 already supports this by contract;
the view just had to opt in.

## 5. Why It Changed?
`SectionTour` was built (report 215) so any page gets a replayable Joyride tour
plus the TopNav "?" button "for free" by mounting the component. The waiter
table-detail route was simply never wired up (reports 216–218 covered the
Catálogo/Analíticas/Personal/Caja/Configuración sections, not this one). Adding
the mount + anchors + copy closes that gap with no mechanism changes. `ready`
is gated on `sessionData` so Joyride does not try to point at cards that are
still loading. `sectionId` `waiter-table-detail` is unique, so the per-user
seen-flag in `sectionTourStore` is independent of the `waiter-tables` grid tour.

## Verification
- `cd frontend && pnpm run build` — PASS (`tsc -b && vite build`, 2847 modules,
  built in 1.63s, 0 TypeScript errors; confirms `satisfies typeof esWaiter`
  parity).
- `pnpm run test:run` — PASS (12 files, 41/41).
