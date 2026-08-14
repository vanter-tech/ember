# Report 47 — Wire BILLING Settings Tab

**Report Number:** 47
**Task ID:** task-5.5
**Predecessor Task:** task-5.4 (report 46)

## Objective
Wire the `BILLING` tab in `Settings.tsx` (previously a `<div>Billing Settings</div>` placeholder) to `SettingsPayload.billing` (`currencySymbol`, `taxRate`, `taxIncludeInMenuPrice`, `suggestedTipPercentage`) plus a `TaxRule[]` list editor.

## Modified Files
- `frontend/src/pages/admin/components/settings/BillingSettings.tsx` (new)
- `frontend/src/pages/admin/Settings.tsx`

## What Changed?
- Added `BillingSettings.tsx`, following the `MenuSettings.tsx`/`SpaceSettings.tsx` pattern (`useQuery`/`useMutation` via `SettingsService.getSettings`/`updateSettings`, single draft-object state with undo/save).
- `currencySymbol` and `taxRate` bound via text/number `Input`; `taxIncludeInMenuPrice` bound via `Switch` (backend field is `taxIncludeInMenuPrice`, not `isTaxIncludeInMenuPrice` as informally phrased in the backlog).
- `suggestedTipPercentage` (`number[]`) rendered as removable percentage tags with an add input/button.
- `taxRules` (`TaxRule[]`, task-3.8) rendered as a row editor (name/rate/includedInPrice) with add/remove controls.
- `Settings.tsx`'s `BILLING` case now renders `<BillingSettings />` instead of the placeholder `<div>`.

## Why It Changed?
`SettingsPayload.billing` has existed on the backend since task-3.8 but had no frontend surface — the `BILLING` tab was still a stub. This closes that gap using the same `SettingsService`/react-query draft pattern already established for `BRANDING`/`SPACE`/`MENU`, keeping consistency across settings tabs.

## Verification
`cd frontend && pnpm run build` — passed, 0 TypeScript errors.
