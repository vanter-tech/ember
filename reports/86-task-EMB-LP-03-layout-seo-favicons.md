# Report 86 — EMB-LP-03

## Identification
- **Report:** 86
- **Task ID:** EMB-LP-03
- **Predecessor Task:** EMB-LP-02 (report 85)

## Objective
Build `src/layouts/Layout.astro` + `src/components/SEO.astro` for per-page `<title>`/`<meta description>`, OG/Twitter tags, and complete the favicon set in `landing/`.

## Modified Files
- `landing/src/layouts/Layout.astro` (new)
- `landing/src/components/SEO.astro` (new)
- `landing/src/pages/index.astro`
- `landing/public/apple-touch-icon.png` (new)
- `landing/public/site.webmanifest` (new)
- `landing/public/og-image.png` (new)

## What Changed?
- `SEO.astro`: takes `title`/`description`/optional `image` props, emits `<title>`, meta description, a canonical link (built from `Astro.url.pathname` + `Astro.site`), and OG (`og:type/url/title/description/image`) + Twitter (`summary_large_image` card) tags. `image` defaults to `/og-image.png` and is resolved to an absolute URL via `Astro.site`.
- `Layout.astro`: new shared shell — imports `global.css`, sets `<html lang="es">`, wires the favicon set (`favicon.svg`, `favicon.ico`, `apple-touch-icon.png`, `site.webmanifest`) in `<head>`, renders `<SEO>` with the caller's `title`/`description`/`image` props, and exposes a `<slot />` for page content.
- `src/pages/index.astro` now imports `Layout` instead of hand-rolling its own `<html>`/`<head>`, passing a real title/description (`Ember — Restaurant Management Platform` / a one-line product summary) — the placeholder `<h1>Astro</h1>` body markup is untouched (Hero/Nav/etc. are EMB-LP-05+ work).
- `public/apple-touch-icon.png` (180×180) and `public/og-image.png` (1200×630) are solid-color placeholder PNGs (brand red `#8c1717` and near-black respectively, generated programmatically — no image tooling was available on this machine), completing the favicon set and giving the OG/Twitter meta tags a real file to resolve to. `public/site.webmanifest` references `favicon.svg`/`apple-touch-icon.png` and sets `theme_color`/`background_color` from the spec's brutalist palette.

## Why It Changed?
Checklist items #3/#4/#5/#6 from the landing page spec require unique per-page meta title/description, an Open Graph image, and a complete favicon set (`favicon.ico`, `.svg`, `apple-touch-icon.png`, `site.webmanifest`) before launch. `astro build` was verified to render all expected `<head>` tags (title, description, canonical, OG, Twitter, favicon links) with correct absolute URLs off `site: 'https://ember.vanter.com'` (set in EMB-LP-02), and to copy all five new/existing `public/` assets into `dist/`. The `apple-touch-icon.png`/`og-image.png` placeholders exist only to satisfy the checklist's "a real file must resolve" requirement — final branded artwork is not yet defined and should land once EMB-LP-04's brutalist theme tokens (and any dedicated brand-asset work) are in place; note this in `PROGRESS.md` so it isn't mistaken for finished art.
