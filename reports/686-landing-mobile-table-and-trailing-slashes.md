# Report 686 — LANDING-MOBILE-TABLE-AND-TRAILING-SLASHES

## 1. Identification
- **Report number:** 686
- **Task ID:** LANDING-MOBILE-TABLE-AND-TRAILING-SLASHES (ad hoc, live bug report from DevTools iPhone view)
- **Predecessor task:** report 685 — LANDING-WHATSAPP-BUTTON

## 2. Objective
Fix two reported problems on the landing at phone width: (1) the pricing comparison table showed only the titles and nothing when scrolled right; (2) navigation felt slow and "stuck", with `ClientRouter` fetches taking up to ~1.2 s.

## 3. Modified Files
- `landing/src/components/PricingTable.astro`
- `landing/src/i18n/utils.ts`
- `PROGRESS.md`

## 4. What Changed?
- **Table:** the first column is `sticky` and was `w-[44%]` of a `min-w-[760px]` table, i.e. 334 px. On a phone the scroll container is ~342 px, so the pinned column covered almost the whole viewport and the plan columns slid under it (measured on the live page: Free at 134–245 px and Starter at 245–327 px were both hidden behind the 0–334 px label column). The label column is now 8.5 rem wide on mobile (`md:` keeps the 44%).
- **Navigation:** `localizePath` now returns paths with a trailing slash (`withTrailingSlash`, files with an extension and anchors untouched). Every internal link goes through it, so after the build no slashless internal page link remains in `dist` (checked with grep).

## 5. Why It Changed?
- Table: root cause measured on production (see above); a simulation of the new 136 px column on the live page showed Free fully visible at scroll 0 and Enterprise fully visible at the end.
- Navigation: Cloudflare answers `307` to `/x/` for every page served as `x/index.html`. Links without the slash made each click two round trips (curl: ~0.3-0.5 s per hop from here, worse on a phone from Nicaragua), and `ClientRouter` fetched through the redirect (observed `/contacto` then `/contacto/` on the live site).

### Verification
- `pnpm run build` (landing): exit 0, 28 pages; no slashless internal links in the generated HTML.

### NOT verified / not fixed
- The flicker ("blink") and the `cancelled` fetch status were NOT isolated. Hypothesis (unproven): `ClientRouter` aborts the in-flight navigation when a second tap arrives because the page looked stuck; the redirect removal may already fix it. Re-measure in DevTools after this deploys; if the blink remains, investigate the `ClientRouter` swap/animation separately.
- The table was not viewed on a real phone or in DevTools emulation (window resize does not work in the automation browser); only measured by simulating the container width. The live site keeps the old behavior until this reaches `main`.
