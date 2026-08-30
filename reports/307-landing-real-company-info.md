# Report 307 — landing-real-company-info

## 1. Identification
- **Report number:** 307
- **Task ID:** landing-real-company-info (ad-hoc, not an HPD queue task)
- **Predecessor Task:** report 306 (landing-client-router)

## 2. Objective
Replace the placeholder company / legal data on the landing site (fictitious
Ecuador entity) with the real details: Vanter, a startup based in Managua,
Nicaragua.

## 3. Modified Files
- `landing/src/components/Footer.astro`
- `landing/src/components/Analytics.astro`
- `landing/src/pages/contacto.astro`
- `landing/src/lib/plans.ts`
- `landing/src/i18n/ui.ts`
- `landing/public/robots.txt`

## 4. What Changed?
| Placeholder | Real value |
| --- | --- |
| `Vanter S.A.` | `Vanter` (startup; no legal suffix yet) |
| `RUC 1792999999001` | *removed* |
| `Av. Amazonas N34-451 y Av. Atahualpa, Quito, Pichincha, Ecuador` | `Managua, Nicaragua` |
| `ventas@ember.vanter.com` | `tofernandoband01@outlook.com` |
| Hours `Lun–Vie 9:00–18:00 (GMT−5)` | `Lun–Vie 8:00–18:00 (GMT−6)` (es + en) |
| Legal jurisdiction: "normativa/ley ecuatoriana", "República del Ecuador", "jueces de Quito" | "Ley No. 787, Ley de Protección de Datos Personales de Nicaragua", "leyes de la República de Nicaragua", "jueces competentes de Managua" (es + en, privacy + terms) |
| Legal "Última actualización: 14 de agosto de 2026" ×4 | `29 de agosto de 2026` / `August 29, 2026` |
| `robots.txt` sitemap host + Plausible `data-domain` `ember.vanter.com` | `ember.vanter.net` (matches `astro.config.mjs` `site:`) |

- **Footer:** third column heading `Vanter S.A.` → `Vanter`; address block → `Managua, Nicaragua` + a `mailto:` link to the contact address (fills the otherwise-sparse column). Copyright line → `© {year} Ember — Vanter.`
- **Contacto page:** "Correo" card + "Pedir una demo" CTA `mailto:` → new address; "Oficina" card body → `Vanter` / `Managua, Nicaragua`.
- **Enterprise plan CTA** (`plans.ts`) `mailto:` → new address.
- Facebook / Instagram: none yet — will be added to the footer once the pages exist and URLs are provided.
- Legal pages remain **reference translations, not a legal review** — only the jurisdiction references were repointed to Nicaragua.
- Placeholder `og-image.png` / `apple-touch-icon.png` / favicon left as-is — pending real asset files.

## 5. Why It Changed?
Report 305 shipped full-site i18n with the note that the placeholder Ecuador
company data would be swapped for real Nicaragua info later. Maintainer supplied
the real details (Vanter, Managua NI, `tofernandoband01@outlook.com`, 8–18h,
no tax ID / phone for now) and approved: normalize the domain to
`ember.vanter.net` and repoint the legal jurisdiction to Nicaragua.

## Verification
- `cd landing && pnpm build` — green, 20 pages.
- Built HTML greps: footer / contacto / plans show `Vanter`, `Managua, Nicaragua`,
  `tofernandoband01@outlook.com`, `8:00–18:00 (GMT−6)`; privacy/terms (es + en)
  cite Nicaragua / Ley No. 787 / Managua courts and the new date; `robots.txt`
  and Plausible `data-domain` are `ember.vanter.net`.
- `grep -r 'vanter.com|Ecuador|Vanter S.A.|1792999999001' dist/` → no matches.
- No file outside `landing/` touched → backend/frontend suites not re-run.
