# Report 219 — bugfix-settings-tour-help-button-missing

## 1. Identification
- **Report Number:** 219
- **Task ID:** bugfix-settings-tour-help-button-missing
- **Predecessor Task:** report 218 (feat-settings-tours-per-tab)

## 2. Objective
Fix TopNav's "?" (replay tour) button not appearing on `/admin/settings` on a fresh visit, reported by the user after manually testing the section-tour rollout from report 218.

## 3. Modified Files
- `frontend/src/store/uiStore.ts`

## 4. What Changed?
`useSettingsStore`'s `activeSettings` initial state changed from `null` to `'BRANDING'`.

## 5. Why It Changed?
`Settings.tsx` only renders `<SectionTour>` (and therefore only announces `activeTourSection` to `TopNav`) when `activeSettings` is truthy — `{activeSettings && <SectionTour ... />}`. Since `activeSettings` defaulted to `null` and nothing selected a tab on mount (no `useEffect` in `Settings.tsx`/`SettingsBar.tsx`, and `closeSettings` — which resets to `'BRANDING'` — is never called anywhere), a fresh visit to `/admin/settings` left no tab selected, no `SectionTour` mounted, and no "?" button, until the user manually clicked a sidebar tab. Defaulting to `'BRANDING'` matches `closeSettings`'s existing "return to BRANDING" convention and `Settings.tsx`'s own comment that BRANDING is the tab a fresh admin lands on first. `pnpm run build` PASS after the fix.
