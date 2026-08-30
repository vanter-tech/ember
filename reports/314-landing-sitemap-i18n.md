# Report 314 — landing: sitemap i18n alternates + 404 exclusion

## 1. Identification
- **Report number:** 314
- **Current Task:** SEO — sitemap hreflang alternates
- **Predecessor Task:** report 313 (landing-pricing-annual-toggle)
- **Branch:** `feat/hpd-14-monitoring` (ad-hoc landing work, track of reports 287–313)

## 2. Objective
`@astrojs/sitemap` was called with no options: the generated `sitemap-0.xml` listed
every URL flat, with no `<xhtml:link rel="alternate" hreflang>` pairs, and wrongly
included `/en/404/`. Add the i18n config and a 404 filter.

## 3. Modified Files
- `landing/astro.config.mjs` — `sitemap()` → `sitemap({ i18n, filter })`

## 4. What Changed?
```js
sitemap({
  i18n: { defaultLocale: 'es', locales: { es: 'es', en: 'en' } },
  filter: (page) => !page.includes('/404'),
})
```
- `i18n` makes the integration pair each `/foo/` (es, unprefixed) with its `/en/foo/`
  counterpart and emit `<xhtml:link rel="alternate" hreflang="es|en">` on both
  entries (each pair is mutually self-referencing, per the sitemap spec).
- `filter` drops any URL containing `/404` — previously `/en/404/` was listed (the
  root `404.astro` was already auto-excluded by Astro, but the `en/404.astro`
  wrapper counted as a normal page).
- No `lastmod` added: the integration cannot infer a real modification date and a
  build-time "now" on every URL is a worse signal than none.

## 5. Why It Changed?
The page `<head>` already carries `hreflang` (report 310); mirroring it in the
sitemap is the second half of the standard i18n signal and is what Search Console's
international-targeting report reads. Keeping soft-404 URLs out of the sitemap avoids
submitting pages that should never be indexed.

## Verification
- `cd landing && pnpm run build` — green, 20 pages built.
- `dist/sitemap-0.xml` (parsed with node): **18** `<url>` entries (was 20 — both
  404s gone), **all 18** carry `xhtml:link` alternates, string `404` absent.
- Spot check: `/planes/` and `/en/planes/` each list `hreflang="en"` →
  `…/en/planes/` and `hreflang="es"` → `…/planes/`.
