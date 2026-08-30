# Report 298 — landing-home-sections-bigger-hero

## 1. Identification
- **Report number:** 298
- **Task ID:** landing-home-sections-bigger-hero (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 297 (landing-info-section)

## 2. Objective
The home had shrunk to Hero → CTA → Footer. Flesh it out with teaser/new sections
and enlarge the hero (less white space, bigger content and image).

## 3. Modified Files
- `landing/src/components/Hero.astro` — larger content, no forced full-viewport
- **New** `landing/src/components/HowItWorks.astro`
- **New** `landing/src/components/FeaturesTeaser.astro`
- **New** `landing/src/components/WhyEmber.astro`
- **New** `landing/src/components/Faq.astro`
- `landing/src/pages/index.astro` — mounts the four new sections between Hero and
  `CTASection`

## 4. What Changed?
### Hero — bigger, balanced, one screen
- Headline: `text-5xl → text-[2.75rem] sm:text-6xl lg:text-7xl xl:text-[5.25rem]`
  (~84 px at `xl`), `leading-[1.02]`. Paragraph `max-w-md → max-w-lg`,
  `lg:text-[1.35rem]`. CTAs `px-6 py-3 text-sm → px-7 py-3.5 text-base`.
  `<Image>` bumped to `width={2200}`.
- **Fills the viewport again** (follow-up: the next section's "Cómo funciona" was
  peeking): section is `flex min-h-[calc(100svh-4rem)] flex-col`; the content grid
  is `flex-1 content-center` so it grows to fill and vertically centres its row,
  and the feature bar sits flush at the bottom (measured: grid 770 + bar 53 +
  `mt-8` = 855 = hero height; next section starts at the fold, its heading not
  visible).
- **Less left-heavy** (follow-up: felt "muy a la izquierda"): the image bleed was
  pulled back — `md:-mr-32 lg:-mr-52 xl:-mr-72 → md:-mr-10 lg:-mr-16 xl:-mr-24` and
  `md:w-[152%] lg:w-[176%] xl:w-[190%] → md:w-[132%] lg:w-[146%] xl:w-[156%]` — so
  the image stays inside the layout (rendered ≈ 611 px, no longer running off the
  right edge) and the two columns read as a centred pair. Grid back to plain
  `md:grid-cols-2`, `gap` `md:gap-14`.

### New home sections (all real content, no new deps)
- **`HowItWorks.astro`** — "Cómo funciona": three numbered steps (comensal se une →
  piden desde la mesa → cocina y caja en vivo) with `bg-primary` number chips.
- **`FeaturesTeaser.astro`** — "Funcionalidades": a 2-up card grid from
  `lib/features.ts` (`n`, title, `description`) plus a "Ver todas las
  funcionalidades →" link to `/funcionalidades`. Not the full detail page.
- **`WhyEmber.astro`** — "Por qué Ember": four value props with icon chips (En
  tiempo real / Sin apps para el comensal / Un solo panel / Multi-restaurante).
- **`Faq.astro`** — "Preguntas frecuentes": six native `<details>`/`<summary>`
  accordion rows (no JS), full content width (`max-w-6xl`), each row a
  `rounded-lg border overflow-hidden` card whose `<summary>` header is a
  **`bg-primary` (brand red) bar with `text-primary-foreground`** and a white `+`
  marker that rotates to `×` on `group-open`; the answer panel sits below on
  `bg-card`. Two answers carry a `/planes` or `/contacto` link.

`index.astro` order is now Nav → Hero → HowItWorks → FeaturesTeaser → WhyEmber →
Faq → CTASection → Footer.

## 5. Why It Changed?
"Sí, hacelo así" (the proposed home layout) + "hay mucho espacio en blanco en el
hero, haz el contenido más grande … en especial las imágenes." The four sub-pages
stripped the home; these teasers/new sections restore a full landing without
duplicating the routed pages, and the hero forced-viewport height was the white
space to kill.

## Verification
- `cd landing && pnpm build` — green, 10 pages.
- Dev server in Chrome: the home renders all six section headings (hero, "Tres
  pasos…", "Todo el servicio…", "Pensado para el ritmo…", "Antes de empezar",
  CTA); the FAQ rows expand/collapse and the marker rotates; the hero headline is
  84 px and the dashboard image ~810 px wide with the tilt/overlays intact. No
  horizontal page overflow.
- No file outside `landing/` touched.
