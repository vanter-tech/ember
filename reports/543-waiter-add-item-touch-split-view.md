# Report 543 — Waiter "add item" modal: touch-friendly split view (clients × categorized products)

## 1. Identification
- **Report:** 543
- **Task ID:** WAITER-ADD-ITEM-TOUCH-SPLIT-VIEW
- **Predecessor Task:** report 542 — HUB-PRICE-MODAL
- **Branch:** `feat/waiter-add-item-touch-split-view` off `main`

## 2. Objective
A prospective Ember Hub client runs on a touch-screen PC and needs dish-to-diner assignment to
work well with touch. `AddItemModal` (the waiter's "add item" flow) was a plain scrollable list
of every menu item plus a `<select>` dropdown for the participant — functional but not designed
for touch. Redesigned as a 2-column split view: clients on the left, categorized products on the
right, with a per-client draft cart confirmed all at once at the end.

## 3. Modified Files
- `frontend/src/pages/waiter/components/AddItemModal.tsx`
- `frontend/src/pages/waiter/components/AddItemModal.test.tsx`
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
- **Left column:** a chip per participant (avatar + name, same visual pattern as the
  participants card in `TableInformation.tsx`) plus a fixed "Mesa (general)" chip for
  unassigned items. Tapping a chip makes it the active client (`aria-pressed`); each chip shows
  a badge with its own draft-cart item count once non-empty.
- **Right column:** search bar (unchanged) + category tabs (same horizontal-pill pattern as the
  customer digital menu, `customer/Menu.tsx`) + a 2-col grid of the active category's items.
  Categories are derived client-side from `MenuItemResponse.category` (already present on every
  item from `inventoryMenuItemService.listAll` — no new API call). Searching overrides the
  category filter and searches every item.
- **Per-client draft cart, confirmed once at the end** (the approved design, not the
  originally-considered "tap = instant add"): tapping a product with no modifier groups adds one
  unit to the *active* client's local cart (tapping again bumps the quantity); a product with
  modifier groups opens the existing modifier-selection step first. Each client's cart is
  independent local state (`Record<clientName, CartLine[]>`) — switching the active chip swaps
  which cart is visible in the footer strip, but nothing already added is lost. The footer strip
  shows the active client's lines as removable chips (+/− steppers, "−" past 1 removes the line).
  A single "Confirmar pedido (N)" button at the bottom submits everything: one
  `SessionTableService.addWaiterItem` call per unit, looping every client's every line — same
  backend contract the old modal already used (`participantName: null` for Mesa), so **no
  backend change**.
- Removed the now-orphaned `addItemQuantityLabel` key (manual quantity input is gone, replaced
  by tap-to-add + the cart steppers); added `addItemUncategorized`, `addItemCartHeading`,
  `addItemEmptyCart`, `addItemDecrementAria`, `addItemIncrementAria`, `addItemModifierCancel`,
  `addItemModifierConfirm`; reworded `addItemSubmit` to "Confirmar pedido" / "Confirm order" to
  match the new all-at-once submit.

## 5. Why It Changed?
User-requested for a specific Ember Hub prospect operating on a touch-screen monitor. The
interaction model (draft cart per client, confirm once) was chosen over instant-tap-to-submit
after discussion: it lets a waiter build a whole table's order — several dishes, several diners —
before it hits the kitchen, matching how table-side ordering actually happens, rather than firing
one network request per tap.

## 6. Verification
- TDD: wrote the full new test suite first (12 tests covering default "Mesa" selection,
  participant chips, category filtering, search override, tap-to-add, quantity bump, per-client
  cart isolation, the modifier-group picker sub-step, the decrement-to-remove stepper, and the
  final confirm submitting one call per unit across every client) — confirmed RED against the
  old component (all 12 failed for the expected reason: old UI), then implemented until GREEN.
  Two test-authoring mistakes were caught and fixed along the way (an assumption that two
  categories' items were both visible by default, and an ambiguous `getByText('Pizza')` query
  that also matched the cart chip) — not component bugs.
- `pnpm run test:run` (frontend): **187/188** — the one failure (`MenuJoin.test.tsx`) is
  pre-existing and unrelated; confirmed by stashing this work and re-running against `main`
  alone, where it fails identically.
- `pnpm run build` (`tsc -b && vite build`): clean.
- `pnpm run lint`: 0 errors, 15 warnings — all pre-existing, none in the files this task touched.
