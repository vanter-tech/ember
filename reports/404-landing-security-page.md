# Report 404 — Landing: security page (`/info/seguridad`)

## 1. Identification
- **Report number:** 404
- **Current Task:** Landing nice-to-have #2 — `/info/seguridad` page
- **Predecessor Task:** Report 403 — Landing: remove the cookie consent banner

## 2. Objective
Ember Local's pitch leans on "your data, on your server" but the site had
nowhere to describe how data is protected in the cloud product. Add a
security overview page a prospect (or their vendor review) can read before
contacting sales.

## 3. Modified Files
- `landing/src/pages/info/seguridad.astro` — new
- `landing/src/pages/en/info/seguridad.astro` — new (thin re-export)
- `landing/src/layouts/InfoLayout.astro`
- `landing/src/pages/info/index.astro`
- `landing/src/i18n/ui.ts`
- `PROGRESS.md`
- `reports/404-landing-security-page.md` — new

## 4. What Changed?
- **New page `/info/seguridad`** (and `/en/info/seguridad`), rendered in
  `InfoLayout` (docs sidebar), same molde as `/info/local`. Four card
  sections + two callouts:
  - *Aislamiento entre restaurantes* — multi-tenant; per-restaurant
    identifier on catalog/sales/billing/settings/kitchen, queries filtered
    by it; diners bound to the restaurant on join.
  - *Cuentas y accesos* — signed, expiring JWTs; BCrypt password hashes;
    admin / floor / kitchen roles gate features.
  - *Datos en tránsito* — HTTPS/TLS everywhere; served behind Cloudflare.
  - *Respaldos* — nightly production DB backup to private versioned
    storage; automatic daily server-disk snapshots; not public, not shared.
  - *Cuando los datos no salen de tu local* — Ember Local keeps the DB
    on-premise; links to `/info/local`.
  - *¿Necesitás más detalle?* — CTA to `/contacto` for vendor/security
    reviews.
- **`InfoLayout.astro`** — added `/info/seguridad` ("Seguridad" / "Security")
  to the sidebar.
- **`info/index.astro`** — added a fourth card linking to `/info/seguridad`.
- **`i18n/ui.ts`** — +21 keys per locale (`sec.page.*`, `info.nav.security`,
  `info.card.security.*`). ES/EN parity 425/425, no duplicate keys.

## 5. Why It Changed?
- **Every claim is verified against the codebase**, not aspirational:
  `BCryptPasswordEncoder` in `SecurityConfig`/`AuthService`; `io.jsonwebtoken`
  for JWTs; `@TenantId` discriminator pattern (CLAUDE.md, tenant isolation);
  `deploy/RUNBOOK.md` — nightly `pg_dump` to a private, object-versioned
  bucket + a daily boot-disk snapshot schedule; Cloudflare-proxied records.
- **Kept deliberately non-committal on specifics that could move:** storage
  is described as "private, versioned" rather than naming the provider; JWT
  TTL is "con expiración" rather than a number.
- **`/info/seguridad` slug (Spanish, even for `/en/`):** matches the
  project's existing convention (`/en/funcionalidades`, `/en/info/local`).

## Verification
- `cd landing && pnpm run build` — clean, **24 pages** (was 22);
  `/info/seguridad` and `/en/info/seguridad` emitted.
- ES/EN i18n key parity: 425 / 425, no duplicates.
- Rendered output spot-checked: ES renders Spanish, EN renders English
  (incl. "BCrypt"); sidebar + info index link to the localized page; the
  on-premise callout links to `/info/local`.
