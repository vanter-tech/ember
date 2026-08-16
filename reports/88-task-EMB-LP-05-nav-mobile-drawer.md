# Report 88

## Identification
- **Report Number:** 88
- **Task ID:** EMB-LP-05
- **Predecessor Task:** EMB-LP-04 (report 87)

## Objective
Add site navigation to the `landing` package: a desktop `Nav.astro` bar (wordmark, in-page anchors, auth CTAs) plus a `MobileNavDrawer.tsx` React island for the hamburger drawer on mobile.

## Modified Files
- `landing/src/lib/constants.ts` (new)
- `landing/src/components/Nav.astro` (new)
- `landing/src/components/MobileNavDrawer.tsx` (new)
- `landing/src/pages/index.astro`

## What Changed?
- `constants.ts` exports `FRONTEND_URL` (`PUBLIC_FRONTEND_URL` env var, falls back to `http://localhost:5173`, the frontend app's dev port) and `NAV_LINKS` (`#features`, `#pricing`).
- `Nav.astro` renders a sticky header: wordmark linking to `/`, desktop anchor nav (`hidden md:flex`) built from `NAV_LINKS`, desktop "Iniciar sesión"/"Registrarme" CTAs (`hidden md:flex`) pointing at `${FRONTEND_URL}/login` and `/register`, and mounts `<MobileNavDrawer client:load />` inside a `md:hidden` wrapper.
- `MobileNavDrawer.tsx` is a self-contained React island: a hamburger trigger button plus a right-side slide-in drawer (overlay + panel) holding the same anchors and CTAs. Closes on overlay click, close button, Escape key, or link click; locks `body` scroll while open.
- `index.astro` now renders `<Nav />` above the existing placeholder `<h1>` (placeholder content untouched — Hero is EMB-LP-06's job).

## Why It Changed?
Backlog item EMB-LP-05 requires the nav bar and mobile drawer. `FRONTEND_URL` is centralized in one constants module (rather than hardcoded per-component) since Hero/Pricing will also need to link out to `frontend` in later tasks (EMB-LP-06/08). The drawer is a React island (not an Astro component) because it needs client-side open/close state, per the spec's `@astrojs/react` islands-only convention.

## Verification
`cd landing && pnpm astro build` — passed, 1 page built, `sitemap-index.xml` regenerated. `dist/` removed post-verify.
