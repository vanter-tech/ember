# Report 310 — landing: hreflang alternates + og:locale / og:site_name

## 1. Identification
- **Report number:** 310
- **Current Task:** task-L3 — i18n SEO metadata (technical-details round)
- **Predecessor Task:** report 309 (landing-compare-trust-sections)
- **Branch:** `feat/hpd-14-monitoring` (ad-hoc landing work, track of reports 287–309)

## 2. Objective
The landing serves every page in two locales (ES at `/`, EN at `/en/`) but emitted
no `hreflang` alternates, so Google could not pair the two language versions or pick
the right one per user. Also missing `og:site_name` and `og:locale`. Add all of it
in one place.

## 3. Modified Files
- `landing/src/components/SEO.astro` — the only `<head>` metadata component; every
  page reaches it through `Layout.astro` (and `InfoLayout.astro`, which wraps `Layout`).

## 4. What Changed?
- Imported `switchLocalePath` from `../i18n/utils` and derived, for the current page,
  its Spanish URL (`switchLocalePath(pathname, 'es')`) and English URL
  (`switchLocalePath(pathname, 'en')`), each resolved against `Astro.site`.
- Emit three alternates on every page:
  `<link rel="alternate" hreflang="es" href="…">`,
  `hreflang="en"`, and `hreflang="x-default"` → the ES URL (default locale, no prefix).
- Added `<meta property="og:site_name" content="Ember">`,
  `<meta property="og:locale" content="es_ES|en_US">` (from `Astro.currentLocale`),
  and `<meta property="og:locale:alternate" content="…">` with the other locale.
- No per-page opt-out prop added — the 404 page also gets alternates (pointing at
  `/404/` ↔ `/en/404/`), matching its pre-existing `canonical` behavior; harmless
  since a 404 response is treated as 404 regardless of head tags.

## 5. Why It Changed?
`hreflang` is the standard signal that `/planes/` and `/en/planes/` are the same page
in different languages; without it the two versions compete and the wrong one can
rank per region. `og:site_name` / `og:locale` improve link unfurls on social and
messaging. Centralizing in `SEO.astro` covers all 20 built pages with one edit and
no per-page wiring.

## Verification
- `cd landing && pnpm run build` — green, 20 pages, sitemap generated.
- Grepped built HTML across ES/EN, root/nested/404:
  - `dist/index.html`: es→`/`, en→`/en/`, x-default→`/`; `og:locale es_ES`.
  - `dist/en/planes/index.html`: canonical `/en/planes/`; es→`/planes/`, en→`/en/planes/`,
    x-default→`/planes/`; `og:locale en_US`, alternate `es_ES`.
  - `dist/planes/`, `dist/en/`, `dist/info/manual/`, `dist/404.html` all resolve to the
    correct alternate pair.
