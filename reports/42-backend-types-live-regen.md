# Report 42 — backend-types.ts live regeneration (not a backlog task)

**Predecessor Task:** ad-hoc fix (report 41)

## Objective
Replace task-5.2's hand-extended `backend-types.ts` with the real `pnpm run openapi` output, now that the backend boots and serves locally (reports 41).

## Modified Files
- `frontend/src/lib/backend-types.ts`

## What Changed?
Ran `pnpm run openapi` against `http://localhost:8080/v1/v3/api-docs`, replacing the full file. The hand-extended schemas from task-5.2 (`Restaurant`, `UpdateRestaurantPlanRequest`, `PublicBrandingResponse`, `PaymentGatewaySettings`, `BusinessHoursSettings`, `DaySchedule`, `TaxRule`) matched the live-generated output almost field-for-field — same names, same inlined-enum convention, same optional/required markers.

## Why It Changed?
Live codegen is authoritative; hand-extension in task-5.2 was only a stopgap because the backend couldn't boot at the time. Now that it can, this removes any residual risk of hand-typo drift.

## Verification
`pnpm run build` — PASSING (0 TS errors, `tsc -b && vite build` succeeded) against the regenerated file.
