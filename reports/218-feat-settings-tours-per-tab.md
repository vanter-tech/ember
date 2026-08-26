# Report 218: One tour per Configuración tab (11 tabs)

**Predecessor:** report 217

## Objective
Final content rollout on `SectionTour`: a tutorial for every tab of the admin Settings page
(Marca y Negocio, Menú, Facturación, Pasarela de pago, Ticket, Impresoras, Hardware, Espacio,
Horario, Fidelización, Catálogo de recompensas) — completes the user's tutorial-rollout request.

## Modified Files
- `frontend/src/pages/admin/Settings.tsx`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/locales/es/admin.ts`, `frontend/src/locales/en/admin.ts`

## What Changed?
Settings' 11 tabs are switched via local state (`useSettingsStore().activeSettings`), not routes,
and there are 11 separate tab components — adding a stable id inside each one (to point a
multi-step tour at specific fields) would mean touching 11 more files for comparatively little
payoff over a well-targeted overview. Instead: `SettingsBar.tsx`'s `<nav>` got `id=
"settings-tour-sidebar"`, and `Settings.tsx`'s content pane (whichever tab component
`renderContent()` is currently showing) got `id="settings-tour-content"`. A `TAB_TOUR_KEYS` map
(typed against `keyof (typeof dictionaries)['es']['admin']`, no `as any`/`as never` casts) resolves
each `SettingsType` to its own title/content i18n key pair, so every tab gets a genuinely different
one-step tour description even though they all target the same pane element. `BRANDING`
(the tab an admin lands on first, since `activeSettings` starts `null` and only becomes `'BRANDING'`
once a sidebar button is clicked) gets an extra leading step pointing at `#settings-tour-sidebar`
explaining tab navigation; the other 10 tabs get just their one content step.

`<SectionTour key={activeSettings} sectionId={\`admin-settings-${activeSettings.toLowerCase()}\`}
steps={tourSteps} />` is keyed on `activeSettings` specifically so switching tabs **remounts** it —
without the `key`, the same `SectionTour` instance would carry over its `run` local state
(possibly already flipped `false` from finishing/skipping the previous tab's tour) into the next
tab's `sectionId`, silently never starting that tab's tour until a manual "?" replay.

## Why It Changed?
Completes the user's explicit ask: "Configuración por cada tab que tiene."

## Verification
`cd frontend && pnpm run test:run` — 31/31 PASS (no regression; `SectionTour`'s own suite already
covers the underlying mechanics this reuses).
`cd frontend && pnpm run build` — PASS (confirms `TAB_TOUR_KEYS`'s keys are valid `admin` namespace
translation keys with no type-checking bypass).
