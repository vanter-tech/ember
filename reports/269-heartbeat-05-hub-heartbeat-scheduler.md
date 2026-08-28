# Report 269 — HEARTBEAT-05: Hub `HeartbeatScheduler` + `@EnableScheduling`

## 1. Identification
- **Report number:** 269
- **Task ID:** HEARTBEAT-05 (fifth task of the 7-task License Heartbeat plan, `docs/superpowers/plans/2026-08-28-hub-license-heartbeat.md`)
- **Predecessor Task:** HEARTBEAT-04 (report 268 — cloud-side `HubHeartbeatController`)
- **Branch:** `feat/hub-license-heartbeat`

## 2. Objective
Add the Hub-side periodic license heartbeat: a `@Profile("hub")` `@Scheduled` component that POSTs
the signed license key + hardware fingerprint to the cloud `/hub-heartbeat` endpoint on a fixed
delay, resets the offline-grace clock on `OK`, starts the suspended-courtesy-grace clock on
`SUSPENDED`, and swallows every exception so Spring never stops scheduling it.

## 3. Modified Files
- **Create:** `backend/src/main/java/com/vanter/ember/hub/sync/HeartbeatScheduler.java`
- **Create:** `backend/src/test/java/com/vanter/ember/hub/sync/HeartbeatSchedulerTest.java`
- **Modify:** `backend/src/main/java/com/vanter/ember/hub/config/HubBeansConfig.java`
- **Modify:** `backend/src/main/resources/application-hub.yml`

## 4. What Changed?
- **`HeartbeatScheduler`** — plain `@Component @Profile("hub")`, constructor
  `(HubProperties, HubStateStore, LicenseService)`. Package-private `runHeartbeat()` annotated
  `@Scheduled(fixedDelayString = "${ember.hub.heartbeat-interval-ms:300000}")`:
  - no-op (debug log) if `HubProperties.heartbeatUrl()` is blank;
  - no-op (warn log) if `HubStateStore.load()` is empty;
  - otherwise reads `license.key`, builds `{licenseKey, hardwareFingerprint}` JSON, POSTs with a
    10s connect / 15s request timeout via `java.net.http.HttpClient`;
  - non-200 → warn log, return (state untouched);
  - 200 + `status:"OK"` → `LicenseService.recordHeartbeatSuccess(state)`;
  - 200 + `status:"SUSPENDED"` → `LicenseService.recordSuspended(state)`;
  - unrecognised status → warn log only;
  - `IOException`/`InterruptedException` (re-interrupts) and any `RuntimeException` are caught and
    logged inside the method — nothing escapes.
  - Private records `HeartbeatRequestBody` / `HeartbeatResponseBody`; a lenient `ObjectMapper`
    (`FAIL_ON_UNKNOWN_PROPERTIES=false`) so an extra response field never throws.
- **`HubBeansConfig`** — added `@EnableScheduling` (+ import). Hub profile only, so the tenant/cloud
  context never starts a scheduler.
- **`application-hub.yml`** — added `ember.hub.heartbeat-interval-ms: ${EMBER_HUB_HEARTBEAT_INTERVAL_MS:300000}`
  (5-minute default). The interval lives only as a Spring property, not a `HubProperties` field,
  because `@Scheduled(fixedDelayString=...)` needs a resolvable placeholder and cannot read a plain
  record (deliberate deviation, per plan header).
- **`HeartbeatSchedulerTest`** — 4 tests using a real `com.sun.net.httpserver.HttpServer` on an
  ephemeral port and a real `LicenseService`/`HubStateStore` writing through a `@TempDir`:
  `OK` bumps `lastHeartbeatAt` + clears `suspendedSince`; `SUSPENDED` stamps `suspendedSince` +
  leaves `lastHeartbeatAt`; HTTP 500 leaves state untouched; blank URL is a no-op.

## 5. Why It Changed?
Per spec `docs/superpowers/specs/2026-08-28-hub-license-heartbeat-design.md` §4.3, the Hub must
phone home on a schedule so the cloud can (a) let a genuinely-offline Hub keep running inside the
4-day offline grace and (b) push a `SUSPENDED` verdict that starts a 48-hour courtesy window before
the `GracePeriodInterceptor` blocks (HEARTBEAT-06). Exception containment is mandatory: an exception
thrown out of a `@Scheduled` method makes Spring cancel all future executions of it, which would
silently disable the heartbeat forever after one transient network blip.

No plan drift — all consumed signatures (`HubProperties` 13-arg canonical ctor, `LicenseService`
6-arg ctor, `HubStateStore(Path)`, `HubState` 4-arg ctor, `recordHeartbeatSuccess`/`recordSuspended`)
matched the current codebase verbatim.

## 6. Verification
- `cd backend && ./mvnw test -Dtest=HeartbeatSchedulerTest` → **4/4 PASS** (RED before implementation:
  `cannot find symbol HeartbeatScheduler`).
- `cd backend && ./mvnw test` → **889/889 PASS**, 0 failures / 0 errors (885 prior + 4 new).
