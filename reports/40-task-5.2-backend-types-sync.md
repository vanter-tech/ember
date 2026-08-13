# Report 40 — task-5.2

**Predecessor Task:** task-5.1

## Objective
Bring `frontend/src/lib/backend-types.ts` (OpenAPI-generated) back in sync with the backend, which had drifted since task-3.8/4.3/4.4.

## Modified Files
- `frontend/src/lib/backend-types.ts`

## What Changed?
- `Restaurant`: was a stale stub (`{ id?: string }` only, predating task-2.10/4.3/4.4) — replaced with the full 7-field shape (`name`, `slug`, `plan`, `status`, `timezone`, `currency`, `createdAt`).
- Added `UpdateRestaurantPlanRequest` and `PublicBrandingResponse` (both entirely missing).
- Added `paymentGateway`/`businessHours` to `SettingsPayload`, `taxRules` to `BillingSettings`, and the new `PaymentGatewaySettings`, `BusinessHoursSettings`, `DaySchedule`, `TaxRule` schemas.
- `plan`/`status` are inlined as string-literal unions at each usage site, matching this file's existing convention for other Java enums (`Role`, `OrderItemStatus`) rather than a separate referenced schema.

## Why It Changed?
`pnpm run openapi` (the project's live-regeneration script) requires the backend running against a working Postgres/Mongo/MinIO stack; the backend currently fails to boot locally (JDBC `08001`, connection refused/misconfigured, unrelated to this task). Hand-extending from the actual Java DTOs was the fallback, keeping the generated conventions (inlined enums, `Format: uuid`/`Format: date-time` comments, required-vs-optional per validation annotations) faithful to what `openapi-typescript` would otherwise produce. Once the backend's DB connection is fixed, `pnpm run openapi` can be run to true this up automatically.

## Verification
`pnpm run build` — PASSING (0 TS errors, `tsc -b && vite build` succeeded).
