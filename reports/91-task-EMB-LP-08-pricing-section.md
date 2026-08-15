# Report 91 — EMB-LP-08

## 1. Identification
- **Report:** 91
- **Task ID:** EMB-LP-08
- **Predecessor Task:** EMB-LP-07 (report 90)

## 2. Objective
Add the `Pricing.astro` section to the standalone `landing` package: four static plan cards matching backend `RestaurantPlan` (FREE/STARTER/PRO/ENTERPRISE), with the ENTERPRISE card routing to a `mailto:` CTA.

## 3. Modified Files
- `landing/src/components/Pricing.astro` (new)

## 4. What Changed?
Added a brutalist `id="pricing"` section (matches the `#pricing` anchor already used by `Hero.astro`'s secondary CTA and `Nav.astro`'s `NAV_LINKS`), styled consistently with `Features.astro`: mono kicker + oversized `<h2>`, 4-card grid (`sm:grid-cols-2 lg:grid-cols-4`, `border-[3px]`). Each card (FREE/STARTER/PRO/ENTERPRISE) carries a price, tagline, mono feature-bullet list, and CTA button. The PRO card is visually inverted (`bg-foreground text-background shadow-brutal`) with a "Más popular" mono label to differentiate it from the other three (`shadow-brutal-sm`). FREE/STARTER/PRO CTAs point to `${FRONTEND_URL}/register`; ENTERPRISE's CTA ("Hablar con ventas") is a `mailto:ventas@ember.vanter.com` link, since no contact form/thank-you page exists yet (that's EMB-LP-16). Component is standalone, not yet wired into `index.astro` (EMB-LP-13's job).

## 5. Why It Changed?
Backlog item EMB-LP-08 per `PROGRESS.md`/the landing-page spec (`docs/superpowers/specs/2026-08-13-ember-landing-page-design.md`, checklist item "Pricing: four static cards matching `RestaurantPlan`, ENTERPRISE CTA → mailto/contact form → thank-you redirect"). Plan names/order sourced directly from `backend/src/main/java/com/vanter/ember/restaurant/model/RestaurantPlan.java` (`FREE, STARTER, PRO, ENTERPRISE`); pricing copy/features are marketing placeholders (no price data exists in the backend) to be swapped for real figures before launch.

## Verification
`cd landing && pnpm astro build` — PASSED (1 page built, `dist/` removed post-verify).
