# Report 402 — Landing: dedicated Ember Local page (`/info/local`)

## 1. Identification
- **Report number:** 402
- **Current Task:** Landing — Ember Local (on-premise), completion pass — dedicated page + cloud/Local differentiation + pricing-card link
- **Predecessor Task:** Report 401 — Landing: add Ember Local to home, pricing and features

## 2. Objective
Ember Local was only visible as a home band, a `/planes` card and a one-line
callout — a buyer had nowhere to read *how it works*. Add a dedicated
`/info/local` page (requirements, offline operation, data ownership, updates,
support), make the `/funcionalidades` callout state explicitly what changes vs
the cloud product, and give the `/planes#local` pricing card a path to that
page instead of a `mailto`-only dead-end.

## 3. Modified Files
- `landing/src/pages/info/local.astro` — new
- `landing/src/pages/en/info/local.astro` — new (thin re-export)
- `landing/src/layouts/InfoLayout.astro`
- `landing/src/pages/info/index.astro`
- `landing/src/components/EmberLocal.astro`
- `landing/src/components/LocalPlan.astro`
- `landing/src/pages/funcionalidades.astro`
- `landing/src/i18n/ui.ts`
- `PROGRESS.md`
- `reports/402-landing-ember-local-page.md` — new

## 4. What Changed?
- **New page `/info/local`** (and `/en/info/local`), rendered in `InfoLayout`
  (docs sidebar). Sections: *Cómo funciona* (one PC as server; other terminals
  by browser on the LAN; Windows `.exe` installer; system-tray icon),
  *Funciona sin internet* (daily ops offline; opportunistic cloud sync —
  catalog down, closed sales up; grace period then reconnect, no data loss),
  *Tus datos, en tu servidor* (local DB, automatic local backups, per-venue
  analytics), *Actualizaciones y soporte* (manual updates with in-app notice;
  annual license covers maintenance/support), and a concrete **Requisitos**
  table (Windows 10/11 64-bit, dedicated server PC, local router/Wi-Fi,
  browser terminals, ESC/POS printer via the printing agent, internet
  optional). Closing CTA → `/contacto` + secondary link → `/planes#local`.
- **`InfoLayout.astro`** — added `/info/local` ("Ember Local") to the sidebar
  nav list.
- **`info/index.astro`** — added a third card linking to `/info/local`.
- **`EmberLocal.astro`** (home band) — CTA now points to `/info/local` (was
  `/planes#local`); `local.cta` reworded "Ver Ember Local en planes" →
  "Conocer Ember Local" / "Learn about Ember Local".
- **`funcionalidades.astro`** — the Ember Local callout now lists the two
  concrete differences from the rest of the page (diners don't use phones —
  servers enter orders from tablets; analytics are per-venue, multi-venue
  dashboards are cloud-panel only); CTA repointed to `/info/local`.
- **`LocalPlan.astro`** — added a secondary link under the "Hablar con el
  equipo" button → `/info/local` ("Ver cómo funciona y requisitos"). Price
  framing unchanged (still quoted, no public number).
- **`i18n/ui.ts`** — +45 keys per locale (`local.page.*`, `local.plan.morelink`,
  `local.feat.diff.*`, `info.nav.local`, `info.card.local.*`); `local.cta`
  reworded. ES/EN parity verified 406/406, no duplicates.

## 5. Why It Changed?
- **Detail level:** Ember Local is still v1 (`EmberHubSetup-0.0.1.exe`) and its
  design doc is a decision draft, so the page is qualitative in prose and only
  commits to hard facts in the requirements table (OS, topology, printer,
  internet-optional). Grace-period days and backup-rotation counts were kept
  out on purpose — they are the most likely specifics to move.
- **`/info/local` (not `/local` or `/planes/local`):** it reads as
  documentation a prospect studies before contacting sales, so it belongs in
  the `/info` section with its sidebar, not as a second top-level marketing
  page. The main nav stays at four items.
- **Repointing CTAs to `/info/local`:** the home band and the funcionalidades
  callout previously dumped the reader onto a pricing anchor with no
  explanation; the pricing card kept only a `mailto`. All three now lead to
  the explainer, and the pricing card keeps its contact CTA plus the new link.
- **Explicit cloud-vs-Local diff:** avoids implying feature parity gaps that
  don't exist (everything on the features page runs in Local) while being
  honest about the two real differences.

## Verification
- `cd landing && pnpm run build` — clean, **22 pages** (was 20); `/info/local`
  and `/en/info/local` emitted.
- ES/EN i18n key parity: 406 / 406, no duplicate keys.
- Rendered output spot-checked: ES page renders Spanish, EN page renders
  English (incl. the requirements table); sidebar, info index, home band and
  funcionalidades callout all link to the localized `/info/local`.
- Pre-existing unrelated warning: `PUBLIC_FRONTEND_URL is not set` (build-time
  CTA fallback, not introduced here).
