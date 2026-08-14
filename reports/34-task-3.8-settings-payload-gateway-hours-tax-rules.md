## Identification
- **Report:** 34
- **Task ID:** task-3.8
- **Predecessor Task:** task-3.7

## Objective
Extend `SettingsPayload` with `PaymentGatewaySettings` (secret-reference pattern, never raw secrets), a structured `BusinessHoursSettings`, and list-based `TaxRules`.

## Modified Files
- `backend/src/main/java/com/vanter/ember/settings/model/SettingsPayload.java`
- `backend/src/test/java/com/vanter/ember/settings/model/SettingsPayloadTest.java` (new)

## What Changed?
- Added `PaymentGatewaySettings` (`enabled`, `provider`, `publicKey`, `secretRef`) as a new top-level field (`paymentGateway`) on `SettingsPayload`. There is no field capable of holding a raw API secret/private key — only an opaque `secretRef` string intended to name an externally-resolved value (env var / secrets-manager key).
- Added `BusinessHoursSettings` (a `List<DaySchedule>`, each with `day` (`java.time.DayOfWeek`), `closed`, `openTime`, `closeTime`) as a new top-level field (`businessHours`), additive alongside the existing flat `branding.openingTime`/`branding.closingTime` (left untouched — `BrandingSettings.tsx` still reads those directly).
- Added `TaxRule` (`name`, `rate` bounded `0`–`100` via `@Min`/`@Max`, `includedInPrice`) and a new `List<TaxRule> taxRules` field on the existing `BillingSettings`, alongside the untouched scalar `taxRate`.
- Added `SettingsPayloadTest`: verifies the new nested objects default to non-null/empty (no accidental raw-secret defaults) and that `paymentGateway`, `businessHours`, and `billing.taxRules` round-trip correctly through Jackson JSON (the shape actually persisted, since `RestaurantSettings.payload` is stored as JSON).

## Why It Changed?
`RestaurantSettings.payload` persists `SettingsPayload` as a JSON column, so the DTO *is* the schema — new settings categories are additive fields rather than migrations. The gateway settings needed a structural guarantee (not just a naming convention) that a raw secret can never be persisted in tenant-editable JSON: the class simply has no field shaped to hold one. Business hours and tax rules were kept additive rather than replacing existing `branding`/`billing` fields to avoid an unrequested, breaking change to the still-wired frontend forms (`BrandingSettings.tsx`) and the OpenAPI-generated `backend-types.ts`; wiring the frontend to the new structures is out of scope for this task.

## Verification
`cd backend && ./mvnw test` — full suite passed, 431 run / 0 failures / 0 errors (includes the 2 new `SettingsPayloadTest` cases).
