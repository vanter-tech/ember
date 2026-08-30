# Report 289 — landing-plans-page-comparison

## 1. Identification
- **Report number:** 289
- **Task ID:** landing-plans-page-comparison (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 288 (landing-hero-dashboard-image)

## 2. Objective
Give the plans their own view: a dedicated `/planes` page with the four plan cards
and, below them, a full feature-by-feature comparison table (checks per plan). Point
the nav "Precios" link at the new page and remove pricing from the home entirely.

## 3. Modified Files
- **New** `landing/src/lib/plans.ts` — single source for `PLANS` and the `COMPARISON`
  matrix (`ComparisonGroup[]`, cell = `true` / `false` / string).
- **New** `landing/src/components/PlanCards.astro` — the 4-card grid, from `PLANS`
  (extracted verbatim from the old `Pricing.astro` body).
- **New** `landing/src/components/PricingTable.astro` — the comparison table.
- **New** `landing/src/pages/planes.astro` — the page (header + `PlanCards` +
  comparison section + `CTASection` + `Footer`).
- **Deleted** `landing/src/components/Pricing.astro` — the home no longer shows
  pricing at all; the component briefly became a teaser, then was removed with the
  section per follow-up.
- `landing/src/pages/index.astro` — `<Pricing />` and its import removed; home is
  now Hero → Features → Contact → CTA → Footer.
- `landing/src/lib/constants.ts` — `NAV_LINKS`: `#features` → `/#features` (works
  from any page), `#pricing` → `/planes`.
- `landing/src/components/Hero.astro` — the "Ver planes" button now links `/planes`
  (the only pricing entry point from the home besides the nav).

## 4. What Changed?
### `/planes`
`Nav` → an intro section (`Planes y precios` eyebrow, `h1`, one-line lede) with the
`PlanCards` grid → a `bg-muted/30` section titled "Qué incluye cada plan" wrapping
`PricingTable` in a `rounded-xl border bg-card` shell, plus a fine-print line about
USD / per-restaurant / Enterprise quoting → `CTASection` → `Footer`. Standard
`Layout` SEO (`title`, `description`).

### `PricingTable.astro`
`overflow-x-auto` wrapper; `<table>` `min-w-[760px]` (scrolls on narrow screens).
Header row: label cell + one column per plan (name + `price+period`); the Pro
column carries a faint `bg-primary/[0.04]` tint the whole way down. First column is
`sticky left-0 bg-background` so feature names stay put while scrolling on mobile.
Body iterates `COMPARISON`: each group emits a full-width `bg-muted/60` category
band, then one `<tr>` per feature — `row.label` as a `<th scope="row">`, then four
cells rendering `true` → a primary check `<svg>` (+ `sr-only` "Incluido"), `false`
→ a muted `—` (`aria-label="No incluido"`), string → the literal text
(`font-medium`). 4 groups, 21 feature rows.

### `COMPARISON` matrix (drafted from Ember's capabilities — needs maintainer review)
- **Operación en piso:** Mesas activas `1 / 10 / Ilimitadas / Ilimitadas`; carrito
  colaborativo + KDS (all); gestión de piso, división/unión de cuentas, cierre de
  caja, impresión de comandas/recibos (Starter+); múltiples salones (Pro+).
- **Analítica:** métricas básicas (all); filtros por período (Starter+); analítica
  avanzada + exportación de reportes (Pro+).
- **Equipo y acceso:** roles Mesero/Cocina/Admin, gestión de empleados, branding
  (Starter+); múltiples meseros simultáneos (Pro+).
- **Escala y soporte:** multi-sucursal, integraciones a medida, SLA, gerente de
  cuenta (Enterprise); Soporte `Comunidad / Correo / Prioritario / Dedicado 24/7`.

## 5. Why It Changed?
The maintainer asked for the plans to live on their own view with a comparison
table underneath, then to drop pricing from the home entirely. A dedicated
`/planes` route (their choice over a full-screen home section) gives pricing room
for a real feature-by-feature matrix; the home is now purely product story
(Hero → Features → Contact → CTA) and reaches pricing through the nav "Precios"
link and the hero "Ver planes" button. Plan data lives in `lib/plans.ts` so
`PlanCards` and `PricingTable` read from one source. The comparison tiers were
inferred from Ember's documented feature set (CLAUDE.md §1) and the existing
per-plan bullet lists — the maintainer should sanity-check which tier each
capability actually unlocks.

## Verification
- `cd landing && pnpm build` — green. **6 pages** (`/planes` added, no `#pricing`
  section on the home); no image changes.
- Dev server reviewed in Chrome: `/planes` renders header + cards + the full table
  (4 categories, 21 rows, Pro column tinted, checks / `—` / text values). Home has
  no pricing block (`document.querySelector('#pricing')` is null); it flows
  Hero → Features → Contact → CTA → Footer. `header` anchors: `/`, `/#features`,
  `/planes`, login, register. No horizontal page overflow at desktop; the table is
  `min-w-[760px]` inside an `overflow-x-auto` wrapper for mobile.
- No file outside `landing/` touched.
