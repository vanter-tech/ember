# Report 401 — Landing: add Ember Local (on-premise)

## 1. Identification
- **Report number:** 401
- **Current Task:** the landing page never mentioned the Hub / on-premise product; add it
- **Predecessor Task:** report 400 — Hub v2 prep: residual hardening

## 2. Objective
Surface **Ember Local** (the on-premise, staff-operated install: no diner phones, servers add
orders and seats from tablets around the venue) on the marketing site — a home section, a pricing
block, and a features callout — in Spanish and English, without disturbing the existing section
rhythm.

## 3. Modified Files
- `landing/src/components/EmberLocal.astro` (new) — home section
- `landing/src/components/LocalPlan.astro` (new) — pricing block
- `landing/src/pages/index.astro` — mount `<EmberLocal />` after `<Compare />`
- `landing/src/pages/planes.astro` — mount `<LocalPlan />` after `<PlanCards />` (anchor `#local`)
- `landing/src/pages/funcionalidades.astro` — on-premise callout before `<CTASection />`
- `landing/src/i18n/ui.ts` — `local.*` keys (36 each) in `es` and `en`

## 4. What Changed?

### Home — `EmberLocal.astro`
A highlight band (`bg-primary/[0.04]`, `border-y`) placed right after the "Ember vs. lo de siempre"
comparison: eyebrow "Dos formas de usar Ember", headline "¿No querés que el comensal use el
teléfono? Ember Local", lede, and three cards — no diner apps/QR, works on your LAN, data on your
server. CTA links to `/planes#local` (localized). Cards lift and the icon chip scales on hover
(`transition`, disabled by the global `prefers-reduced-motion` guard).

### Pricing — `LocalPlan.astro`
A wide card below the four Cloud plans (own eyebrow "Preferís tenerlo en tu local"), visually keyed
to the highlighted-plan style (`border-primary/40`, `ring-primary/20`). Left: badge, name, tagline,
five-item feature list. Right panel: **Formas de pago** — annual license (best price), semi-annual
license (less commitment), setup & training (one-time, quoted) — plus a note that the license price
is agreed per tablet/table count, and a "Hablar con el equipo" CTA to `/contacto`. No public number.
Carries `id="local"` with `scroll-mt-24` for the anchor.

### Features — `funcionalidades.astro`
A short accent section ("¿Preferís el servicio 100% atendido?") linking to `/planes#local`.

### i18n
All copy is `t('local.*')`; the `en/*` pages are thin re-exports of the ES pages, so the shared
components localize automatically from `Astro.currentLocale`. No page duplication.

## 5. Why It Changed?
Ember Local is commercially ready but was invisible on the site — the entire landing was framed as
cloud SaaS ("creás la cuenta y corre en el navegador"), which actively excludes restaurants that
don't want phone ordering. The Local block reframes it as "two ways to run Ember" and gives sales a
concrete page to point at. Billing is shown as annual **or** semi-annual (plus a separate quoted
install), which fits how a Nicaraguan restaurant budgets and keeps collection events few.

## System Health
- `landing`: `pnpm run build` — clean, 20 pages
- Visual check (astro preview): home band, `/planes#local` card and `/funcionalidades` callout
  render correctly in ES and EN, consistent with the existing design system
- No test suite in the landing project
