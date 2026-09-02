# Report 330 — landing: idle float animation on TiltedShot images

## 1. Identification
- **Report number:** 330
- **Task ID:** landing polish (ad-hoc — hero/funcionalidades image motion)
- **Predecessor task:** report 329 (LSEO-05 logo .ico → PNG)

## 2. Objective
Add a continuous, subtle "floating" (idle bob) motion to the tilted app-screenshot
images on the landing — the hero shot and the per-feature shots on
`/funcionalidades` — plus a gentler, out-of-phase bob on the badges layered over
the hero shot (`[data-float]` chips). Keep the existing static 3D tilt, the hover
"lift", the chips, and the layout untouched.

## 3. Modified Files
- `landing/src/components/TiltedShot.astro` (`<style>` block only)

## 4. What Changed?
`TiltedShot` is used in exactly two places — `Hero.astro` (with `[data-float]`
chips passed through the slot) and `funcionalidades.astro` (mapped per feature,
`flip` alternating per row, no chips) — so the effect was made default on
`.tiltshot` rather than an opt-in prop; `Hero.astro` and `funcionalidades.astro`
were not touched.

Added to the scoped `<style>`:
- **`@keyframes tiltshot-float`** — `transform: translateY(0 → -12px → 0)`, applied
  to the `.tiltshot` container. The container has no `transform` of its own, so
  the bob composes cleanly with the child `.tiltshot__img`'s tilt
  (`rotateY(...) rotate(...)`) instead of overwriting it.
- **`@keyframes tiltshot-float-soft`** — animates the CSS **`translate`** property
  (not `transform`) `0 → -5px → 0` on `.tiltshot [data-float]`, so it stacks with
  the chips' existing `transform: rotate(var(--float-rot))` without clobbering the
  rotation. Smaller amplitude + a different period (5s vs 6s) gives a shallow
  parallax/depth feel.
- Both animations live inside **`@media (min-width: 768px)`** — desktop only,
  matching the existing tilt/hover (mobile already flattens the tilt at ≤767px
  and continuous motion is undesirable on small/battery devices).
- **`.tiltshot--flip`** gets `animation-duration: 7s` + `animation-delay: -2.5s`
  so the alternating `/funcionalidades` rows bob out of sync rather than in
  mechanical unison.
- **Hover pause:** `.tiltshot:hover`, `.tiltshot:hover [data-float]` →
  `animation-play-state: paused`, so the existing hover "lift" reads cleanly. The
  hover rule for chips also sets `translate: 0` with a short `translate`
  transition so they settle rather than freeze mid-bob.
- **`prefers-reduced-motion: reduce`** block extended: `.tiltshot`,
  `.tiltshot--flip`, `.tiltshot__img`, `.tiltshot [data-float]` now also get
  `animation: none` (it previously only cleared `transition`).

## 5. Why It Changed?
The hero/feature screenshots were fully static apart from a hover state most
visitors never trigger. A slow idle bob makes the page feel alive and hints at
"tiempo real" without new assets or JS. Doing it in CSS on the container (bob) vs
the image (tilt) — and on the `translate` property vs `transform` for the chips —
keeps every existing transform intact, so it is purely additive.

## 6. Verification
- `cd landing && pnpm run build` — 20 pages, sitemap emitted, no errors.
- `@keyframes tiltshot-float` / `tiltshot-float-soft` confirmed inlined into
  `dist/index.html` and `dist/funcionalidades/index.html` (ES + EN).
- Landing-only, `<style>`-only change; backend/frontend suites unaffected.
- Visual check (desktop bob present, out-of-phase feature rows, subtler chip
  drift, nothing on mobile / reduced-motion, hover lift + chip hover intact) to
  be done in `astro preview` / after deploy.
