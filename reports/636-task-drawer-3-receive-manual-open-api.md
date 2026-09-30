# Report 636

## 1. Identification
- **Report Number:** 636
- **Task ID:** EMB-DRAWER task-drawer-3 — receive / manual-open API
- **Predecessor Task:** report 635 (task-drawer-2, pending cash receipt, V20). Tasks 1-2 still uncommitted at the time of writing.

## 2. Objective
Expose the accountant/admin API that consumes the pending cash receipts: list, receive-and-open-drawer (with retry), and manual open with a mandatory reason. Give the accountant read access to the tables dashboard.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/cashregister/service/CashDrawerService.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/controller/CashDrawerController.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/dto/CashDrawerEventResponse.java`, `ManualOpenRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/session/controller/DashboardController.java`
- `backend/src/test/java/com/vanter/ember/cashregister/service/CashDrawerServiceTest.java` (new), `.../controller/CashDrawerControllerTest.java` (new)
- `backend/src/test/java/com/vanter/ember/session/controller/DashboardControllerTest.java`, `.../config/SecurityAuditTest.java`
- `PROGRESS.md`

## 4. What Changed?
- `GET /cash-drawer/current` — every pending receipt (any shift) plus the open shift's events, each with `drawer` = `NONE|OPENING|OPENED|FAILED` derived from the linked kick job.
- `POST /cash-drawer/{id}/receive` — a `PENDING` sale becomes `RECEIVED` (who/when) and a kick job is dispatched. A `RECEIVED` event whose last kick `FAILED` may be received again (retry, new job, original receiver kept). Anything else → 409, so the drawer cannot open twice for the same cash. The status change is flushed **before** the kick so a concurrent second click fails on the `@Version` check instead of sending a second pulse.
- `POST /cash-drawer/open {reason}` — reason `@NotBlank`/max 255; recorded as a `MANUAL` event with creator, time and shift. `ACCOUNTANT` needs an open shift; `ADMIN` does not.
- All three endpoints: `ACCOUNTANT` + `ADMIN`. The waiter is denied everywhere (403) — covered by a test. (The spec said receive is accountant-only; the plan widened it to admin so the owner can retry. Recorded in the plan's "Spec corrections".)
- `/dashboard/status` now also allows `ACCOUNTANT` (read-only) so the accountant can see which table is being charged.
- `SecurityAuditTest` gets the three new endpoints (unauthenticated → 401).

## 5. Why It Changed?
The accountant owns the drawer (report 473); the drawer must open only on their explicit action, tied to a specific pending sale or an audited manual reason. Retry-on-failure keeps cash traceable without a third status to keep in sync.

## 6. Verification
- `cd backend && ./mvnw test` — **1629/1629**, 0 failures (+18: 8 service, 6 controller, 1 dashboard, 3 security-audit rows). RED first (missing classes). One earlier full run had a single unrelated failure in `PortableMinioBootstrapCredentialsIntegrationTest` ("Server not initialized yet", MinIO startup timing); it passes alone and the next two full runs were clean, so it is flaky, not caused by this task.
- Not tested: real concurrency of two simultaneous `receive` calls (the flush-before-kick ordering is by design, not exercised); end-to-end drawer pulse (needs task-drawer-4 and hardware).
- Known limitations: responses expose no user names (`createdBy`/`receivedBy` are stored ids, auditable in DB); the manual-open events of an ADMIN with no open shift have no shift and so do not show in the panel.
