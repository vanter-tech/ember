# Report 100 — EMB-LP-17

## Identification
- **Report Number:** 100
- **Task ID:** EMB-LP-17
- **Predecessor Task:** EMB-LP-16 (report 99)

## Objective
Wire a privacy-first analytics script into `ember/landing/`'s page `<head>`, with zero tracking cookies by default, satisfying checklist item #18.

## Modified Files
- `landing/src/components/Analytics.astro` (new)
- `landing/src/layouts/Layout.astro`

## What Changed?
- `Analytics.astro`: renders a single Plausible Analytics script tag (`defer`, `data-domain="ember.vanter.com"`, `src="https://plausible.io/js/script.js"`), gated behind `import.meta.env.PROD` so `astro dev` never fires pings. No npm dependency added — it's a static script tag, matching Plausible's cookieless, no-consent-banner-required design.
- `Layout.astro`: imports and mounts `<Analytics />` in `<head>` right after `<SEO />`, so every page (including the new `thank-you.astro`) picks it up with no per-page change.

## Why It Changed?
The spec calls for a privacy-first analytics integration (Vercel Analytics/Plausible/Cloudflare) with zero tracking cookies. Plausible was chosen because its embed needs no API key or account credential in the script tag itself (unlike Vercel Analytics, which needs the `@vercel/analytics` package, or Cloudflare, which needs a site token) — it only needs an actual Plausible account for `ember.vanter.com` provisioned before launch to start receiving data, same "placeholder until launch" caveat already accepted for the OG image/favicons (EMB-LP-03). Domain literal matches the existing `site: 'https://ember.vanter.com'` in `astro.config.mjs`.

## Verification
`cd landing && pnpm run build` — PASSING (5 pages: index, 404, privacy, terms, thank-you). Confirmed the script tag with `data-domain="ember.vanter.com"` is present in `dist/index.html`. `dist/` removed post-verify.
