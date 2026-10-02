# Report 696 — Landing: cookie consent banner gating the Meta Pixel + age/commercial-use clause in the terms

## 1. Identification
- **Report number:** 696
- **Current Task:** LANDING-COOKIE-CONSENT
- **Predecessor Task:** Report 694 — RELEASE-V0.3.8 (report 695, NOT-FOUND-REDESIGN, lives on the unmerged `feat/not-found-redesign` branch; this task is on `feat/landing-cookie-consent`, branched from `main`)

## 2. Objective
Report 403 removed the cookie banner on the premise that Plausible was the only analytics. Report 633 then added the Meta Pixel (loaded for everyone) and `privacy.s4` still claimed "cookieless, non-advertising analytics only". Restore a working consent notice that really gates the Pixel, explain everything in a modal, correct the privacy policy, and add an age / commercial-use clause to the terms.

## 3. Modified Files
- `landing/src/components/CookieBanner.tsx` — restored (from `ad0e8af5^`) and extended
- `landing/src/components/Analytics.astro`
- `landing/src/layouts/Layout.astro`
- `landing/src/i18n/ui.ts`
- `landing/src/pages/terms.astro`
- `PROGRESS.md`
- `reports/696-landing-cookie-consent-and-terms-age-clause.md` — new

## 4. What Changed?
- `CookieBanner.tsx`: two buttons, «Aceptar» and «Ver información». The second opens a native `<dialog>` (same frame/backdrop pattern as the videos modal) listing essential storage, Plausible, the Meta Pixel and how to withdraw consent, with a link to the privacy policy and its own «Aceptar». Accepting stores `ember-cookie-consent=accepted` and dispatches `ember:cookie-consent`. `localStorage` access is wrapped in try/catch. On mobile the banner sits at `bottom-20` to clear `StickyMobileCTA`.
- `Layout.astro`: mounts the banner on every page (`client:load`, language from `getLang`). The Pixel `<noscript><img>` fallback is removed: without JS the notice cannot be accepted, so it would load the Pixel without consent.
- `Analytics.astro`: the Pixel snippet is wrapped in `loadPixel()`, called immediately only if consent is already stored, otherwise on the `ember:cookie-consent` event. Plausible (cookieless) still loads unconditionally. `gracias.astro` and `WHATSAPP_TRACK_ATTR` already guard on `window.fbq`, so Lead events fire only for consenting visitors.
- `ui.ts` (ES + EN, parity 496/496): `cookie.*` keys; `privacy.s4` now discloses the Pixel (`_fbp` cookie, data sent, purpose, consent, withdrawal); new `terms.s9` «Edad y uso comercial» (B2B only, 18+, no consumer/household use, no knowing collection from minors, diner name only under the restaurant's/adult's responsibility, right to suspend); Contact moves to `terms.s10`; `privacy.date`/`terms.date` set to 2 Oct 2026.
- `terms.astro`: section count 9 → 10 (the `/en` page reuses it).

## 5. Why It Changed?
- The privacy policy contradicted what the site did (Pixel for all visitors, no notice). A banner that only hid itself (r403's objection) is fixed here by making «Aceptar» actually gate the Pixel.
- Trade-off accepted: visitors who ignore the banner are not tracked, so Facebook campaign attribution undercounts them.
- The age / commercial clause states the B2B scope in writing, which keeps COPPA-style and consumer-protection regimes out of scope.

## Verification
- `cd landing && pnpm run build` — clean, 28 pages.
- `astro preview` + browser: before accepting, no `fbq` and no `fbevents` script; «Ver información» opens the modal; «Aceptar» closes it, hides the banner, stores `accepted` and loads `fbq`.
- Not done: reload with consent already stored, `/en` rendering, and the Pixel against Meta's Test Events tool.
- Pending: lawyer review of the legal copy (see r602).
