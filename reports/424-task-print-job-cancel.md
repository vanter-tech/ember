# Report 424 — Cancel stuck print jobs

## 1. Identification
- **Report number:** 424
- **Current Task ID:** PRINT-JOB-CANCEL (ad-hoc, not a milestone in the EMB-PRINT-AGENT backlog)
- **Predecessor Task:** report 423 — EMB-PRINT-AGENT T7 (manual installer verification checklist)

## 2. Objective
Give admins/waiters a way to stop print jobs that are stuck `PENDING`. A job with no
connected agent stays `PENDING` forever, and `PrintDispatchService.flushPendingFor` replays
**every** `PENDING` job onto its printer each time an agent reconnects — so an out-of-paper
printer keeps being handed the same old tickets. There was no UI action for `PENDING` jobs
(only `ERROR` jobs had "Reimprimir").

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/printing/model/PrintJobStatus.java`
- `backend/src/main/java/com/vanter/ember/printing/repository/PrintJobRepository.java`
- `backend/src/main/java/com/vanter/ember/printing/service/PrintDispatchService.java`
- `backend/src/main/java/com/vanter/ember/printing/controller/PrintJobController.java`
- `backend/src/test/java/com/vanter/ember/printing/service/PrintDispatchServiceTest.java`
- `backend/src/test/java/com/vanter/ember/printing/controller/PrintJobControllerTest.java`
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`
- `frontend/src/lib/api.ts`
- `frontend/src/lib/backend-types.ts`
- `frontend/src/pages/admin/components/settings/PrintingSettings.tsx`
- `frontend/src/pages/admin/components/settings/PrintingSettings.test.tsx`
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`

## 4. What Changed?

### Backend
- `PrintJobStatus` gains a `CANCELED` value (`PENDING, SENT, PRINTED, ERROR, CANCELED`).
- `PrintJobRepository` gains `findByTenantIdAndStatus(UUID, PrintJobStatus)` — same
  tenant-explicit style as the existing `findByTenantIdAndStatusOrderByCreatedAtDesc`.
- `PrintDispatchService`:
  - `cancel(tenantId, jobId)` — tenant-checked (`ResourceNotFoundException` on mismatch);
    rejects anything that is not `PENDING`/`ERROR` with `IllegalStateException` (→ 409 via
    `GlobalExceptionHandler`); sets `status = CANCELED` + `updatedAt`.
  - `cancelAllPending(tenantId)` — flips every `PENDING` job for the tenant to `CANCELED`
    in one `saveAll`, returns the count.
  - `flushPendingFor` is unchanged — it scans `findByStatus(PENDING)`, so `CANCELED` jobs
    are naturally excluded from the reconnect replay.
- `PrintJobController` adds `POST /printing/jobs/{id}/cancel` and
  `POST /printing/jobs/cancel-pending`, both `@PreAuthorize("hasAnyRole('ADMIN','WAITER')")`
  (mirrors `retry`).
- Tests: `PrintDispatchServiceTest` +4 (cancel happy path / rejects `PRINTED` / rejects
  other tenant / bulk marks all pending), `PrintJobControllerTest` +2 (cancel as WAITER,
  cancel-pending as ADMIN), `SecurityAuditTest` +2 CSV rows (both new routes require auth).

### Frontend
- `printingService.cancelJob(id)` and `cancelPendingJobs()` in `api.ts`.
- `backend-types.ts`: `CANCELED` added to the `list` query `status` enum (hand-patched,
  consistent with the project's current no-full-regen practice).
- `PrintingSettings.tsx` — "Trabajos recientes" card:
  - Header shows a **"Limpiar pendientes"** button when any job is `PENDING`.
  - Each `PENDING`/`ERROR` row shows a ghost **"Cancelar"** button (`Ban` icon); `ERROR`
    rows keep "Reimprimir" alongside it.
  - `CANCELED` rows render greyed with a "Cancelado" label and hide the stale `lastError`.
- i18n ES/EN: `printingCancelJobButton`, `printingClearPendingButton`,
  `printingJobCanceledStatus`.
- `PrintingSettings.test.tsx` +1 (cancel a pending job, then clear all pending).

## 5. Why It Changed?
- **`CANCELED` status rather than row delete** — keeps the audit trail of a job that
  existed, and dropping out of the `flushPendingFor` scan is automatic because that query
  is status-filtered. No migration needed (string enum, no DB check constraint).
- **`PENDING`/`ERROR` only** — `SENT`/`PRINTED` jobs already left the backend; cancelling
  them client-side would be misleading. `IllegalStateException` → 409 is the existing
  contract for that class of guard.
- **ADMIN + WAITER** — a waiter watching the pass needs to clear a jammed queue without
  finding an admin; identical to who can hit `retry`.
- **Bulk "Limpiar pendientes"** — the reported pain is a *pile* of pending tickets after a
  printer sat out of paper; cancelling them one by one is the wrong ergonomics.

## Verification
- `cd backend && ./mvnw test` → **1216/1216** (was 1208; +8).
- `cd frontend && pnpm run build` → clean (`tsc -b` + vite).
- `cd frontend && pnpm run test:run` → **121/121** (was 120; +1).
- `cd frontend && pnpm run lint` → 0 errors, 16 pre-existing warnings.
