# Report 425 — Landing: `/info/descarga` downloads page

## 1. Identification
- **Report number:** 425
- **Current Task:** ad-hoc — landing downloads section (not an EMB-* milestone)
- **Predecessor Task:** report 424 — print-job cancel

## 2. Objective
Add a downloads page to the landing site listing the two Windows installers
(Ember print agent, Ember Hub) as a card grid, placed in the `/info` section
directly below Seguridad.

## 3. Modified Files
- `landing/src/lib/constants.ts`
- `landing/src/pages/info/descarga.astro` (new)
- `landing/src/pages/en/info/descarga.astro` (new)
- `landing/src/layouts/InfoLayout.astro`
- `landing/src/pages/info/index.astro`
- `landing/src/i18n/ui.ts`

## 4. What Changed?
- **`constants.ts`** — two new exports, `AGENT_DOWNLOAD_URL` and
  `HUB_DOWNLOAD_URL`, each `import.meta.env.PUBLIC_AGENT_DOWNLOAD_URL` /
  `PUBLIC_HUB_DOWNLOAD_URL` with a fallback to
  `https://downloads.ember.vanter.net/EmberAgentSetup-latest.exe` and
  `EmberHubSetup-latest.exe`. Same env-with-fallback pattern as `FRONTEND_URL`;
  no PROD warn since the fallback is already the intended production URL.
- **`info/descarga.astro`** — new page on `InfoLayout`. Eyebrow / h1 / lede,
  then a `sm:grid-cols-2` grid of two cards (name, description, requirements
  line, "Descargar .exe" button linking the constant). Below: a SmartScreen
  note, an "Ember Hub?" callout linking `/info/local`, and a contact callout
  linking `/contacto`. Structure mirrors `info/seguridad.astro`.
- **`en/info/descarga.astro`** — 3-line re-export of the ES page (existing
  per-locale-file pattern; i18n resolves via `Astro.currentLocale`).
- **`InfoLayout.astro`** — sidebar `items[]` gets `/info/descarga` after
  `/info/seguridad`.
- **`info/index.astro`** — overview `links[]` gets a downloads card after the
  security card.
- **`ui.ts`** — +22 keys per locale (ES + EN, parity kept 467/467):
  `info.nav.downloads`, `info.card.downloads.{title,body}`, `dl.page.*`,
  `dl.agent.*`, `dl.hub.*`, `dl.hub.more.*`, `dl.help.*`, `dl.cta`, `dl.note`.

## 5. Why It Changed?
Restaurants need a single public place to get the desktop apps. The `/info`
section is where operational how-to already lives (manual, videos, Ember
Local, security), so the downloads page sits with it rather than on the
marketing home. Route kept as `descarga` (singular) per the request; the
English route reuses the Spanish slug like every other `/en/info/*` page.

## Notes / follow-ups (not code)
- The download links 404 until the installers are hosted at
  `downloads.ember.vanter.net`. `EmberHubSetup-*.exe` is not built/published
  yet. Hosting + `PUBLIC_*_DOWNLOAD_URL` config is the next discussion.
- Installers are not code-signed, hence the SmartScreen note in `dl.note`.

## Verification
- `cd landing && pnpm run build` — clean, **28 pages** (+2), sitemap OK.
- i18n ES/EN parity — 467/467, no mismatched keys.
