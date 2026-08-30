# Report 295 — landing-scrollytelling-walkthrough

## 1. Identification
- **Report number:** 295
- **Task ID:** landing-scrollytelling-walkthrough (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 294 (landing-features-page)

## 2. Objective
A scroll-driven "walkthrough" section on the home, right after the hero: the
dashboard stays pinned while four explanatory steps scroll past, and the image
zooms/pans to the relevant area of each step (Mesas → Cocina/KDS → Detalle y cobro
→ Analítica).

## 3. Modified Files
- **New** `landing/src/components/Walkthrough.astro`
- `landing/src/pages/index.astro` — `<Walkthrough />` between `<Hero />` and
  `<ContactSection />`

## 4. What Changed?
`Walkthrough.astro` renders an intro heading, then a `md:grid md:grid-cols-2`:
- **Left** — an `<ol>` of four `.walkthrough__step` blocks, each `min-h-[72vh]
  flex flex-col justify-center`, with `n · tag`, title, body and a primary-checked
  points list. Content is real (CLAUDE.md §1): floor map + table state; KDS state
  machine; split/merge bills + cash close; analytics filters + per-entity
  performance.
- **Right** — `.walkthrough__stage` (`hidden md:block`) holding
  `.walkthrough__sticky` (`position: sticky; top: 13vh; flex h-[74vh] items-center
  overflow-hidden; perspective: 1800px`). Inside is the `Hero.png` `<Image>`
  **exactly as the hero renders it** — `.walkthrough__img w-[112%] rounded-xl
  ring-1 ring-black/5` with the hero's layered shadow and 3D tilt
  (`rotateY(-13deg) rotate(1.5deg)`), no card/border around it. The sticky box just
  `overflow-hidden`-clips the bleed as the image zooms. A live-dot label chip and
  four progress dots float over it.

**Scroll mechanic** — a small `<script>` runs an `IntersectionObserver`
(`rootMargin: -45% 0px -45%`, so a step "activates" when it crosses viewport
centre) that writes `data-active="0..3"` on the section and updates the label
text. CSS keys off `.walkthrough[data-active='N'] .walkthrough__img` to transition
`transform-origin` + `transform: rotateY() rotate() scale()` (0.75s cubic-bezier):
step 0 zooms the table grid (`scale 1.7`, origin `20% 32%`), step 1 pans mid
(`1.45`), step 2 zooms the right "Detalles de mesa" / charge panel (`2.15`, origin
`88% 26%`), step 3 zooms back out nearly flat (`1.12`, `rotateY 0`) — so the tilt
also relaxes as you go. Matching `data-active` rules light the progress dot.
`prefers-reduced-motion` freezes it at the resting tilt.

- **Mobile** (`md:hidden`) — each step carries its own static `Hero.png` above the
  text (`rounded-xl ring-1 shadow-lg`, no card); no sticky, no zoom.
- **Single-asset caveat** — only the waiter-floor screenshot exists, so steps 2
  (Cocina/KDS) and 4 (Analítica) are pans/zooms of that same image with a label,
  not their own screens. Dropping `assets/kds.png` / `assets/analitica.png` and
  swapping the frame `src` per `data-active` is a small follow-up.

## 5. Why It Changed?
The maintainer asked for a scroll-linked animation: "primero estamos viendo el
dashboard … mientras scrolleo … la imagen … ir haciendo zoom." Approved as
sticky-visual + four zoom steps, then refined: **no framed card — the hero image
itself (tilted, ring, shadow) zooming**, so the first build's bordered
`object-cover` frame was replaced with the bare hero-style image transformed by
`scale`/`transform-origin`/reducing `rotateY`. Built with IntersectionObserver +
CSS transitions (not `animation-timeline: scroll()`, which is not cross-browser).
The section also gives the home a product story back after `Features` moved to
`/funcionalidades` (report 294).

## Verification
- `cd landing && pnpm build` — green, 7 pages.
- Dev server in Chrome: scrolling through the section drives `data-active` 0 → 1 →
  2 → 3; the label chip reads Piso → Cocina → Cuenta → Datos; the framed image
  visibly zooms to the table grid, pans to the detail panel, and zooms back out;
  progress dots follow; the frame stays pinned (`sticky`) through the whole
  section. No horizontal page overflow.
- No file outside `landing/` touched.
