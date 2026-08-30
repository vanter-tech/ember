# Report 309 — landing: "Ember vs. lo de siempre" + honest-guarantees sections

## 1. Identification
- **Report number:** 309
- **Current Task:** task-L1 — landing content gaps (priority round 1)
- **Predecessor Task:** report 308 (landing-real-brand-assets)
- **Branch:** `feat/hpd-14-monitoring` (ad-hoc landing work, not an HPD queue task — same track as reports 287–308)

## 2. Objective
Close the two highest-priority content gaps found in the landing review: (a) no
differentiation vs. the status quo, (b) no trust/social-proof block. Ember has no
real customers yet, so instead of fabricated testimonials/logos the trust block
states only verifiable guarantees. Hero and the nav were explicitly left untouched
at the maintainer's request.

## 3. Modified Files
- `landing/src/components/Compare.astro` (new) — "Ember vs. lo de siempre" comparison
- `landing/src/components/Trust.astro` (new) — honest-guarantees section
- `landing/src/i18n/ui.ts` — 33 new keys × 2 locales (`cmp.*`, `trust.*`)
- `landing/src/pages/index.astro` — imports + placement of the two sections

## 4. What Changed?
- **`Compare.astro`** renders a 3-column comparison (Comanda a voz/papel · POS
  tradicional · **Ember**) across 4 rows (Toma de pedido, Dividir la cuenta, Estado
  de la cocina, Puesta en marcha). Desktop: a bordered `grid-cols-[1.2fr_1fr_1fr_1fr]`
  table with the Ember column highlighted (`bg-primary` header, `bg-primary/5` cells,
  check icon per cell). Mobile (`md:hidden`): one card per approach, Ember first.
  No competitor is named — the columns describe categories.
- **`Trust.astro`** renders eyebrow "Sin letra chica" + title "Lo que podés esperar
  de Ember" + a 5-item check grid (Free sin tarjeta / cancelás cuando quieras / datos
  aislados por restaurante / funciona con tus impresoras / sin apps para el comensal)
  + a closing line "Detrás de Ember está Vanter, desde Managua, Nicaragua." linking
  to `/contacto`.
- **`index.astro`** page order is now
  `Nav → Hero → Compare → HowItWorks → FeaturesTeaser → WhyEmber → Faq → Trust → CTASection → Footer`.
  Compare sits directly under the hero (the "why switch" hook); Trust sits right
  before the final CTA (reassurance at the decision point).
- Both components follow the existing section idiom (`border-t border-border`,
  alternating `bg-background` / `bg-muted/30`, `mx-auto max-w-6xl px-6 py-20 md:py-28`,
  `text-primary` eyebrow, shared inline check `<svg>` — no new deps). i18n keys added
  to both `es` and `en`; EN apostrophes use `’` (U+2019) per the file's convention.
  `en/index.astro` needs no change — it renders `<Page />` and each component derives
  its locale from `Astro.currentLocale`.

## 5. Why It Changed?
The landing claimed "en tiempo real" and "un solo panel" everywhere but never showed
why that beats what a restaurant does today, and had zero trust signals before the
signup CTA. `Compare` gives a concrete side-by-side of the pain removed; `Trust`
supplies believable, non-fabricated reassurance (real Free plan, no lock-in, tenant
isolation, printer compatibility) plus the "who's behind this" line. Deferred to
later tasks: hero screencast/video section, pricing context (needs business input),
and the SEO/a11y technical fixes.

## Verification
- `cd landing && pnpm run build` — green, 20 pages built, sitemap generated.
- Both sections confirmed in `dist/index.html` (es) and `dist/en/index.html` (en).
- Visual check in `astro preview` (dark theme): Compare highlights the Ember column
  and flows into "Cómo funciona"; Trust renders the 3-col check grid + closing line
  and flows into the CTA. No horizontal overflow.
