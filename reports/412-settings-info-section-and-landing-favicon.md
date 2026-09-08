# Report 412 — SaaS favicon = landing favicon; new "Información" section in Settings

## 1. Identification
- **Report number:** 412
- **Current Task ID:** ad-hoc frontend feature (no milestone ID)
- **Predecessor Task:** report 411 — hotfix (waiter movement button / dead TopNav button)

## 2. Objective
1. Use the same favicon as the landing site in the SaaS app.
2. Add an "Información" section to `/admin/settings` with a made-by/location/legal block.

## 3. Modified Files
- `frontend/public/favicon.svg` (replaced), `frontend/public/favicon.ico` (new), `frontend/public/apple-touch-icon.png` (new)
- `frontend/index.html`
- `frontend/src/store/uiStore.ts`
- `frontend/src/pages/admin/components/settings/InfoSettings.tsx` (new)
- `frontend/src/pages/admin/Settings.tsx`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/components/GlobalSearchResults.tsx`
- `frontend/src/locales/es/admin.ts`, `frontend/src/locales/en/admin.ts`

## 4. What Changed?
- **Favicon:** copied `landing/public/{favicon.svg,favicon.ico,apple-touch-icon.png}` into
  `frontend/public/`; `index.html` now declares the same three `<link rel="icon">` /
  `apple-touch-icon` tags the landing's `Layout.astro` uses (`.ico` 32x32 + SVG + apple-touch).
- **`SettingsType`** (`uiStore.ts`): added `'INFO'`.
- **`InfoSettings.tsx`:** read-only Card (icon `Info`, no save/undo footer). Body: "Hecho y
  distribuido por **vanter.net**" (link → `https://vanter.net`), "Managua, Nicaragua",
  "© {year} Ember — Vanter. Todos los derechos reservados." (year computed at render), and a
  support line linking to `https://ember.vanter.net/contacto`.
- **`Settings.tsx`:** `INFO` case in `renderContent`; `TAB_TOUR_KEYS` relaxed to
  `Partial<Record<…>>` (INFO has no tour) with a `tourSteps`/`<SectionTour>` guard so a
  tour-less tab renders nothing extra.
- **`SettingsBar.tsx`:** `INFO` leaf in `LEAF` + `buildSettingsNav` (last item, `Info` icon).
- **`GlobalSearchResults.tsx`:** `INFO: 'infoLabel'` so the admin global search finds it.
- **i18n:** `infoLabel`, `infoCardTitle`, `infoCardDescription`, `infoMadeByPrefix`,
  `infoLocation`, `infoCopyright` (`{{year}}`), `infoSupportPrefix`, `infoSupportLinkText` in
  ES and EN.

## 5. Why It Changed?
- One brand identity across the marketing site and the app; the old app favicon was a
  different mark.
- The admin had nowhere showing who makes/distributes Ember or how to reach support; a
  Settings "Información" tab is the conventional home for it.

## 6. Verification
- `cd frontend && pnpm run build` — clean (`tsc -b` + vite).
- `pnpm run lint` — 0 errors, 16 pre-existing warnings.
- `pnpm run test:run` — 118/118.
