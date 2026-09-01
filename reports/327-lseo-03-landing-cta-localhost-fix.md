# Report 327 — LSEO-03: landing login/register CTAs point to localhost in prod

## 1. Identification
- **Report number:** 327
- **Task ID:** LSEO-03 (Landing — SEO & customer acquisition backlog)
- **Predecessor task:** report 326 (fix-ws-url-scheme-sockjs)

## 2. Objective
User report: on the deployed landing (`ember.vanter.net`), the "Iniciar sesión" /
"Registrarme" buttons navigate to `localhost` instead of `app.ember.vanter.net`.

## 3. Modified Files
- `landing/src/lib/constants.ts`
- `deploy/RUNBOOK.md`

## 4. What Changed?
- **`landing/src/lib/constants.ts`** — `FRONTEND_URL` previously resolved to
  `import.meta.env.PUBLIC_FRONTEND_URL ?? 'http://localhost:5173'`. The Cloudflare
  `ember` Worker builds `landing/` without `PUBLIC_FRONTEND_URL`, so every CTA
  (`Nav.astro`, `Hero.astro`, `CTASection.astro`, `MobileNavDrawer.tsx`,
  `StickyMobileCTA.tsx`, `lib/plans.ts`) shipped the `localhost:5173` fallback.
  The fallback is now environment-aware: `import.meta.env.PROD` →
  `https://app.ember.vanter.net`, dev → `http://localhost:5173`. An explicit
  `PUBLIC_FRONTEND_URL` still overrides both. The existing PROD-without-var
  `console.warn` now names the actual fallback URL it will use.
- **`deploy/RUNBOOK.md`** — HPD-18 section documents the `PUBLIC_FRONTEND_URL`
  build variable for the `ember` Worker and notes the new safe PROD fallback.

## 5. Why It Changed?
`frontend/src/lib/api.ts` and `platformApi.ts` already follow the pattern of a
production-sensible hardcoded fallback rather than relying solely on a build-time
env var. Mirroring that here means a landing deploy from `main` can never ship
localhost links again, regardless of the Cloudflare project's build-variable
config. Setting `PUBLIC_FRONTEND_URL` is now only needed to target a non-default
SPA origin (staging, preview).

## 6. Verification
- `cd landing && pnpm run build` — 20 pages built, no errors.
- `grep -rn "localhost:5173" landing/dist` — no matches.
- CTAs in `dist/index.html` and `dist/planes/index.html` resolve to
  `https://app.ember.vanter.net/login` and `https://app.ember.vanter.net/register`.
