# Report 101 — EMB-LP-18: A11y/Perf Pass

## 1. Identification
- **Report:** 101
- **Task ID:** EMB-LP-18
- **Predecessor Task:** EMB-LP-17 (report 100)

## 2. Objective
Close out the EMB-LP backlog with a final accessibility/performance audit of the `landing` package: visible focus indicators, WCAG AAA contrast, alt-text coverage, and an LCP check on the hero image.

## 3. Modified Files
- `landing/src/styles/global.css`
- `landing/src/components/Footer.astro`
- `landing/src/components/ContactSection.astro`
- `landing/src/components/Pricing.astro`
- `landing/src/pages/privacy.astro`
- `landing/src/pages/terms.astro`
- `landing/src/components/Hero.astro`

## 4. What Changed?
- Added a site-wide `:focus-visible { outline: 3px solid var(--color-accent); outline-offset: 3px }` rule in `global.css`. Previously only `ContactForm.tsx`'s text inputs had an explicit focus style; every other interactive element (nav links, CTA buttons, drawer/cookie/sticky-CTA buttons) relied on unstyled browser defaults.
- Bumped several muted-text opacity utilities from `text-foreground/60`/`/70` up to `/80` (and one `opacity-70` → `opacity-80`) on `#f5f5f0` backgrounds: Footer tagline/address/copyright bar, `ContactSection` subhead, Pricing card tagline + billing period, privacy/terms "last updated" line. Manual contrast math on the theme's `#0a0a0a`/`#f5f5f0` pair put `/60` at ~5.1:1 and `/70` at ~7.3:1 — under, or too close to, the WCAG AAA 7:1 floor for normal text; `/80` lands at ~10.5:1.
- `Hero.astro`'s `<Image>` (the page's LCP element) now sets `loading="eager"` and `fetchpriority="high"`, overriding Astro's default lazy-load/async behavior for above-the-fold images.

## 5. Why It Changed?
The landing spec (`docs/superpowers/specs/2026-08-13-ember-landing-page-design.md`) hard-requires WCAG AAA contrast and visible focus indicators on every interactive element as part of the 20-point checklist. The audit found the site was already compliant on alt-text (single `<Image>` in `Hero.astro`, already had descriptive alt) and on primary text colors (foreground/background/accent combinations all exceed 8:1), but several secondary/muted-text utilities and the absence of any deliberate focus style were genuine gaps. Lazy-loading the hero image also directly hurts LCP, the primary Core Web Vital for a marketing landing page.

## System Health
`astro build` (landing package's own verification command) PASSED — 5 pages generated, `sitemap-index.xml` emitted, focus-visible rule confirmed present in built CSS output, `dist/` removed post-verify. `frontend`/`backend` untouched this task.
