# Report 93 — EMB-LP-10: Footer.astro

## 1. Identification
- **Report number:** 93
- **Task ID:** EMB-LP-10
- **Predecessor Task:** EMB-LP-09

## 2. Objective
Add the landing page footer: wordmark, copyright notice, physical address/business entity block, and links to Privacy Policy, Terms of Service, and Contact.

## 3. Modified Files
- `landing/src/components/Footer.astro` (new)

## 4. What Changed?
Created a standalone `Footer.astro` component with a `border-t-[3px] border-foreground` brutalist band containing three columns: wordmark + one-line tagline, a "Legal" link list (`/privacy`, `/terms`, `mailto:ventas@ember.vanter.com`), and a "Vanter S.A." address block (street address, city/province/country, RUC). A bottom bar shows a dynamic-year copyright line. Styling reuses the existing mono/uppercase/tracking-widest conventions from `Nav.astro`/`CTASection.astro`. The component is not yet wired into `index.astro` (EMB-LP-13's job), matching the pattern of EMB-LP-07/08/09.

## 5. Why It Changed?
Fulfills checklist item #19 (real contact address in the footer) and the spec's Footer section (wordmark, copyright, address/business entity, Privacy/Terms/Contact links).

## Notes / Caveats
- The address and RUC are **placeholder values**, not verified real business registration details — same caveat as EMB-LP-03/06's placeholder image assets. Swap for the real registered entity before launch.
- `/privacy` and `/terms` routes don't exist yet (land in EMB-LP-15) — links are dead until then, same standalone-component pattern as prior tasks.
- Contact reuses `ventas@ember.vanter.com` (already used by `Pricing.astro`'s ENTERPRISE CTA) rather than introducing a new address.

## System Health
- `landing`: `astro build` — PASSING (`dist/` removed post-verify).
- `frontend`: untouched this task; `pnpm run build` last verified passing (EMB-PC-14).
- `backend`: untouched this task; `./mvnw test` last verified passing (580 tests, EMB-PC-09).
