# Report 648 — FIX-LANDING-NAV-CSS-FLASH

## 1. Identification
- Report: 648
- Task ID: FIX-LANDING-NAV-CSS-FLASH
- Predecessor: 647 (SETTINGS-PLAN-TAB-SUBSCRIPTION); last landing task: 633 (LANDING-META-PIXEL)

## 2. Objective
Stop the ~1s loss of CSS (flash of unstyled page) when navigating between landing views.

## 3. Modified Files
- `landing/src/components/Analytics.astro`
- `landing/src/layouts/Layout.astro`

## 4. What Changed?
The Meta Pixel `<noscript><img …></noscript>` moved from the `<head>` (Analytics.astro) to the top of `<body>` (Layout.astro), still PROD-only.

## 5. Why It Changed?
Astro's `ClientRouter` parses the destination page with `DOMParser` (scripting disabled), which parses `<noscript>` content as HTML. An `<img>` is invalid in `<head>`, so the parser closed the head at the pixel's noscript and the `Footer.css` `<link>` plus page `<style>` tags ended up in the parsed body. The router then found no stylesheet in the new head, removed the current `<link>`, and re-inserted it unloaded, leaving the page unstyled until the CSS reloaded (visible on slow networks). Introduced by commit 8c15fa44. Verified before/after in the browser on the built preview: before, new head had 0 stylesheets and body background was transparent after swap; after, the same `<link>` node persists, 3 stylesheets and the themed background stay applied. `pnpm run build` clean (28 pages).
