# Report 361 — FIX-QA branch: remediation of QA_SIMULATION_REPORT.md

## 1. Identification
- **Report:** 361
- **Task:** FIX-QA — fix every error in `QA_SIMULATION_REPORT.md` (E-01..E-23), user-requested
- **Predecessor:** report 360 (move quick-login PIN to admin-only, `feat/waiter-quick-login-table-actions`)
- **Branch:** `FIX-QA`, created off `feat/waiter-quick-login-table-actions` @ `b8fd7b0`

## 2. Objective
`QA_SIMULATION_REPORT.md` (live-executed QA simulation of the 4 roles, 2026-09-04) surfaced 23
numbered findings (E-01..E-23) ranging from Bloqueante (cross-tenant data leak, duplicate
payments, a customer-facing crash) to Baja (console warnings, icon semantics). The user asked to
create a `FIX-QA` branch and fix each one. This report covers 22 of the 23 — E-23 is deliberately
deferred (see §6).

## 3. Modified Files
Backend (`backend/src/main/java/com/vanter/ember/...`):
- `config/JwtChannelInterceptor.java`, `config/SecurityConfig.java`, `config/RateLimitProperties.java`
- `session/controller/SessionController.java`, `session/controller/DashboardController.java`, `session/service/SessionService.java`
- `settings/controller/SettingsController.java`
- `billing/service/PaymentService.java`, `billing/service/BillingService.java`
- `resources/application.yml`

Backend tests: `config/JwtChannelInterceptorTest.java`, `session/controller/{SessionControllerTest,DashboardControllerTest}.java`, `session/service/SessionServiceTest.java`, `settings/controller/SettingsControllerTest.java` (new), `billing/service/{PaymentServiceTest,BillingServiceTest}.java`.

Frontend (`frontend/src/...`):
- `App.tsx`
- `pages/customer/components/ItemsFloatingIsland.tsx` (+ new `.test.tsx`), `pages/customer/ComandaView.tsx`
- `pages/waiter/TableInformation.tsx`, `pages/waiter/cashRegister/components/CloseShiftDialog.tsx`, `pages/waiter/components/WaiterTour.tsx` (+ `.test.tsx`)
- `pages/admin/staff/components/{EditStaffModal,StaffCard,StaffGrid}.tsx` (+ `EditStaffModal.test.tsx`), `pages/admin/staff/Staff.tsx`
- `pages/admin/Category.tsx`, `pages/admin/components/settings/BrandingSettings.tsx`
- `pages/auth/QuickLoginModal.tsx` (+ `.test.tsx`)
- `store/uiStore.ts`, `components/CashShiftSentinel.tsx`
- `locales/{es,en}/{waiter,customer,admin}.ts`

Reports/docs: this file; `PROGRESS.md`.

## 4. What Changed? (12 commits, chronological)

