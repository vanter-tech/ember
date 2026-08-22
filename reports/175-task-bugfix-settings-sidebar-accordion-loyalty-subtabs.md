# Report 175

## Identification
- **Report #:** 175
- **Task:** bugfix-settings-sidebar-accordion-loyalty-subtabs
- **Predecessor Task:** bugfix-settings-sidebar-billing-subtabs (report 174)

## Objective
Fix two gaps left by report 174: (1) "Fidelización" still stacked its settings form and rewards-catalog card together instead of exposing them as subtabs; (2) subtabs (Facturación/Fidelización) were always visible whenever the sidebar was expanded, instead of only unfolding on click and defaulting to one subtab.

## Modified Files
- `frontend/src/store/uiStore.ts`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/pages/admin/Settings.tsx`
- `frontend/src/pages/admin/components/settings/LoyaltySettings.tsx`
- `frontend/src/pages/admin/components/settings/LoyaltyRewardsSettings.tsx` (new)

## What Changed?
- `SettingsType` gained `'LOYALTY_REWARDS'`.
- `LoyaltySettings.tsx` trimmed down to only the loyalty-program settings Card (enabled toggle, accrual mode, points, tier thresholds); the rewards-catalog Card, its table, and `CreateRewardModal`/`EditRewardModal` moved out to a new `LoyaltyRewardsSettings.tsx`.
- `Settings.tsx` gained a `LOYALTY_REWARDS` case rendering `<LoyaltyRewardsSettings/>`.
- `SettingsBar.tsx` reworked into an accordion: added local `expandedGroup` state (`'BILLING' | 'FIDELIZACION' | null`, starts closed) and a `GROUP_MEMBERS` map. Clicking a group's parent button (Facturación / Fidelización) toggles its subtabs open/closed via `handleGroupClick`, and if the group's active tab isn't already one of its members, auto-selects the group's first member as the default view. Clicking any flat item (Marca y Negocio, Menú, Hardware, Espacio, Horario) closes any open group via `handleFlatClick`. Subtabs now render only when `expandedGroup` matches that group (previously they rendered whenever the sidebar was merely expanded). "Fidelización" is now itself a group with two subtabs: "Fidelización" (`FIDELIZACION`) and "Catálogo de recompensas" (`LOYALTY_REWARDS`, reusing the existing `rewardCatalogTitle` key, new `Award` icon).

## Why It Changed?
User feedback: the Fidelización tab was missed in the original subtab split, and the sidebar's subtabs should behave as a click-to-expand accordion (hidden by default, opened on click, showing a default subtab) rather than being permanently visible.

## Verification
`cd frontend && pnpm run build` → PASS (`tsc -b && vite build`, no errors; pre-existing >500kB chunk-size warning unrelated).
