# Report 403 — Landing: remove the cookie consent banner

## 1. Identification
- **Report number:** 403
- **Current Task:** Landing nice-to-have #1 — remove `CookieBanner`
- **Predecessor Task:** Report 402 — Landing: dedicated Ember Local page

## 2. Objective
The cookie banner only had an "Aceptar" button that hid itself; it gated
nothing. The site's only analytics (Plausible) is cookieless and already
loads only in production. The banner implied a choice that did not exist,
so it is removed.

## 3. Modified Files
- `landing/src/components/CookieBanner.tsx` — deleted
- `landing/src/pages/index.astro`
- `landing/src/i18n/ui.ts`
- `PROGRESS.md`
- `reports/403-landing-remove-cookie-banner.md` — new

## 4. What Changed?
- Deleted the `CookieBanner` React island (its only consumer was the home
  page; `Layout` never mounted it, so no other route showed it).
- `index.astro`: removed the `CookieBanner` import and the
  `<CookieBanner lang={lang} client:load />` mount.
- `i18n/ui.ts`: removed the `// --- Cookie banner ---` block in both locales
  (`cookie.text`, `cookie.link`, `cookie.accept`). ES/EN parity 403/403.
- The privacy policy §4 ("Cookies") is unchanged — it already states the
  site uses only strictly necessary cookies and a cookieless analytics
  tool, and that cookies are managed from the browser.

## 5. Why It Changed?
- `Analytics.astro` loads `plausible.io/js/script.js` (no-cookie script)
  only when `import.meta.env.PROD`. No cookies are set that need consent
  under GDPR/ePrivacy, so a consent gate is not required.
- The banner's "Aceptar" only wrote `localStorage['ember-cookie-consent']`
  and hid the element — it never enabled or disabled anything. Keeping a
  non-functional consent UI is worse than none.
- A real consent flow (with a working "Rechazar" that suppresses analytics)
  was the alternative; it was rejected as unnecessary for a cookieless
  setup.

## Verification
- `cd landing && pnpm run build` — clean, 22 pages (unchanged).
- `grep -rni cookie landing/src` — only the privacy §4 copy and
  `youtube-nocookie` embed URLs remain; no component or key references.
- ES/EN i18n key parity: 403 / 403.
