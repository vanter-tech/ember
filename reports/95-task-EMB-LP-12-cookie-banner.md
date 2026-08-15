# Report 95 — EMB-LP-12: CookieBanner island

**Predecessor Task:** EMB-LP-11 (report 94)

## Objective
Add a non-intrusive brutalist cookie-consent banner for privacy compliance.

## Modified Files
- `landing/src/components/CookieBanner.tsx` (new)

## What Changed?
Added `CookieBanner.tsx`, a React island following the same hooks pattern as `MobileNavDrawer.tsx`/`StickyMobileCTA.tsx`. On mount it checks `localStorage['ember-cookie-consent']`; if not `'accepted'`, it renders a fixed bottom-left brutalist panel (`border-[3px]`/`shadow-brutal`) with a short notice, a link to `/privacy`, and an "Aceptar" button. Accepting writes the flag to `localStorage` and hides the banner permanently (no re-prompt on future visits). No granular cookie-category toggles — the site has zero tracking cookies by default (EMB-LP-17 is a privacy-first analytics script with no cookies), so this is a simple acknowledgment banner, not a preference center.

## Why It Changed?
Implements EMB-LP-12 from the landing-page spec (checklist item #17): a "non-intrusive brutalist Cookie Banner component for cookie/privacy compliance."

## System Health
`landing`'s `astro build` PASSING (`dist/` removed post-verify). Component is standalone, NOT yet wired into `index.astro` (EMB-LP-13's job, same as Footer/StickyMobileCTA). `frontend`/`backend` untouched this task.
