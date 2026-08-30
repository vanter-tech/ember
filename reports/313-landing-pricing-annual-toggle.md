# Report 313 — landing: repricing + annual billing toggle

## 1. Identification
- **Report number:** 313
- **Current Task:** pricing track — new prices + monthly/annual view
- **Predecessor Task:** report 312 (landing-a11y-hardening)
- **Branch:** `feat/hpd-14-monitoring` (ad-hoc landing work, track of reports 287–312)

## 2. Objective
Apply the pricing decision (Free $0 / Starter $19 / Pro $49 / Enterprise custom,
per location, USD, IVA aparte) and add a monthly ⇄ annual billing toggle to the
`/planes` page. Annual = pay 10 months, use 12 (2 months free): Starter $190/yr,
Pro $490/yr. No separate free trial — the Free plan is the trial. No card-checkout
claim on the page (payment collection is still TBD).

## 3. Modified Files
- `landing/src/lib/plans.ts` — new prices + `priceAnnual` / `periodAnnual` /
  `annualNote` fields on `Plan`
- `landing/src/components/PlanCards.astro` — billing toggle control, dual price
  markup, inline toggle script, active-state style
- `landing/src/components/PricingTable.astro` — dual price in the column headers
- `landing/src/pages/planes.astro` — `data-billing="monthly"` on the `<main>` wrapper
- `landing/src/styles/global.css` — 2 rules that show only the matching price variant
- `landing/src/i18n/ui.ts` — new keys + copy edits (es + en)

## 4. What Changed?
**Prices** (`plans.ts` `getPlans`): Starter `$29`→`$19`, Pro `$79`→`$49`. Each of
Free/Starter/Pro gained `priceAnnual` (`$0` / `$190` / `$490`) + `periodAnnual`
(`/año`). Starter and Pro also carry `annualNote` = `t('ppage.billing.save')`
("2 meses gratis"). Enterprise is unchanged and has no annual variant.

**Toggle mechanism** (no framework, mirrors `Nav.astro`'s inline-script pattern):
- `planes.astro`'s existing `<main id="main-content">` now also carries
  `data-billing="monthly"` — it spans both the plan-cards section and the
  comparison-table section, so one toggle drives both.
- `PlanCards.astro` renders a segmented `Mensual | Anual · 2 meses gratis` control
  (`[data-billing-toggle]`, two `[data-billing-set]` buttons with `aria-pressed`).
  Its `<script is:inline>` (bound on `astro:page-load`) flips
  `data-billing` on the nearest `[data-billing]` ancestor and updates `aria-pressed`.
- Every price is emitted twice: `<… data-price-monthly>` and `<… data-price-annual>`,
  plus a `<… data-annual-note>` line. `global.css` hides the non-active variant via
  `[data-billing='monthly'] [data-price-annual] { display:none }` etc. Default
  markup is `monthly`, so there is no flash and the page degrades correctly if the
  script never runs. Elements with no annual counterpart (Enterprise) are left
  untagged and always show.
- `PricingTable.astro` column headers use the same `data-price-monthly` /
  `data-price-annual` spans, so the table header prices switch in lockstep.

**Copy** (`ui.ts`, es + en):
- `plan.perYear` (new): `/año` / `/yr`
- `ppage.billing.{label,monthly,annual,save}` (new)
- `plan.starter.tagline` → "Para sodas, comedores y cafés." / "For small restaurants
  and cafés."
- `plan.pro.tagline` → "Para restaurantes con volumen." / "For higher-volume
  restaurants."
- `ppage.lede` += "Empezás gratis, sin tarjeta," / "Start free, no card,"
- `ppage.fineprint` += "Con plan anual pagás 10 meses y usás 12." / "On the annual
  plan you pay for 10 months and use 12."

**Not changed:** feature gating / per-plan table limits (e.g. Free "1 mesa", Starter
"10 mesas") — flagged for a separate review since report 289. JSON-LD
`SoftwareApplication` still advertises only the free `Offer` — the paid numbers stay
out of structured data until validated with real customers.

## 5. Why It Changed?
$79 was competing with Fudo/Parrot without the track record; $19/$49 sits mid-market
for Central America and leaves room to raise later. The annual toggle is the
standard way to surface the prepay discount and improves cash flow / retention,
which matters more given recurring card billing in Nicaragua is still unresolved.

## Verification
- `cd landing && pnpm run build` — green, 20 pages.
- `grep` on `dist/planes/index.html`: `$19`/`$49` (×2 each: cards + table header),
  `$190`/`$490` (×2 each), `data-billing="monthly"` on `<main>`, toggle attrs, 6×
  `data-price-monthly` / 6× `data-price-annual`, 2× `data-annual-note`. `dist/en/planes/`
  has `$190`/`$490` + "2 months free" / "Monthly" / "Annual".
- Browser (`astro preview`): monthly view shows $0/$19/$49/A medida; clicking
  **Anual** flips every price to $0/$190/$490 `/año`, reveals the "2 meses gratis"
  note under Starter & Pro, marks the button `aria-pressed`, and the comparison-table
  header prices switch too. Enterprise stays "A medida" in both modes. Fineprint and
  lede updated. No layout shift, no horizontal overflow.
