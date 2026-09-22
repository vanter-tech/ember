# Report 542 — Ember Local card: "Ver precio" button opens a pricing modal

## 1. Identification
- **Report:** 542
- **Task ID:** HUB-PRICE-MODAL
- **Predecessor Task:** report 541 — DOWNLOADS-BUTTON-ALIGNMENT (sibling branch, not yet merged)
- **Branch:** `feat/hub-price-modal` off `main`

## 2. Objective
Show Ember Hub's list price on the landing page — previously fully hidden behind a "Hablar con
el equipo" contact CTA — without it reading as a cold sticker price. A button on the `LocalPlan`
card opens a modal with the price *and* a short value explanation before the visitor ever talks
to a human, addressing the earlier "seeing a bare price feels expensive" concern from the same
pricing conversation (see memory `ember-hub-pricing`).

## 3. Modified Files
- `landing/src/components/LocalPlan.astro`
- `landing/src/i18n/ui.ts` (ES + EN blocks)

## 4. What Changed?
- New secondary "Ver precio" / "See price" button on the `LocalPlan` card, styled like the
  site's existing outline-button convention (`PlanCards.astro`'s non-highlighted CTA), placed
  above the existing "Hablar con el equipo" primary CTA.
- New `<dialog id="local-price-modal">` + inline `<script>`, copying the exact working pattern
  already used by `landing/src/pages/info/videos.astro`'s video lightbox (`showModal()`/`close()`,
  backdrop-click-to-close, `astro:page-load` re-binding for Astro's client router). No new JS
  dependency, no React island — plain `<dialog>`.
- Modal shows the list price only, no founder discount (that stays private/negotiated per
  memory `ember-hub-pricing`): **Anual $490/año** (with the "2 meses gratis" framing, matching
  Cloud Pro's own annual price and discount convention) and **Semestral $294/semestre**, plus a
  value paragraph ("incluye todo sin límites... lo mismo que el plan Pro, corriendo en tu propio
  servidor") and the fixed-price note. Closes with "Hablar con el equipo" as the next step.
- Updated `local.plan.pay.note` — it previously said the license price was "a convenir según la
  cantidad de tablets y mesas" (to be negotiated by tablet/table count), which would have
  contradicted the concrete number now shown in the modal. Reworded to state the license price is
  fixed regardless of tablet/table count, and only installation is quoted per-site.

## 5. Why It Changed?
User request, following up on the same session's pricing conversation: show the price with
context rather than leaving it fully hidden. A modal gated behind a deliberate click — not a
number sitting in the open on page load — self-selects for people who are actually evaluating the
product, and the explanation text is shown in the same breath as the number so it never reads as
a bare price with no justification.

## 6. Verification
- `cd landing && pnpm run build` — clean, 28 pages.
- Live-tested in Chrome (`astro dev --background`) on both `/planes` and `/en/planes`: button
  opens the modal, price/copy render correctly in both languages, × button and backdrop click
  both close it. (First manual click attempt appeared not to register — traced to a scroll-timing
  artifact in the test tooling, not a page bug: a JS-dispatched click and a second real click both
  opened/closed the dialog correctly and the screenshot confirmed the fully-rendered modal.)
