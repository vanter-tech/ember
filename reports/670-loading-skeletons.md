# Report 670 — LOADING-SKELETONS

## 1. Identification
- Report: 670
- Task ID: LOADING-SKELETONS
- Predecessor: 669 (SETTINGS-TAB-TRANSITION)

## 2. Objective
Replace the bare "Cargando…" texts shown while data loads with skeleton placeholders (good practice, and it makes the page fade-in animation cover the real layout instead of a text). Scope chosen by the user: "where they are needed".

## 3. Modified Files
- New: `frontend/src/components/ui/skeleton.tsx`; `frontend/src/components/skeletons/{LoadingStatus,SettingsFormSkeleton,CardGridSkeleton,ListRowsSkeleton,ChartSkeleton,BarRowsSkeleton}.tsx` (+ `skeletons.test.tsx`)
- Views using them: waiter `Tables.tsx`, `TableInformation.tsx`; the 8 Settings tabs that loaded `isLoadingSettings` (`Billing`, `BusinessHours`, `Hardware`, `Loyalty`, `Menu`, `PaymentGateway`, `Space`, `Ticket`) and `LoyaltyRewardsSettings`; admin `Category`, `Inventory`, `ListMenuItem`, `ModifierGroups`, `Staff`; analytics `SummaryCards`, `SalesChart`, `ProductPerformance`, `TableAnalytics`; cash register `accountant/CashRegister`, `ShiftHistoryTable`, `DailyZReportPanel`; `kitchen/OrdersDisplay`; `customer/Menu`; `console/ConsoleRestaurantDetail`.
- Tests (new): `SettingsTabs.loading.test.tsx`, `AdminViews.loading.test.tsx`, `Analytics.loading.test.tsx`, `loadingStates.test.tsx`, `TableInformation.loading.test.tsx`; `Tables.test.tsx` (3 loading tests).
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- `Skeleton` is a decorative (`aria-hidden`) block that only pulses under `motion-safe`. `LoadingStatus` keeps the old loading text as a `role="status"` `sr-only` region, so screen readers still hear it. Four composed placeholders mirror the shapes of the real content: a settings form card, a grid of cards (columns/gap/height passed in to match the real grid), rows with avatar + lines, a chart block, and "label + bar" rows.
- Each view that used to `return <div>Cargando…</div>` (or showed that text in a block) now shows the placeholder with the same box as the real content, so nothing jumps when data arrives. `waiter/tables` draws its title, legend and panel from the first frame with 6 placeholder cards, and does not show "no tables yet" or "open the caja" while loading (the drag-and-drop grid mounts when the data is in). Pages that keep a frame (analytics cards, the cash register title, Settings card) keep it and only the data zone is a skeleton.
- Left as they were on purpose: spinners inside buttons in an action's pending state, the full-screen spinner of the public `TenantLanding` (its layout depends on the branding that is loading), the TicketLogo/BannerPicker/onboarding spinners, and two small inline texts ("Cargando pagos…" inside an expanded shift row, "Cargando historial…" in the console).

## 5. Why It Changed?
A bare text gives the page no structure while it loads: the fade-in plays over the text and the real content pops in later, and the page looks empty. Skeletons keep the layout stable and give immediate visual structure. Tests use a pending `api.get` (no real network) and each failed before its view was changed.

### Verification
- New tests: skeleton components 8/8, Settings tabs 9, admin lists 4, analytics 4, other views 7, table detail 1, waiter tables 3. Full `pnpm run test:run` 359/360 — the one failure is the known `MenuJoin` "authenticated…" test. `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings).

### NOT verified
- How the skeletons look on screen (colour `bg-zinc-200/70`, box sizes vs the real content) and the pulse on a real device: not inspected visually; the sizes (`h-28`, `h-72`, `h-80`…) are estimates of the real cards.
- The skeletons do not count as LCP candidates (no text), so the LCP number itself may not change; the gain is in perceived loading.
