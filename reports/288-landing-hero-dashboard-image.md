# Report 288 — landing-hero-dashboard-image

## 1. Identification
- **Report number:** 288
- **Task ID:** landing-hero-dashboard-image (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 287 (landing-restyle-saas-coherence)

## 2. Objective
Replace the hero's placeholder framed box with a real screenshot of the waiter
dashboard (`Hero.png`, added by the maintainer), shown large and tilted to the
right with a 3D-perspective lean (right edge receding) instead of a flat bordered
frame; make the hero fill the whole viewport so the next section does not peek; add
a hover interaction on the image.

## 3. Modified Files
- `landing/src/components/Hero.astro` — new image + tilted media treatment, badge
  eyebrow, Anton display headline
- `landing/src/assets/Hero.png` — **new** (added by the maintainer, 3827×2041 PNG,
  216 KB source; Astro `<Image>` at `width={1800}` emits a ~30 KB WebP)
- `landing/src/styles/global.css` — `@import "@fontsource/anton"` + `--font-display` token
- `landing/package.json` / `landing/pnpm-lock.yaml` — add `@fontsource/anton`

## 4. What Changed?
- Import switched from `../assets/product-screenshot.png` to `../assets/Hero.png`,
  still through `astro:assets` `<Image>` (`width={1300} height={693}`, keeps the
  1.875:1 ratio; build optimises the 216 KB PNG down to a 19 KB WebP).
- The old `<div class="overflow-hidden rounded-xl border border-border shadow-md">`
  wrapper is gone. New structure: a `.hero-media` container with
  `perspective: 1600px` and the `<Image>` carrying `.hero-media__img`:
  `transform: rotateY(-14deg) rotate(1.5deg)` (`transform-origin: center`) so the
  right edge tilts away from the viewer, a layered soft drop shadow, `rounded-xl`
  and a hairline `ring-1 ring-black/5` for edge definition (no solid border).
- **Full-viewport hero:** the `section` is `flex min-h-dvh items-center` and its
  vertical padding dropped to `py-12` (min-height drives the size now). The hero
  fills the viewport (`section.height === window.innerHeight`) so the next section
  no longer peeks under the fold.
- **Larger image:** `<Image>` bumped to `width={1800}`; the rendered element is
  `md:w-[140%] lg:w-[158%]` and the column bleeds further right
  (`md:-mr-28 lg:-mr-44`). `section overflow-hidden` still guards against a
  horizontal scrollbar (verified: `scrollWidth <= innerWidth`).
- **Left column presence:** the plain eyebrow is now a pill badge —
  `inline-flex … rounded-full border bg-muted/50 px-3.5 py-1.5` with a small
  `bg-primary` dot. The headline moved onto a condensed athletic display face,
  `--font-display: 'Anton'` (new `@fontsource/anton` import + `@theme` token), set
  `uppercase`, `leading-[0.92]`, `text-5xl sm:text-6xl lg:text-7xl` — a "CBUM
  wordmark" feel while body copy stays Inter. Headline sits ~72px clear of the
  image at `lg` (measured). Paragraph bumped to `md:text-xl`.
- **Hover:** under `@media (hover: hover)`, hovering the panel straightens and
  lifts it — `rotateY(-5deg) rotate(0) translateY(-8px) scale(1.02)` with a deeper
  shadow, eased over `0.5s` (`transform` + `box-shadow`).
- Responsive: under `768px` the tilt and the `w-[128%]` widening are removed
  (`transform: none`, `w-full`) and the shadow softened. `prefers-reduced-motion`
  drops the transition; touch devices never get a stuck hover state (`hover: hover`
  guard).
- A first attempt used `Hero.svg` (also added by the maintainer). That file is a
  cropped viewport — `viewBox="0 0 361.13 1152"` over a `3827×2041` embedded raster
  translated `(-1733, -445)` — so at hero width it zoomed to a single table card on
  whitespace. `Hero.svg` was removed; the landscape `Hero.png` is the usable asset.

## 5. Why It Changed?
The maintainer disliked the flat framed box left over from the brutalist build and
wanted the real waiter dashboard as the hero visual, tilted right for depth. The
3D-perspective lean (chosen over a flat 2D rotation) gives the hero the "live
product panel" feel common to current SaaS landings while staying on the same
maroon/Inter design system as the app. Follow-up rounds: fill the viewport, enlarge
the image, add the hover, then give the left column weight — the eyebrow read as
loose small text, so it became a pill badge, and the headline moved to a heavy
condensed uppercase face (Anton, the free stand-in for the CBUM wordmark the
maintainer referenced) scoped to the hero only.

## Verification
- `cd landing && pnpm install && pnpm build` — green. 5 pages; `Hero.png` →
  `_astro/Hero.*.webp` (216 kB → ~30 kB at `width={1800}`). `@fontsource/anton`
  added (5.3.0).
- Dev server (`http://localhost:5174`) reviewed in Chrome at desktop width: the
  dashboard renders legibly, tilts right with the right edge receding, soft shadow.
  `getComputedStyle(h1).fontFamily` starts with `Anton`; the eyebrow renders as a
  pill. `section height === window.innerHeight` (hero fills the view, Features does
  not peek); `document.scrollWidth === window.innerWidth` (no horizontal overflow
  despite the `-mr-44` / `w-[158%]` bleed); headline↔image gap ≈ 72px at `lg`.
  Hover confirmed: the panel straightens and lifts.
- Known cosmetic: the dashboard screenshot contains a test email
  (`hanzel@ember.com`) in the detail panel — illegible at hero scale, but worth a
  cleaner capture if it ever bothers anyone.
- No file outside `landing/` touched.
