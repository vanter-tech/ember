# Report 311 — landing: JSON-LD structured data (home)

## 1. Identification
- **Report number:** 311
- **Current Task:** task-L4 — structured data (technical-details round)
- **Predecessor Task:** report 310 (landing-hreflang-og-locale)
- **Branch:** `feat/hpd-14-monitoring` (ad-hoc landing work, track of reports 287–310)

## 2. Objective
The landing had no machine-readable structured data — no `Organization`, no product
entity, and the visible FAQ section produced no `FAQPage` markup. Add a single
JSON-LD graph on the home page in both locales.

## 3. Modified Files
- `landing/src/components/StructuredData.astro` — new; builds and emits the graph.
- `landing/src/pages/index.astro` — import + render `<StructuredData />` once inside
  `<Layout>` (home renders for both `/` and `/en/` via the `en/index.astro` wrapper).

## 4. What Changed?
`StructuredData.astro` emits one `<script type="application/ld+json" is:inline>` with
`@context: https://schema.org` and an `@graph` of four nodes:
- **Organization** (`@id …/#organization`) — `Vanter`, `url`, `logo` `/icon-512.png`,
  `email`, `PostalAddress` Managua / `NI`.
- **WebSite** (`@id …/#website`) — name `Ember`, `inLanguage`, `publisher` → the org.
- **SoftwareApplication** (`@id …/#software`) — `Ember`, `applicationCategory:
  BusinessApplication`, `operatingSystem: Web`, locale-aware `url`, `description`
  from `seo.home.desc`, `publisher` → the org, and a single **free `Offer`** (`price
  "0"`, `USD`, description from `plan.free.tagline`). The paid tiers are deliberately
  omitted from structured data while pricing is still being decided.
- **FAQPage** (`@id …/#faq`) — `mainEntity` built from the same `faq.1..6.q` /
  `faq.1..6.a` i18n keys `Faq.astro` renders, so the markup always matches the
  visible Q&A.

Locale comes from `Astro.currentLocale`: the ES graph renders at `/` (`inLanguage
es`, `software.url …/`), the EN graph at `/en/` (`inLanguage en`, `software.url
…/en/`). `Organization` / `WebSite` `@id`s are shared across locales (same entity);
`SoftwareApplication` / `FAQPage` differ by URL. All `<` in the serialized JSON are
escaped to `<` to prevent any `</script>` break-out.

## 5. Why It Changed?
`Organization` + `logo` is what Google uses for the knowledge-panel / site name;
`SoftwareApplication` classifies the product; `FAQPage` can earn expandable Q&A in
search results and matches content already on the page. Home-only keeps it simple —
that is the only page with both the product pitch and the FAQ — and avoids emitting
`FAQPage` where no FAQ exists.

## Verification
- `cd landing && pnpm run build` — green, 20 pages.
- `node` parse of the emitted script in `dist/index.html` and `dist/en/index.html`:
  valid JSON; `@graph` types `Organization, WebSite, SoftwareApplication, FAQPage`;
  `FAQPage.mainEntity.length === 6`; `inLanguage` = `es` / `en` respectively;
  `SoftwareApplication.url` = `…/` and `…/en/`; question text localized.
- `grep` confirms the JSON-LD block appears on exactly the two home pages, once each,
  and nowhere else in `dist/`.
