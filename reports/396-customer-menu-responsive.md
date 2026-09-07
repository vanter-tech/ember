# Report 396 — fix the horizontal scroll on the customer menu view

## 1. Identification
- **Report:** 396
- **Task ID:** customer `/customer/menu` horizontal scroll on phones
- **Predecessor:** Live QA findings Q1–Q6 (reports 387–395, all merged)

## 2. Objective
Stop the customer menu screen from scrolling sideways on a phone, with the "Ver factura"
button pushed off the right edge. Leave the desktop / tablet layout exactly as it was.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx` (2 lines)

## 4. What Changed?
Only the `Carta Digital` header row:

- `flex items-center justify-between p-4` → `flex flex-col gap-3 p-4 sm:flex-row sm:items-center
  sm:justify-between`. On `sm:` (≥ 640px) and up this resolves to exactly `flex items-center
  justify-between p-4` — no visual change. Below `sm:` it stacks: the title block on top, the
  table-code badge + "Ver factura" button on their own row underneath, so nothing is forced
  past the viewport width.
- The badge + button group `flex items-center gap-3` → `flex flex-wrap items-center gap-3`.
  `flex-wrap` is a no-op when there's room (desktop, tablet); on a very narrow phone the button
  drops below the badge instead of overflowing.

Nothing else was touched — no size/padding changes, no `max-w` container, no changes to the
menu-item cards, no changes to `CustomerLayout`.

## 5. Why It Changed?
`Menu.tsx` is desktop-first; the product grid is responsive but the header wasn't. The
`Carta Digital` row was a single non-wrapping `flex justify-between` whose right side is a
`Badge` with `p-6` (~24px padding all round, ~230px wide) plus an `h-13 px-5` button, and whose
left side is a `text-3xl` heading — roughly 430px on a ~375px phone, so it overflowed and the
whole page scrolled sideways. Restacking that one row on small viewports removes the cause and
keeps every larger breakpoint byte-for-byte identical.

Verification: `pnpm run build` + `lint` clean. A layout change can't be unit-tested meaningfully
(jsdom has no layout engine); a live check at a phone-width viewport is the final confirmation.

### Note
An earlier version of this change also constrained the container width, shrank paddings/fonts,
line-clamped card descriptions and added `overflow-x-clip` to `CustomerLayout`. That altered the
desktop appearance, which was out of scope — it was reverted and replaced with the minimal
2-line change above.
