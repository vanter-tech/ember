# Report 98 — EMB-LP-15

## 1. Identification
- **Report:** 98
- **Task ID:** EMB-LP-15
- **Predecessor Task:** EMB-LP-14 (report 97)

## 2. Objective
Create Privacy Policy (`/privacy`) and Terms of Service (`/terms`) legal pages (checklist items #15/#16), matching the site's brutalist visual language.

## 3. Modified Files
- `landing/src/pages/privacy.astro` (new)
- `landing/src/pages/terms.astro` (new)

## 4. What Changed?
Added two static Astro pages, both wrapped in `Layout` (per-page title/description) with `Nav` + `Footer`, rendering a stacked list of bordered sections (`border-[3px] border-foreground`, `shadow-brutal-sm`) matching the card styling used in `Features.astro`. `privacy.astro` covers responsible party, data collected, use of data, cookies, retention, data-subject rights, and contact. `terms.astro` covers acceptance, service description, account responsibility, plans/billing, acceptable use, availability, liability limitation, governing law/jurisdiction, and contact. Both reference Vanter S.A.'s existing RUC/address already present in `Footer.astro` and link no new dependencies.

## 5. Why It Changed?
`Footer.astro` (EMB-LP-10) already links to `/privacy` and `/terms`, and the spec's Legal & Compliance checklist requires dedicated routes for both. Static Spanish-language legal copy closes checklist items #15/#16.

## Verification
`astro build` — passed (4 pages built: `index.html`, `404.html`, `privacy/index.html`, `terms/index.html`; `sitemap-index.xml` regenerated). `dist/` removed post-verify.
