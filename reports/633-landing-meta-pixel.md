# Report 633 — LANDING-META-PIXEL

## 1. Identification
- **Report:** 633
- **Task ID:** LANDING-META-PIXEL (ad hoc, Facebook Ads campaign support)
- **Predecessor Task:** report 632 — KDS-FOCUSED-CARD-HEADER-RESPONSIVE

## 2. Objective
Install the Meta (Facebook) Pixel on the landing site so the upcoming Facebook Ads campaign (carousel + video) can be attributed: track page views campaign-wide and a `Lead` conversion event when a visitor submits the contact/demo-request form.

## 3. Modified Files
- `landing/src/components/Analytics.astro`
- `landing/src/pages/gracias.astro`

## 4. What Changed?
- `Analytics.astro`: added the standard Meta Pixel base snippet (init + `PageView` + `<noscript>` fallback pixel image) alongside the existing Plausible script, both gated behind `import.meta.env.PROD` (unchanged pattern — doesn't load in dev/preview). Pixel ID `1853493642493263` hardcoded, same treatment as Plausible's hardcoded domain, since the ID is not a secret (it ships in public HTML regardless).
- `gracias.astro`: added an inline script calling `window.fbq('track', 'Lead')`, guarded by a `typeof window.fbq === 'function'` check. `ContactForm.tsx` already redirects here (`window.location.href = localizePath('/gracias', lang)`) only after a successful `POST /api/contact`, so landing on this page is itself the existing "success" signal — no changes needed to `ContactForm.tsx`. `en/gracias.astro` re-exports this same file, so both locales are covered by the one edit.

## 5. Why It Changed?
User is running a Facebook Ads campaign (~1000 NIO) for Ember and needs to know how many ad clicks actually turned into a demo request, not just how many clicks the ads got. Without a Lead conversion event, Meta Ads Manager can only report clicks/impressions — insufficient to judge a small-budget campaign's real return.

## Verification
- `cd landing && pnpm run build` → clean, 28 pages built.
- Confirmed in the built output: `dist/index.html` contains `fbq('init', '1853493642493263')`; `dist/gracias/index.html` contains `fbq('track', 'Lead')`.
- Not live-tested against Meta's Pixel Helper / Events Manager test-events tool (requires the deployed site + a real form submission) — recommend the user verify via Meta Events Manager's "Test Events" tab after this deploys.
