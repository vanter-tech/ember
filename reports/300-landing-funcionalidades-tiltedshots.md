# Report 300 — landing-funcionalidades-tiltedshots

## 1. Identification
- **Report number:** 300
- **Task ID:** landing-funcionalidades-tiltedshots (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 299 (landing-tiltedshot-component)

## 2. Objective
Use `<TiltedShot>` on `/funcionalidades` — one tilted screenshot per feature in the
detail section, alternating sides. `Hero.png` as a placeholder for now; real admin
views (analytics, categorías, KDS…) get dropped in later.

## 3. Modified Files
- `landing/src/pages/funcionalidades.astro`

## 4. What Changed?
The detail section (`bg-muted/30`) was a `divide-y` stack of text-only rows
(`md:grid-cols-[1fr_1.4fr]`: title/description on the left, a points grid on the
right). It is now a `space-y-24 md:space-y-32` list of **text + visual** rows at
`max-w-6xl`:
- Each row is `grid md:grid-cols-2 items-center gap-16`. Odd rows add
  `md:[&>*:first-child]:order-2` so the text moves right and the image left —
  the four features alternate sides.
- Left/right text block: `feature.n` eyebrow, `h2` (`md:text-3xl`), description, and
  the `points` list (now single-column `space-y-2.5` since the column is narrower).
- Visual: `<TiltedShot src={placeholderShot} width={1600} alt={`Vista de Ember:
  ${feature.title}`} flip={i % 2 === 1} class="md:w-[112%]" frameClass={odd ?
  'md:-ml-6 lg:-ml-12' : 'md:-mr-6 lg:-mr-12'} />` — `flip` + the `-ml`/`-mr` bleed
  follow whichever side the image is on. The `<section>` gained `overflow-hidden` so
  the tilt/bleed never causes a horizontal scrollbar.

All four `<TiltedShot>`s currently import the same `../assets/Hero.png`
(`placeholderShot`), with a frontmatter comment: replace with per-view captures in
`src/assets/` and point each `src` at its own image (e.g. add an `image` field to
`lib/features.ts`).

## 5. Why It Changed?
"Aplícalo en /funcionalidades usando Hero.png por ahora, después pongo diferentes
imágenes de la vista del admin, como las analytics, categoría." The component from
report 299 is now in use on a second page; swapping images is a one-line change per
feature.

## Verification
- `cd landing && pnpm build` — green, 10 pages.
- Dev server in Chrome: `/funcionalidades` shows four alternating text/visual rows,
  each with a tilted screenshot (ring + layered shadow, straighten on hover);
  `document.querySelectorAll('.tiltshot').length === 4`;
  `document.scrollWidth === window.innerWidth` (no horizontal overflow despite the
  tilt and `-ml`/`-mr` bleed).
- No file outside `landing/` touched.
