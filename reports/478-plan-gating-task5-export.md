# Report 478

## 1. Identification
- **Report Number:** 478
- **Task ID:** PLAN-GATING-PHASE1 Task 5 — gate: tenant data export (PRO+)
- **Predecessor Task:** report 477 (analytics period-filters gate)

## 2. Objective
Gate the tenant data `.xlsx` export behind PRO+, per the approved plan (`docs/superpowers/plans/2026-09-14-plan-gating-phase1.md`, Task 5).

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/export/controller/ExportController.java`
- Modify: `backend/src/test/java/com/vanter/ember/export/controller/ExportControllerTest.java`
- Modify: `frontend/src/lib/planGate.ts`
- Modify: `frontend/src/lib/planGate.test.ts`
- Modify: `frontend/src/pages/admin/components/settings/ExportSettings.tsx`

## 4. What Changed?
`ExportController.exportData` resolves the tenant id once and calls `planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.PRO, "export")` before building the workbook.

**Unplanned but necessary fix, found while wiring the frontend:** `exportService.downloadTenantData` calls `GET /admin/export` with `responseType: 'blob'` (it's a file download). Under that response type, axios hands back the error body as an opaque `Blob` even for a JSON `ProblemDetail` response — the existing synchronous `extractPlanGateError` (which expects `error.response.data` to already be a parsed object) would never have detected a 402 here, silently falling through to the generic error toast and defeating the point of wiring this gate's frontend at all. Added `extractPlanGateErrorFromBlob(error): Promise<PlanGateError | null>` to `planGate.ts` — same matching logic, but `await`s `blob.text()` + `JSON.parse` first when the body is a `Blob`, otherwise falls back to reading it as a plain object. `ExportSettings.tsx`'s `onError` is now `async` and awaits it.

## 5. Why It Changed?
Fifth task of PLAN-GATING-PHASE1. The blob-body fix is a case the plan didn't anticipate (`export` is the only one of the 6 gates whose frontend call site is a file download rather than a plain JSON mutation) — caught before shipping broken UX, not after.

## 6. Verification
- TDD: `export_blockedWhenPlanBelowPro` written first, confirmed RED (200 instead of 402), GREEN after wiring. `extractPlanGateErrorFromBlob`'s 3 new cases (non-axios error, blob match, blob non-match) also RED→GREEN.
- `./mvnw test` (full backend suite) — **1289/1289** (+1 new).
- `pnpm run build` — clean. `pnpm run lint` — 0 errors. `pnpm vitest run` — **142/142** (+3 new).
- No live browser check — worth confirming next time a sub-PRO tenant is exercised live that the download button shows the upgrade toast instead of a silent/generic failure.
