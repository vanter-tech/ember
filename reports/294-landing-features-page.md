# Report 294 — landing-features-page

## 1. Identification
- **Report number:** 294
- **Task ID:** landing-features-page (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 293 (landing-header-nav-refactor)

## 2. Objective
Same treatment as `/planes` (report 289) for the features: a dedicated
`/funcionalidades` route with the feature cards plus a per-feature detail section,
nav "Funcionalidades" pointed at it, and the section removed from the home.

## 3. Modified Files
- **New** `landing/src/lib/features.ts` — `FEATURES` (n, title, description,
  `points[]`), the single source.
- **New** `landing/src/components/FeatureCards.astro` — the 4-card grid, from
  `FEATURES` (extracted verbatim from the old `Features.astro` body).
- **New** `landing/src/pages/funcionalidades.astro` — the page.
- **Deleted** `landing/src/components/Features.astro`.
- `landing/src/pages/index.astro` — `<Features />` + import removed; home is now
  Hero → Contact → CTA → Footer.
- `landing/src/lib/constants.ts` — `NAV_LINKS`: `/#features` → `/funcionalidades`
  (so both the header and the mobile drawer follow).

## 4. What Changed?
### `/funcionalidades`
`Nav` → an intro section (`Funcionalidades` eyebrow, `h1` "Hecho para el flujo real
de un restaurante", one-line lede) with the `FeatureCards` grid → a `bg-muted/30`
section that expands each of the four features into a `md:grid-cols-[1fr_1.4fr]`
row — number / title / description on the left, a `sm:grid-cols-2` list of concrete
capabilities with primary checks on the right, rows `divide-y divide-border` →
`CTASection` → `Footer`. Standard `Layout` SEO.

### `FEATURES` capability lists (drafted from CLAUDE.md §1)
- **Carrito colaborativo:** QR / 5-char code join, live shared cart, pay-your-own-
  part, no app install.
- **Comandas en cocina (KDS):** real-time per-station queue, Pendiente → En
  preparación → Listo → Entregado, WebSocket sync, overdue orders self-flag.
- **Gestión de piso y meseros:** floor map, open/assign/close table sessions,
  manual item adds + split/merge bills, per-shift cash close with arqueo.
- **Analítica:** sales / average ticket / top items, day·week·month·year filters,
  per-product and per-table performance, employee & role management.

## 5. Why It Changed?
"Ahora hay que hacer lo mismo con la funcionalidad, una nueva vista con su ruta."
Mirrors the plans move: feature data lives in `lib/features.ts` so `FeatureCards`
renders identically wherever it is used, the dedicated route has room for a real
per-feature breakdown, and the home stays short — feature discovery now runs
through the nav and the hero feature bar.

## Verification
- `cd landing && pnpm build` — green. **7 pages** (`/funcionalidades` added, no
  features section on the home).
- Dev server in Chrome: `/funcionalidades` renders the header, the 4 cards, and the
  four detail rows with their check-lists; `CTASection` + `Footer` follow. The nav
  "Funcionalidades" link has `aria-current="page"` and the active pill on that page.
  No horizontal page overflow.
- No file outside `landing/` touched.
