# Report 305 — landing-i18n

## 1. Identification
- **Report number:** 305
- **Task ID:** landing-i18n (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 304 (landing-feature-images)

## 2. Objective
Add full-site internationalization to the landing site: Spanish as the base
language (no URL prefix), English added under `/en/`. Also move the dark/light
theme toggle to the right of the "Registrarme" button in the header.

## 3. Modified Files
- `landing/astro.config.mjs` — `i18n: { locales: ['es','en'], defaultLocale: 'es', routing: { prefixDefaultLocale: false } }`
- `landing/src/i18n/ui.ts` — **new**, full ES+EN dictionary (~230 keys)
- `landing/src/i18n/utils.ts` — **new**, `getLang` / `useTranslations` / `localizePath` / `switchLocalePath` (+ re-exports `Lang`, `defaultLang`)
- `landing/src/components/LangSwitcher.astro` — **new**, ES/EN segmented control
- `landing/src/components/Nav.astro` — `t()` + `localizePath` links, theme toggle moved after the auth group, `<LangSwitcher>` added, `lang` passed to `<MobileNavDrawer>`
- `landing/src/components/Footer.astro`, `Hero.astro`, `HowItWorks.astro`, `FeaturesTeaser.astro`, `WhyEmber.astro`, `Faq.astro`, `CTASection.astro`, `FeatureCards.astro`, `PlanCards.astro`, `PricingTable.astro` — `t()` wiring
- `landing/src/components/MobileNavDrawer.tsx`, `StickyMobileCTA.tsx`, `CookieBanner.tsx` — `lang` prop + `useTranslations`
- `landing/src/layouts/Layout.astro` — `<html lang={Astro.currentLocale ?? 'es'}>`
- `landing/src/layouts/InfoLayout.astro` — `t()` + locale-aware sidebar/active-path
- `landing/src/lib/constants.ts` — `NAV_LINKS` `label` → `key`
- `landing/src/lib/features.ts` — `FEATURES` → `getFeatures(lang)`
- `landing/src/lib/plans.ts` — `PLANS`/`COMPARISON` → `getPlans(lang)` / `getComparison(lang)`
- `landing/src/pages/{index,funcionalidades,planes,contacto,privacy,terms,404}.astro`, `pages/info/{index,manual,videos}.astro` — `t()` wiring, translated `<Layout>` title/description
- `landing/src/pages/en/**` — **new** (10 files): thin wrappers re-rendering the ES page component; `Astro.currentLocale` resolves to `'en'` from the `/en/` URL

## 4. What Changed?
**i18n foundation.** Astro's built-in `i18n` config; `prefixDefaultLocale: false`
keeps `/` Spanish and puts English at `/en/`. `ui.ts` holds a flat
`Record<Lang, Record<string,string>>` with Spanish values copied verbatim from the
old hardcoded components plus English translations for every string on the site
(nav, hero, home sections, FAQ, CTA, footer, features, plans, pricing table,
contact, info/manual/videos, privacy, terms, 404, SEO titles). `utils.ts`:
`getLang(Astro.currentLocale)` → `'es' | 'en'`; `useTranslations(lang)` → `t(key)`
with fallback to `defaultLang` then the raw key; `localizePath(path, lang)`
prefixes `/en` for internal links; `switchLocalePath(pathname, target)` maps the
current URL to its counterpart for the language switch.

**Components/pages.** Every `.astro` component computes
`const t = useTranslations(getLang(Astro.currentLocale))` and renders `t('...')`;
internal `href`s go through `localizePath`. The data files (`features.ts`,
`plans.ts`) became locale factory functions so the arrays stay ergonomic while the
strings translate. The three React islands take a `lang` prop (serialized into the
`astro-island` element per locale) and use the same `useTranslations`.

**Routing.** Rather than duplicate ~400 lines of page bodies, each
`src/pages/en/<name>.astro` is a 3-line wrapper that imports and renders the
Spanish page as a component. Astro computes `Astro.currentLocale` from the request
URL, so the shared component tree renders English under `/en/`. Build emits 20
pages (10 ES + 10 EN).

**Theme toggle.** `<button id="theme-toggle">` moved from before the auth `<div>`
to after it, with the `w-px` divider repositioned; `<LangSwitcher>` sits to its
right (desktop). Order is now: nav links, auth (Iniciar sesion | Registrarme), theme toggle, ES/EN switcher.

## 5. Why It Changed?
Maintainer: *"El boton del modo oscuro y claro debe de ir a la derecha del boton
de registrame y de paso quiero agregar idiomas, espanol base y despues ingles"* —
scope confirmed as *"Todo el sitio"* (whole site, including `/info/*` and the legal
pages as reference translations). Placeholder company data (Vanter S.A. / Quito
address / RUC / email) is left as-is in both languages — the maintainer flagged it
will be replaced with real Nicaragua info in a later pass, so Ecuador legal
accuracy is not a concern now.

## Verification
- `cd landing && pnpm build` — green, **20 pages** (10 ES + 10 EN), sitemap, image opt OK.
- Built HTML greps: no raw `key.path` leaks anywhere; `dist/en/**` fully English;
  `<html lang="en">` on EN, `"es"` on ES; EN nav links all `/en/`-prefixed, no bare
  ES internal links; `astro-island` `props` carry `lang:"en"` / `lang:"es"` per
  locale for `CookieBanner` / `MobileNavDrawer`.
- Preview server + browser: ES home renders, theme toggle sits right of
  "Registrarme" with the ES/EN switcher after it, cookie banner in Spanish;
  clicking **EN** navigates to `/en/`, `document.documentElement.lang === 'en'`,
  h1 = "Your restaurant, in sync", nav = Features/Pricing/Info/Contact → `/en/*`,
  button = "Sign up", LangSwitcher shows EN active / ES → `/`.
- `switchLocalePath` verified both directions (`/` ↔ `/en/`, `/planes/` ↔ `/en/planes/`).
- No file outside `landing/` touched → backend/frontend suites not re-run.
