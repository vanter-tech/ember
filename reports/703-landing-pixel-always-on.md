# Report 703 — Meta Pixel always loaded; cookie notice becomes informational (landing)

## 1. Identification
- **Report number:** 703
- **Current Task:** LANDING-PIXEL-ALWAYS-ON
- **Predecessor Task:** Report 702 — FIX-FLAKY-NUMBERING-TEST

## 2. Objective
The Facebook campaign (1900 C$, 2-16 Oct 2026) needs every `Lead` the Pixel can see; with the cookie notice gating the Pixel, only visitors who pressed "Aceptar" were counted. The Pixel now loads on every visit and the notice only informs.

## 3. Modified Files
- `landing/src/components/Analytics.astro`
- `landing/src/components/CookieBanner.tsx`
- `landing/src/i18n/ui.ts`
- `PROGRESS.md`
- `reports/703-landing-pixel-always-on.md` — new

## 4. What Changed?
- `Analytics.astro`: `loadPixel()` runs immediately; the `localStorage` check and the `ember:cookie-consent` listener are gone.
- `CookieBanner.tsx`: the accept handler no longer dispatches the consent event (`CONSENT_EVENT` export removed); it only remembers that the notice was seen. The button label is now "Entendido" / "Got it".
- `ui.ts` (es and en): banner, modal and privacy section 4 say the Meta (Facebook) Pixel **always loads** and what it sends (IP, browser, pages, form submit and WhatsApp tap), and give ways to avoid it (browser privacy settings, tracker blocker, turning off personalized ads, deleting `_fbp`). The "only if you accept / if you do not accept it is not loaded / withdraw consent" wording was removed because it would now be false.

## 5. Why It Changed?
- The user decided the campaign needs the full conversion signal at the start. Plausible (cookieless) was already always on.
- Decision recorded: the banner keeps **naming Meta's Pixel** and what it collects. A request to replace it with a generic "a tracker" was declined: hiding who receives the data misleads visitors and Meta's business-tools terms require clear notice, which risks the ad account of a new, small campaign. The change keeps the Pixel on without making any statement untrue.
- Not a legal opinion: no consent is requested any more. If the site starts targeting regions that require opt-in consent for advertising cookies (the `/en/` pages are public), revisit this.

## Verification
- `cd landing && pnpm run build`: 28 pages built, no errors; the built `index.html` calls `loadPixel()` directly and has no `ember-cookie-consent` reference.
- Not done: no browser check of the live page; verify in Meta Test Events (Pixel `1853493642493263`) after deploy that a first visit with no click on the banner already shows `PageView`, and that `/gracias/` and the WhatsApp button still send `Lead`.
