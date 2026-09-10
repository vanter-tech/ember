# Report 427 — Downloads page: "cloud version" badge on the print-agent card

## 1. Identification
- **Report number:** 427
- **Current Task:** ad-hoc — landing `/info/descarga` print-agent card badge
- **Predecessor Task:** report 426 — downloads hosting live + `v0.2.3` prod deploy

## 2. Objective
Add a badge reading "Necesario para la versión cloud" to the print-agent card on
`/info/descargas` (the print agent is required for the SaaS/cloud deployment;
Ember Hub bundles printing itself).

## 3. Modified Files
- `landing/src/i18n/ui.ts`
- `landing/src/pages/info/descarga.astro`

## 4. What Changed?
- `ui.ts`: new key `dl.agent.badge` in both locale blocks — ES `'Necesario para la
  versión cloud'`, EN `'Required for the cloud version'` — placed after
  `dl.agent.req`. Parity stays balanced (+1/+1).
- `descarga.astro`:
  - Agent entry in the `downloads` array gains `badge: t('dl.agent.badge')`; the
    Hub entry gains `badge: null` to keep a uniform object shape (avoids a TS
    union-access error in the frontmatter).
  - The card `.map` now wraps the `<h2>` in a `flex flex-wrap items-center gap-2`
    row and renders a pill `<span>` after the title when `item.badge` is set
    (`rounded-full bg-primary/10 text-primary ring-1 ring-inset ring-primary/20`,
    `text-xs`). Only the print-agent card shows it.

## 5. Why It Changed?
The two installers on the page look equivalent, but the print agent is mandatory
for cloud/SaaS tenants that want physical tickets, whereas Ember Hub is an
alternative all-in-one deployment. The badge disambiguates at a glance without
adding prose.

## 6. Verification
- `cd landing && pnpm run build` — clean, **28 pages** (unchanged), optimized
  images reused, sitemap generated.
