# Report 299 — landing-tiltedshot-component

## 1. Identification
- **Report number:** 299
- **Task ID:** landing-tiltedshot-component (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 298 (landing-home-sections-bigger-hero)

## 2. Objective
The maintainer likes the hero's image treatment (tilted 3D panel, layered shadow,
straighten-on-hover, floating chips) and wants to reuse it with other images.
Extract it into a reusable component.

## 3. Modified Files
- **New** `landing/src/components/TiltedShot.astro`
- `landing/src/components/Hero.astro` — now renders the dashboard through
  `<TiltedShot>`; the duplicated media CSS is removed.

## 4. What Changed?
### `TiltedShot.astro`
A self-contained wrapper around Astro's `<Image>`:
- **Props:** `src` (imported `ImageMetadata`), `alt`, `width` (default 1800,
  height inferred), `flip` (tilt the other way), `eager` (above-the-fold
  `loading="eager" fetchpriority="high"`), `class` (extra `<img>` classes for
  responsive widths), `frameClass` (extra wrapper classes for `-mr` bleed).
- **Scoped `<style>`:** `perspective` on the wrapper; `.tiltshot__img` carries the
  `rotateY(-14deg) rotate(1.5deg)` tilt, the two-layer shadow and a `.5s`
  transition; `.tiltshot--flip` mirrors the angle; `@media (hover:hover)` straightens
  and lifts the panel (`scale(1.02)`); `@media (max-width:767px)` flattens it;
  `prefers-reduced-motion` kills the transitions.
- **Floating chips via the default slot:** any child marked `data-float` gets a
  base `transform: rotate(var(--float-rot, 0deg))` and, on
  `.tiltshot:hover` (a `:global()` selector so it reaches slotted light-DOM),
  `translateY(-4px) rotate(var(--float-rot-hover, 0deg))`. The consumer sets the
  two angles inline.

### `Hero.astro`
The `.hero-media` block became:
```astro
<TiltedShot src={heroDashboard} width={2200} eager
  alt="…"
  frameClass="md:-mr-10 lg:-mr-16 xl:-mr-24"
  class="md:w-[132%] lg:w-[146%] xl:w-[156%]">
  <div data-float style="--float-rot: 3deg;  --float-rot-hover: 2deg"  class="absolute -top-4 left-4 …">…</div>
  <div data-float style="--float-rot: -3deg; --float-rot-hover: -2deg" class="absolute -bottom-4 -left-4 …">…</div>
</TiltedShot>
```
`.hero-grid` / `.hero-glow*` stay in `Hero.astro`; `.hero-media*` / `.hero-nav` /
`.hero-card` CSS is deleted (now in `TiltedShot`). No visual change.

### How to reuse with a different image
```astro
---
import kds from '../assets/kds.png';
import TiltedShot from '../components/TiltedShot.astro';
---
<TiltedShot src={kds} alt="Pantalla de cocina de Ember" flip class="lg:w-[120%]" />
```
Add `<div data-float …>` children only when that shot needs annotation chips.

## 5. Why It Changed?
"Me gusta el cómo usamos la imagen del hero, ¿cómo lo podemos implementar de esa
forma con diferentes imágenes?" — the treatment is now one component; any page
(e.g. `/funcionalidades`, `/info`, a future gallery) drops in `<TiltedShot
src={…} />` with its own screenshot.

## Verification
- `cd landing && pnpm build` — green, 10 pages.
- Dev server in Chrome: the home hero is visually unchanged — tilted dashboard,
  ring, layered shadow, the nav pill and "Mesa 8 · lista" chip in place; on hover
  the panel straightens/lifts and both chips straighten with it
  (`getComputedStyle` on `[data-float]` shows the ~2° hover rotation applied). No
  horizontal page overflow.
- No file outside `landing/` touched.
