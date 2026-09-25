# Report 594 — WAITER-ADD-ITEM-MOBILE

## 1. Identification
- Report: 594
- Task ID: WAITER-ADD-ITEM-MOBILE
- Predecessor: WAITER-TABLE-SELECTED-HIGHLIGHT (report 593)

## 2. Objective
Make the waiter "add dish" modal usable on phones (it overflowed and its controls were too small/wide).

## 3. Modified Files
- `frontend/src/pages/waiter/components/AddItemModal.tsx`
- `frontend/src/pages/waiter/TableInformation.tsx`

## 4. What Changed?
- Below `sm`: dialog padding `p-4`, tighter gaps; layout is one column instead of the 240px client rail + content grid.
- Client chips become a horizontal scroll row (label hidden), `shrink-0`, min height 44px.
- Dish grid: shorter images (`h-28`), smaller text, `size-11` add button, list height `100dvh-23rem`; cart button, search input and footer button are at least 44px/40px tall.
- Modifier popover: width `min(24rem, 100vw-2rem)`, `max-h-[70dvh]`; the trigger-height match moved from inline style to `sm:h-[var(--radix-popover-trigger-height)]` so it only applies on wider screens.
- Cart panel padding `p-4` on mobile.
- Desktop (`sm`+) classes are unchanged.
- `TableInformation.tsx` header: title block and the 4 action buttons (QR, print, transfer, add dish) were one non-wrapping row, which widened the page on phones (horizontal scroll, `FloatingNav` pushed out of view). Now the header stacks below `md`, buttons wrap (`flex-wrap`) with `h-12`/`px-4` on mobile, `h-18`/`px-6` from `sm`; title `text-2xl` on mobile and smaller paddings.

## 5. Why It Changed?
The dialog had fixed `p-8`, a two-column grid and a 384px popover, which broke on narrow viewports.

`FloatingNav.tsx` was NOT changed: it already has mobile sizing, and no defect was found from the code alone (the dialog portal sits above it). Needs a concrete repro (role + screen).

Verification: `pnpm run build` clean, lint 0 errors, waiter tests 39/39. Not verified on a device.
