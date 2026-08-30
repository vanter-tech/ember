# Report 297 — landing-info-section

## 1. Identification
- **Report number:** 297
- **Task ID:** landing-info-section (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 296 (landing-remove-walkthrough-contact-page)

## 2. Objective
Add an "Info" nav item leading to an `/info` section with a sidebar: **Manual de
usuario** and **Videos de Ember funcionando**.

## 3. Modified Files
- **New** `landing/src/layouts/InfoLayout.astro` — shared shell (Nav + left
  sidebar + slot + Footer).
- **New** `landing/src/pages/info/index.astro` — section overview.
- **New** `landing/src/pages/info/manual.astro` — user manual.
- **New** `landing/src/pages/info/videos.astro` — video grid.
- `landing/src/lib/constants.ts` — `NAV_LINKS` gains `{ href: '/info', label:
  'Info' }` (between Precios and Contacto).

## 4. What Changed?
### `InfoLayout.astro`
Wraps `Layout`, renders `Nav`, then a `md:grid-cols-[220px_1fr]` with a `md:sticky
top-24` `<aside>` and a `<slot />` in a `max-w-2xl` content column, then `Footer`.
The sidebar lists Resumen / Manual de usuario / Videos de Ember as pill links with
an `aria-current="page"` active state from `Astro.url.pathname`. The header nav
"Info" link is prefix-matched (`Nav.astro`'s `isActive` already does
`path.startsWith('/info/')`), so it stays lit on every `/info/*` page.

### Pages
- **`/info`** — eyebrow + `h1` "Aprendé a usar Ember" + two card links to the
  manual and the videos.
- **`/info/manual`** — "Manual de usuario" with a "Borrador" note, an "En esta
  página" anchor TOC (`sm:grid-cols-2`), then nine `scroll-mt-24` sections, one per
  Ember module (primeros pasos, roles, piso, carrito, KDS, cobro, analítica,
  impresión, configuración), each a short real description drawn from CLAUDE.md §1.
- **`/info/videos`** — a `sm:grid-cols-2` grid of six cards (Recorrido general,
  Alta de restaurante, Servicio de mesa completo, Cocina en vivo, Cierre de caja,
  Analítica). Each has an `aspect-video` slot: it renders an `<iframe>` when the
  entry's `embed` URL is set, otherwise a "Próximamente" play-icon placeholder. A
  frontmatter comment explains where to paste YouTube/Vimeo embed URLs.

Content on the manual and videos pages is scaffolding — real procedures / captures /
video URLs to be filled in.

## 5. Why It Changed?
"Quiero agregar un apartado en el nav llamado info, y dentro … como sidebar con
Manual de usuario, y videos de ember funcionando." Built as a small docs-style
section following the same routed-sub-page pattern as `/planes`, `/funcionalidades`
and `/contacto`, with its own second-level sidebar for the two topics.

## Verification
- `cd landing && pnpm build` — green. **10 pages** (`/info`, `/info/manual`,
  `/info/videos` added).
- Dev server in Chrome: `/info/manual` and `/info/videos` render the shared sidebar
  (correct item `aria-current="page"` per page) and their content; the header nav
  "Info" link is active on both. The video cards show the "Próximamente"
  placeholder. No horizontal page overflow.
- No file outside `landing/` touched.
