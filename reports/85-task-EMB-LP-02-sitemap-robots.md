# Report 85 — EMB-LP-02

## Identification
- **Report:** 85
- **Task ID:** EMB-LP-02
- **Predecessor Task:** EMB-LP-01 (report 84)

## Objective
Add `@astrojs/sitemap` integration and a static `public/robots.txt` (allow-all, referencing the sitemap) to the standalone `landing/` Astro package.

## Modified Files
- `landing/astro.config.mjs`
- `landing/package.json`
- `landing/pnpm-lock.yaml`
- `landing/public/robots.txt` (new)

## What Changed?
- Ran `pnpm astro add sitemap`, which added `@astrojs/sitemap@^3.7.3` to `package.json`/`pnpm-lock.yaml` and wired `sitemap()` into `astro.config.mjs`'s `integrations` array.
- Added `site: 'https://ember.vanter.com'` to `astro.config.mjs` — required by `@astrojs/sitemap` to emit absolute URLs; this is the landing/marketing host, distinct from tenant subdomains (`slug.ember.vanter.com`) served by the main app.
- Created `landing/public/robots.txt`: `User-agent: * / Allow: /` plus a `Sitemap:` line pointing at `https://ember.vanter.com/sitemap-index.xml` (the index file `@astrojs/sitemap` actually emits, not a bare `sitemap.xml`).

## Why It Changed?
Checklist items #7/#8 from the landing page spec (`docs/superpowers/specs/2026-08-13-ember-landing-page-design.md`) require crawlable, indexable output before the site goes live: an auto-generated sitemap for search engines and a `robots.txt` that both permits indexing and points crawlers at it. `astro build` was verified to emit `dist/robots.txt`, `dist/sitemap-index.xml`, and `dist/sitemap-0.xml` (containing the `https://ember.vanter.com/` entry); `dist/` was removed afterward per this package's existing verify-then-discard convention (already gitignored).
