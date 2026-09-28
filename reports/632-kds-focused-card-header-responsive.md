# Report 632 — KDS-FOCUSED-CARD-HEADER-RESPONSIVE

## 1. Identification
- **Report:** 632
- **Task ID:** KDS-FOCUSED-CARD-HEADER-RESPONSIVE (ad hoc, live bug report)
- **Predecessor Task:** report 631 — MOBILE-ACTIONS-CIRCLE-COLOR

## 2. Objective
Fix the KDS "focused card" (the large order-detail card shown below the queue row) overflowing its own container on mobile: the bulk-status `Select`, "Seleccionar todo" button, and "Imprimir" button in the card header were not responsive and spilled past the card edge on narrow viewports.

## 3. Modified Files
- `frontend/src/pages/kitchen/components/FocusedCard.tsx`

## 4. What Changed?
In `FocusedCard`'s `CardHeader`:
- The outer row (ticket/time info + actions) went from `w-full flex items-center justify-between` to `w-full flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between` — stacks vertically below `sm`, row layout preserved at `sm` and up.
- The actions row (bulk-status `Select` + "Seleccionar todo/Deseleccionar" + "Imprimir") went from `flex flex-row items-center gap-3` to `flex flex-wrap items-center gap-3` — wraps instead of forcing all three controls onto one non-shrinking line.

No logic changes; pure Tailwind class edits.

## 5. Why It Changed?
Live user report: on mobile, the header's action controls (select-all button and the bulk status dropdown) rendered outside the card's bounds instead of wrapping or stacking. The two sibling flex rows had no wrap/stack behavior, so their combined width exceeded the card at phone viewport widths. Adding `flex-col`→`sm:flex-row` on the outer row and `flex-wrap` on the inner actions row lets the controls reflow within the card at any width.

## Verification
- `cd frontend && pnpm run build` → clean (`tsc -b && vite build`, 0 errors; pre-existing chunk-size warning only).
- Not live-verified in-browser this session (no running kitchen-role dev session available); fix is a scoped Tailwind wrap/stack change on the exact two flex containers identified as the overflow source.
