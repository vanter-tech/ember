# Report 97 — EMB-LP-14

## 1. Identification
- **Report:** 97
- **Task ID:** EMB-LP-14
- **Predecessor Task:** EMB-LP-13 (report 96)

## 2. Objective
Create a custom 404 error page (checklist item #1) matching the site's brutalist visual language.

## 3. Modified Files
- `landing/src/pages/404.astro` (new)

## 4. What Changed?
Added `src/pages/404.astro` — an Astro page (no client islands) rendering `Nav` + a centered error block (large "404" mark, heading, message, "Volver al inicio" CTA link to `/`) + `Footer`, wrapped in `Layout`. Reuses the existing token classes seen in `Nav.astro`/`CTASection.astro` (`border-[3px] border-foreground`, `bg-accent`, `shadow-brutal`, `font-mono uppercase font-black`) — no new styles or dependencies introduced.

## 5. Why It Changed?
Astro auto-serves `src/pages/404.astro` for unmatched routes in static builds; without it the host's generic 404 would break the site's design consistency. This closes checklist item #1 from the landing-page spec.

## Verification
`astro build` — passed (2 pages built: `index.html`, `404.html`; `sitemap-index.xml` regenerated). `dist/` removed post-verify.
