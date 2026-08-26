# Report 231

## 1. Identification
- **Report Number:** 231
- **Task ID:** HUB-01-09
- **Predecessor Task:** HUB-01-08 (report 230)

## 2. Objective
Ninth task of the HUB-01 plan (`docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`): add a `HandlerInterceptor` that blocks order/payment-creating endpoints with `403 license_grace_period_expired` once the license grace period (4 days, spec §2.8) has lapsed, registered only under the `hub` Spring profile.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/license/GracePeriodInterceptor.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/config/HubWebConfig.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/license/GracePeriodInterceptorTest.java` (new)

## 4. What Changed?
`GracePeriodInterceptor` loads `HubState` via `HubStateStore.load()` (throwing `IllegalStateException` if missing — startup license validation guarantees it exists before the interceptor can ever run) and delegates to `LicenseService.isWithinGracePeriod(state)`. Within grace, `preHandle` returns `true`. Past grace, it writes a `403` JSON body (`{"error":"license_grace_period_expired", "message": "..."}`) and returns `false`, blocking the request before it reaches the controller.

`HubWebConfig`, a `@Profile("hub")` `@Configuration implements WebMvcConfigurer`, builds one `GracePeriodInterceptor` instance in its constructor (from injected `LicenseService`/`HubStateStore` beans) and reuses it both as a `@Bean` and in `addInterceptors`, registered against `/sessions/*/items`, `/sessions/*/participants/*/confirm`, and `/billing/**`.

Both new production classes and the test were taken verbatim from the plan (Task 9).

## 5. Why It Changed?
Spec §2.8 requires that once the Hub can't reach the cloud (no successful heartbeat) for more than 4 days, order/payment writes must stop — read-only endpoints and existing local data stay untouched, only new writes are blocked until connectivity and a heartbeat resume. This is the enforcement point; HUB-02's sync client will be the thing that calls `LicenseService.recordHeartbeatSuccess` to keep the window open.

## Verification
- `cd backend && ./mvnw test -Dtest=GracePeriodInterceptorTest` → 2/2 PASS
- `cd backend && ./mvnw test` → 807/807 PASS (+2 from report 230's 805, as expected)
- `@Profile("hub")` on `HubWebConfig` means no existing test context loads it — default (non-hub) boot path confirmed unaffected by the full-suite run.
