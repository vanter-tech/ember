# Report 544 — Waiter "add item" modal: review feedback (size, spacing, photos, misclick guard)

## 1. Identification
- **Report:** 544
- **Task ID:** WAITER-ADD-ITEM-REVIEW-FEEDBACK
- **Predecessor Task:** report 543 — WAITER-ADD-ITEM-TOUCH-SPLIT-VIEW
- **Branch:** `fix/waiter-add-item-modal-review-feedback` off `main`

## 2. Objective
User review of the just-shipped touch split view (report 543) asked for four changes: a bigger
modal, more visual separation between the clients and products columns, showing each dish's
photo (already available on `MenuItemResponse.imageUrl`), and a dedicated "+" button per product
instead of the whole card being the add-trigger — tapping a dense card on a touch screen is an
easy misclick.

## 3. Modified Files
- `frontend/src/pages/waiter/components/AddItemModal.tsx`
- `frontend/src/pages/waiter/components/AddItemModal.test.tsx`
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
- Modal width `sm:max-w-3xl` → `sm:max-w-6xl` (the largest in the codebase — every other modal
  tops out at `xl`), padding `p-6` → `p-8`.
- Client/product column gap `gap-4` → `gap-10`, plus a `border-r` on the client column so the
  split reads as two distinct panels, not just adjacent content. Client chips grew (`size-8` →
  `size-10` avatar, more internal padding) — bigger touch targets, matching the "bigger overall"
  ask. Both columns' internal scroll areas grew (`max-h-[50vh]`/`max-h-[38vh]` →
  `max-h-[65vh]`/`max-h-[55vh]`) to use the extra room; the product grid picked up a third column
  on wider viewports (`grid-cols-2 sm:grid-cols-3`).
- Each product card now shows its `imageUrl` (a fixed `h-28` photo strip, `object-cover`) or —
  when the item has none — a placeholder (`UtensilsCrossed` icon on a neutral background,
  `data-testid="menu-item-placeholder-{id}"` for tests).
- **The card itself is no longer clickable.** It's a plain `<div>`; the only interactive element
  is an 44px (`size-11`) round "+" button next to the price, `aria-label="Agregar {name}"`. This
  directly closes the misclick gap — image, name and price are inert display now, so a stray
  touch anywhere except the button does nothing.

## 5. Why It Changed?
Direct user feedback after reviewing the shipped feature on the actual target hardware
consideration (a touch-screen PC). All four points are standard touch-UI hygiene: bigger elements
and clearer separation reduce mis-taps in general; a dedicated, appropriately-sized add control
(vs. a whole dense card) is the specific fix for "I can fat-finger the wrong thing"; photos help
staff recognize dishes faster than a name/price pair alone, which matters when moving quickly
table-side.

## 6. Verification
- TDD: updated the test suite first — replaced every "tap the card" interaction with "tap the
  card's own `+` button" (`Agregar {name}` by accessible name), added a dedicated misclick-guard
  test (tapping the price text does nothing) and a photo/placeholder test. Confirmed RED against
  the report-543 component (8 failures, all for the expected reason — no "+" button, no photo
  markup — one test-authoring mistake caught along the way: asserting the cart *testid* itself
  disappears, when it's always rendered and only its content changes), then implemented to GREEN.
- `pnpm run test:run` (frontend): **190/190** (the MenuJoin flake noted in report 543 passed this
  run — confirmed intermittent, not something either task introduced).
- `pnpm run build` (`tsc -b && vite build`): one real type error caught and fixed
  (`item.name` is `string | undefined`, the `t()` interpolation param wanted `string | number` —
  fixed with `item.name ?? ''`); clean after that.
- `pnpm run lint`: 0 errors, 15 pre-existing warnings, none in the touched files.
- **Not done this round:** a live visual check in a real browser against a running backend
  (report 543 did one via `astro dev`/Chrome for the landing-page work, but this modal needs an
  authenticated waiter session with an open table to render real data — judged disproportionate
  for a styling/interaction refinement already covered by 13 passing behavioral tests and a
  type-checked build). Worth a real look before the next installer goes to the actual client.
