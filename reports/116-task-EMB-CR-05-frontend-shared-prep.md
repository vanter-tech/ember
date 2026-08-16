# Report 116: task-EMB-CR-05

## Identification
- **Report:** 116
- **Task ID:** EMB-CR-05
- **Predecessor Task:** none (first frontend-track task of the EMB-CR backlog; backend track EMB-CR-01..04 runs in parallel, independently)

## Objective
Add the frontend shared prep for the Cash Register & Daily Shift Management module: a `formatCurrency` utility, the three shadcn components the module's UI needs (`Tabs`, `Select`, `AlertDialog`), the `cashShiftService` API client with its hand-written types, and the new `uiStore` modal types.

## Modified Files
- Create: `frontend/src/lib/format.ts`
- Create: `frontend/src/lib/format.test.ts`
- Create (shadcn CLI): `frontend/src/components/ui/tabs.tsx`
- Create (shadcn CLI): `frontend/src/components/ui/select.tsx`
- Create (shadcn CLI): `frontend/src/components/ui/alert-dialog.tsx`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/store/uiStore.ts`

## What Changed?
- `formatCurrency(value: number): string` — `Intl.NumberFormat('en-US', {style:'currency',currency:'USD'})`-based helper, replacing the ad hoc `$${value.toFixed(2)}` pattern used elsewhere in the codebase (not retrofitted onto existing call sites — out of scope for this module).
- Ran `npx shadcn@latest add tabs select alert-dialog` (declining the prompt to overwrite the already-present `button.tsx` dependency) — added `tabs.tsx`, `select.tsx`, `alert-dialog.tsx` under `components/ui/`, matching the project's `radix-nova` style per `components.json`. No existing UI component was modified.
- `api.ts` gained: `CashShiftLifecycleStatus`, `CashMovementType`, `CashShiftResponse`, `CashMovementResponse`, `CashShiftDetailResponse`, `DailyReportResponse` (hand-written interfaces — see rationale below), and `cashShiftService` (`open`, `current`, `history`, `detail`, `recordMovement`, `close`, `dailyReport`), following the existing plain-object-of-async-functions service convention over the shared `api` axios instance.
- `uiStore.ts`'s `ModalType` union gained `'OPEN_SHIFT' | 'CASH_MOVEMENT' | 'CLOSE_SHIFT'`.

## Why It Changed?
Per the design spec (`docs/superpowers/specs/2026-08-16-cash-register-shift-management-design.md`) and implementation plan (`docs/superpowers/plans/2026-08-16-cash-register-shift-management.md`, Task 5): this is the shared groundwork both the waiter operate page (EMB-CR-06) and admin oversight page (EMB-CR-07) build on, so it lands first as its own reviewable, buildable increment.

`cashShiftService`'s types are hand-written rather than `components['schemas'][...]` aliases because regenerating `backend-types.ts` requires `pnpm run openapi` against a live backend, which isn't available while the backend track (EMB-CR-01..04) is landing in parallel in this same working tree. This is called out explicitly in `api.ts` as a follow-up: regenerate and swap to schema aliases once a live backend exists, matching every other service in the file.

A frontend test runner (`vitest`, plus one existing smoke test) was found to already exist in this repo, contrary to what earlier planning/exploration had assumed — so `formatCurrency` was built via real TDD (RED: `pnpm exec vitest run src/lib/format.test.ts` failed to resolve the not-yet-existing module; GREEN: same command, 4/4 passing) rather than the plan's build-only fallback path.

## Verification
- `pnpm exec vitest run src/lib/format.test.ts` — 4/4 passing.
- `pnpm run build` — passed (`tsc -b && vite build`, no new errors; pre-existing large-chunk warning unrelated to this change).
