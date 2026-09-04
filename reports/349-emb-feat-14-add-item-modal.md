# Report 349 — EMB-FEAT-14: Add-item modal on the waiter table view

## Identification
- **Report number:** 349
- **Current Task ID:** EMB-FEAT-14 (Plan C — `docs/superpowers/plans/2026-09-03-waiter-table-detail-actions.md`, Task 3)
- **Predecessor Task:** EMB-FEAT-13 (report 348 — `POST /sessions/{id}/waiter-items` endpoint)

## Objective
Frontend consumer of EMB-FEAT-12/13: a searchable "add item" modal (menu list + minimal
modifier picker + quantity + participant selector) wired to the previously-dead
"Agregar platillo" header button in `TableInformation.tsx`, posting to
`POST /sessions/{id}/waiter-items` so a waiter can send a dish straight to the kitchen.

## Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/store/uiStore.ts`
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`
- `frontend/src/pages/waiter/TableInformation.tsx`
- `frontend/src/pages/waiter/components/AddItemModal.tsx` (new)
- `frontend/src/pages/waiter/components/AddItemModal.test.tsx` (new)

## What Changed?
- **`api.ts`** — added `SessionTableService.addWaiterItem(sessionId, { menuItemId, selectedOptionIds, participantName })` → `POST /sessions/${sessionId}/waiter-items`, returns `void`. Placed next to `addItem`.
- **`uiStore.ts`** — added `'ADD_ITEM'` to the `ModalType` union.
- **`locales/{es,en}/waiter.ts`** — 9 new parity keys: `addItemModalTitle`, `addItemSearchPlaceholder`, `addItemParticipantLabel`, `addItemParticipantMesa`, `addItemQuantityLabel`, `addItemSubmit`, `addItemSuccessToast`, `addItemErrorToast`, `addItemBillExistsToast`.
- **`AddItemModal.tsx`** (new) — pattern-matched on `ChargeTableModal.tsx` (shared `Dialog`, `useUIStore`, `useMutation`, `useTranslation('waiter')`):
  - `isOpen = activeModal === 'ADD_ITEM'`; `sessionId` / `participants` read from `modalPayload`.
  - `useQuery(['menuItemsAll'], inventoryMenuItemService.listAll, { enabled: isOpen })`.
  - Local state: `search`, `selected: MenuItemResponse | null`, `optionIds: Record<groupId, number[]>`, `qty` (min 1), `participant` (`''` = Mesa).
  - Case-insensitive name filter over the fetched list; click selects an item and clears modifier picks.
  - Minimal modifier picker (only when `selected.modifierGroups` non-empty): radio inputs for `SINGLE_REQUIRED` groups, checkboxes (capped at `maxSelections`) otherwise — field names (`group.options`, `option.id`, `option.priceDelta`, `group.selectionType`) reused from `customer/components/SelectModifiersModal.tsx`, reimplemented locally to avoid pulling in customer cart state.
  - Participant `<select>`: "Mesa (general)" (value `''`) plus each named participant.
  - Mutation loops `qty`× `addWaiterItem`; `participantName: participant || null`.
  - `onSuccess`: invalidate `['sessionDetails', sessionId]` + `['bill', sessionId]`, success toast, close + reset. `onError`: axios 409 → `addItemBillExistsToast`, else `addItemErrorToast`.
  - Submit disabled while `!selected` or mutation pending.
- **`AddItemModal.test.tsx`** (new) — 2 tests: search filters the list; selecting an item + submitting calls `addWaiterItem('s1', { menuItemId: 10, selectedOptionIds: [], participantName: null })`.
- **`TableInformation.tsx`** — import + render `<AddItemModal/>` alongside the other modals; "Agregar platillo" button now `onClick={() => openModal('ADD_ITEM', { sessionId: id, participants: sessionData?.participants ?? [] })}` and `disabled={sessionData?.status !== 'OPEN'}`.

## Why It Changed?
The three header buttons in `TableInformation.tsx` were rendered but inert. EMB-FEAT-12/13
built the backend path for a waiter to add a dish straight to the kitchen; this task delivers
the UI that drives it. Quantity is handled client-side as a loop of single-item POSTs because
the endpoint takes one `menuItemId` per call. The 409 branch surfaces the backend's
"bill already requested" guard as an actionable toast.

## Plan Deviations
- The plan's `AddItemModal.test.tsx` snippet asserts on raw i18n keys (`getByText('addItemSubmit')`).
  This repo does **not** mock `@/lib/i18n` in tests — `t()` returns the real ES string (default
  locale), matching the existing `WaiterTour.test.tsx` convention. Assertions use the real ES
  copy (`'Buscar platillo...'`, `'Agregar a la comanda'`) instead.
- Used `vi.mocked(...)` rather than the plan's `as vi.Mock` casts (cleaner, same effect).

## Verification
- `pnpm run test:run AddItemModal` → **2/2 PASS**
- `pnpm run test:run` (full) → **59/59 PASS** (18 files; was 57/57 + 2 new)
- `pnpm run build` → **PASS**, 0 TypeScript errors
- `pnpm run lint` → **0 errors** (17 pre-existing warnings, none in the new files)
