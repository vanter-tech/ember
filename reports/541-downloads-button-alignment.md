# Report 541 — Downloads page: align the Hub and print-agent CTA buttons

## 1. Identification
- **Report:** 541
- **Task ID:** DOWNLOADS-BUTTON-ALIGNMENT
- **Predecessor Task:** report 540 — PRICING-TABLE-HONESTY (sibling branch, not yet merged)
- **Branch:** `fix/downloads-button-alignment` off `main`

## 2. Objective
On `/info/descarga`, the print-agent and Ember Hub download cards sit side by side
(`sm:grid-cols-2`) with different-length body copy, so their "Descargar" buttons landed at
different heights instead of lining up.

## 3. Modified Files
- `landing/src/pages/info/descarga.astro`

## 4. What Changed?
The button's class changed from `mt-6` (fixed top margin) to `mt-auto pt-6` — same pattern
`PlanCards.astro` already uses for the pricing cards' CTA buttons. Each `<section>` card is
`flex flex-col`; the grid row already stretches both cards to equal height (grid's default
`align-items: stretch`, no override on the container), so `mt-auto` now pushes the button to the
bottom of whichever card it's in regardless of how many lines the body/requirement text above it
wraps to. `landing/src/pages/en/info/descarga.astro` is a bare re-export of this file, so no
second edit was needed for the English page.

## 5. Why It Changed?
Reported live: the Ember Hub and print-agent download buttons weren't aligned with each other.

## 6. Verification
- `cd landing && pnpm run build` — clean, 28 pages.
- Grepped the built `dist/info/descarga/index.html` for the new class — present on both cards.
