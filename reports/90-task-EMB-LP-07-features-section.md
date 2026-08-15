# Report 90 — EMB-LP-07: Features section

**Predecessor Task:** EMB-LP-06 (report 89)

## Objective
Add the Features grid to the `landing` marketing site, highlighting the four core product pillars: collaborative cart, KDS, floor/waiter management, admin analytics.

## Modified Files
- `landing/src/components/Features.astro` (new)
- `landing/src/pages/index.astro`

## What Changed?
Added `Features.astro`: a brutalist `id="features"` section with a mono kicker, oversized `<h2>`, and a 4-card grid (`sm:grid-cols-2 lg:grid-cols-4`) of `border-[3px]`/`shadow-brutal-sm` cards. Each card carries a numbered label (`01`–`04`, no icon library — none exists in this standalone package and adding one wasn't warranted), a bold uppercase title, and a short Spanish description: Carrito Colaborativo, Comandas en Cocina (KDS), Gestión de Piso y Meseros, Analítica para Administradores. `index.astro` now imports and renders `<Features />` below `<Hero />`.

## Why It Changed?
Implements EMB-LP-07 from the landing-page spec (`docs/superpowers/specs/2026-08-13-ember-landing-page-design.md`, checklist item 3): a blocky feature grid is required directly beneath the hero, before Pricing (EMB-LP-08). Styling reuses the existing brutalist tokens (`shadow-brutal-sm`, `border-foreground`, `text-accent`) established in EMB-LP-04/05/06 rather than introducing new patterns or dependencies.

## System Health
`landing`'s `astro build` PASSING (`dist/` removed post-verify). `frontend`/`backend` untouched this task.