| Commit | Findings | Summary |
|---|---|---|
| `19039af` | E-01, E-02, E-06, E-07, E-08, E-09, E-10 | STOMP `SUBSCRIBE` now validates destination tenant + role (`JwtChannelInterceptor`); `PUT /settings` is ADMIN-only; NPE fixes in `confirmDraftsForUser`/`removeItem` (participantId-null ordering bugs); `closeEmptySession`/`addItemAsWaiter` now require the assigned waiter; `GET /dashboard/status` is WAITER/ADMIN-only. |
| `6240b96` | E-04 | `registerPhysicalPayment`/`initiateDigitalPayment` reject once a split is no longer `UNPAID`; digital payment also rejects a second concurrent pending intent and locks the bill row. |
| `73892ab` | E-05 | `BillingService.calculateBill`/`splitByConsumption` apply the tenant's real configured tax rate; `TableInformation.tsx`/`ComandaView.tsx` read the same rate instead of a hardcoded 10%; `GET /settings` opened to CUSTOMER. |
| `dd530a5` | E-03 | `ItemsFloatingIsland`'s `state.items \|\| []` selector (fresh array every render → infinite Zustand/React loop) fixed with a stable `EMPTY_ITEMS` constant; regression test added. |
| `def4420` | E-11 | `<Route index>` added under `/admin`, `/waiter`, `/kitchen`, `/customer` — no more blank screen after login. |
| `0af802f` | E-14, E-15, E-16 | Default profile's `actuator.health.show-details` → `never`; `/sessions/join` and (later, `0eafea5`) `/printing/agents/token` added to the auth rate limiter; `/sessions/{id}/status` removed from `permitAll`. |
| `7e1d8bf` | E-13 | `EditStaffModal` reads the live `['staff']` query cache by id instead of the frozen `modalPayload` snapshot, so the PIN badge reflects a just-saved PIN immediately. |
| `738fd74` | E-12 | Staff card's "⋯" (MoreHorizontal) → `UserX` + honest "Deactivate"/"Desactivar" label; `onOpenActions` renamed `onDeactivate` end to end. |
| `898eff0` | E-18 (partial), E-19, E-20 | `Category.tsx`'s list `key` moved to the mapped `<Link>`; `BrandingSettings.tsx`'s time inputs no longer mix `value`+`defaultValue`; `CloseShiftDialog`'s description wired to a real `DialogDescription`. |
| `81942af` | E-21 | `QuickLoginModal` no longer shows the account's email pre-authentication; shows role instead, matching the chip grid. |
| `0eafea5` | E-22 | `/printing/agents/token` added to the rate limiter. |
| `469d9fa` | E-17 | New `useUIStore.cashShiftAlertOpen` flag, set by `CashShiftSentinel`; `WaiterTour` holds off starting while it's true — no more stacked overlays on first `/waiter/tables` render. |

## 5. Why It Changed?
Every fix traces to a specific finding in `QA_SIMULATION_REPORT.md`, most of which were
**reproduced live** against a running backend+frontend during that QA session (not just inferred
from code reading) — see that report's "Comportamiento Obtenido" column per finding for the exact
repro steps and observed evidence (HTTP responses, stack traces, screenshots). Full rationale for
each fix is in its own commit message (`git log FIX-QA`), which quotes the specific live evidence
that justified it.

## 6. Deliberately not fixed: E-23
`QA_SIMULATION_REPORT.md` E-23 (PIN-login account-enumeration oracle: `401` unknown email vs.
`409 PIN_NOT_SET` vs. `423 PIN_LOCKED`) was **not** fixed. The distinguishable responses are
consumed on purpose by `QuickLoginModal` to give a legitimate user a helpful message ("no tienes
PIN configurado, usa tu contraseña") instead of a generic failure. Collapsing the three cases to
one generic 401 closes the enumeration oracle but breaks that UX; keeping them open leaves the
oracle. This is a genuine product trade-off, not a one-line fix, and needs a decision from
whoever owns this UX before it's changed either direction.

## 7. Verification
Run after every commit in this table, not just once at the end:
- Backend: `./mvnw test` — **1038/1038**, 0 failures/errors (started at 1036 pre-branch after the
  branch's own `SettingsControllerTest` addition; net +2 after `E-16`'s new status-route tests).
- Frontend: `pnpm run build` (0 TS errors), `pnpm run lint` (0 errors, 16 pre-existing warnings,
  unchanged), `pnpm run test:run` — **73/73** across 23 files (up from 68/68 pre-branch).
- No manual/live browser re-verification was performed in this session (unlike the original QA
  simulation) — the fixes are covered by the automated suites above; a follow-up live smoke test
  (the same style as `QA_SIMULATION_REPORT.md`'s) is recommended before merging to confirm the 5
  Bloqueante findings (E-01, E-02, E-03, E-04, E-05) are actually closed end-to-end, not just at
  the unit/integration-test level.

## 8. Not in scope of this branch
- `AUDIT_BLUEPRINT.md`'s F-18 (stale `/api/**`-prefixed rows in `SecurityAuditTest`) and other
  static-audit-only findings not carried into `QA_SIMULATION_REPORT.md`'s numbered matrix.
- Deep architectural change to `PrintAgentService.authenticateByApiKey`'s O(N) BCrypt scan
  (E-22's rate limit mitigates but doesn't remove it).
- E-23 (see §6).
