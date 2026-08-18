# Report 147 — task-EMB-CLP-02

**Predecessor Task:** EMB-CLP-01 (loyalty module data layer, report 146)

## Objective
Add `SettingsPayload.LoyaltySettings` (enabled/accrualMode/pointsPerVisit/pointsPerCurrencyUnit/plata-oro-platinoThreshold) and a "Fidelización" admin Settings tab so a tenant can configure its loyalty program.

## Modified Files
- `backend/src/main/java/com/vanter/ember/settings/model/SettingsPayload.java`
- `backend/src/test/java/com/vanter/ember/settings/model/SettingsPayloadTest.java`
- `frontend/src/store/uiStore.ts`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/pages/admin/Settings.tsx`
- `frontend/src/lib/api.ts`
- `frontend/src/pages/admin/components/settings/LoyaltySettings.tsx` (new)

## What Changed?
- `SettingsPayload` gained a `loyalty` field of new nested type `LoyaltySettings` (`enabled`, `accrualMode: AccrualMode` [`BY_VISIT`|`BY_AMOUNT_SPENT`], `pointsPerVisit`, `pointsPerCurrencyUnit`, `plataThreshold`/`oroThreshold`/`platinoThreshold`, all `@Min(0)`-validated, with sane defaults). The payload column is a JSON blob (`RestaurantSettings.payload`, `SqlTypes.JSON`), so no Flyway migration is needed — the field round-trips through Jackson like every other nested settings group.
- `SettingsPayloadTest` gained a default-value assertion and a full JSON round-trip test for the new `loyalty` block, mirroring the existing `paymentGateway`/`businessHours` coverage.
- Frontend: `SettingsType` gained `'FIDELIZACION'`; `SettingsBar.tsx` gained a "Fidelización" nav button (Gift icon); `Settings.tsx` routes that tab to a new `LoyaltySettings.tsx` component, built on the exact same draft/save/undo pattern as `PaymentGatewaySettings.tsx` (Switch for enable, Select for accrual mode, conditional input for the active mode's rate, 3-column threshold inputs).
- `api.ts` gained a hand-typed interim `LoyaltySettings`/`LoyaltyAccrualMode`/`SettingsResponseWithLoyalty` (intersection of the generated `SettingsResponse` with an optional `loyalty` field), since `backend-types.ts` has not been regenerated against the new backend field in this sandbox (no live backend to point `openapi-typescript` at).
- In `LoyaltySettings.tsx`, the imported type had to be aliased (`LoyaltySettings as LoyaltySettingsPayload`) — importing a type with the same name as the file's own exported component triggered `TS2395` (merged declaration must be all-exported-or-all-local), unlike the generated-schema pattern other tabs use where the type alias is declared locally rather than imported.

## Why It Changed?
Continues the EMB-CLP engine buildout (report 146: data layer) into the settings surface admins need to configure accrual before EMB-CLP-03 (tier/accrual math) and EMB-CLP-05 (accrual listener) can consume it. Single accrual mode per tenant matches the EMB-CLP design decision already recorded in `PROGRESS.md` (no dual-mode). The hand-typed frontend interim follows the established project pattern (reports 128, 134): the backend schema lands first, the user runs `pnpm run openapi` locally, and a follow-up bugfix task swaps the hand-typed shape for the generated one.

## Verification
- `./mvnw test` — exit 0, full suite green (694 tests: previous 692 + 2 new `SettingsPayloadTest` cases).
- `pnpm run build` (`tsc -b && vite build`) — exit 0, clean.

## Follow-up owed
- Regenerate `backend-types.ts` (`pnpm run openapi` against a running backend) and swap `LoyaltySettings`/`SettingsResponseWithLoyalty` in `api.ts` + `LoyaltySettings.tsx` for the generated `components['schemas']['LoyaltySettings']`, same as reports 128/134.
