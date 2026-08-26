## 1. Identification
- **Report number:** 244
- **Task ID:** Task 6: HubProvisioningRunner
- **Predecessor Task:** report 243

## 2. Objective
Add the Hub-side (client) piece of the license-activation pipeline: an `ApplicationRunner` that
runs once per boot on the Hub profile, calls the cloud's `POST /hub-activations` endpoint (Task 5)
exactly once on first boot ever, and seeds the local Postgres with the `Restaurant` + admin `User`
returned by that call — reusing the license's `restaurantId` as the entity's own id. On every
later boot the runner is a pure no-op (zero network calls) since the restaurant already exists
locally.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java` (modified — added
  `activationUrl` field + `EMBER_HUB_ACTIVATION_URL` env read)
- `backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java` (modified — added
  assertion for the new field's default)
- `backend/src/main/java/com/vanter/ember/hub/provisioning/HubProvisioningException.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/provisioning/HubProvisioningRunner.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/provisioning/HubProvisioningRunnerTest.java` (new)

## 4. What Changed?
`HubProperties` (a Spring-free record, constructed before Spring context startup — see its own
class javadoc) gained an 8th field, `activationUrl`, read from `EMBER_HUB_ACTIVATION_URL` with a
`""` fallback. `HubPropertiesTest` got one new assertion (`activationUrl()` is empty by default)
appended to the existing `fromEnvironment_usesDefaultsWhenNoEnvVarsSet` test method.

New package `com.vanter.ember.hub.provisioning`:
- `HubProvisioningException extends RuntimeException` — the single failure type this runner (and,
  per the brief, Task 7's broadened `HubDashboard` catch clause) throws for any activation problem.
- `HubProvisioningRunner implements ApplicationRunner`, `@Component @Profile("hub")`. On `run()`:
  loads `HubState` from `HubStateStore` (throws `IllegalStateException` if missing — should be
  impossible post-license-validation); if `RestaurantRepository.existsById(state.restaurantId())`
  is true, logs and returns (no-op path); otherwise requires a non-blank `activationUrl` (else
  throws `HubProvisioningException` naming `EMBER_HUB_ACTIVATION_URL`), reads the license file,
  gets the hardware fingerprint, POSTs `{licenseKey, hardwareFingerprint}` as JSON to the
  activation URL via `java.net.http.HttpClient`, and on a non-200 or unreachable-server response
  wraps the failure as `HubProvisioningException` (network failure message is in Spanish,
  mentioning "conexión a internet", matching the rest of the Hub's user-facing error strings). On
  success, builds `Restaurant` with the license's `restaurantId` as its own `@Id` (Hibernate's
  `GenerationType.UUID` respects a pre-assigned id) and saves it, then builds and saves the admin
  `User` (role `ADMIN`) linked to that restaurant, using the response body's
  name/slug/adminName/adminEmail/adminPasswordHash fields.

All code — `HubProperties`, its test, `HubProvisioningException`, `HubProvisioningRunner`, and
`HubProvisioningRunnerTest` — was taken verbatim from the task brief; no drift was found against
the current signatures of `HubStateStore.load()`, `HardwareFingerprintService.currentFingerprint()`,
`Restaurant`/`User`'s Lombok `@Builder`, or `RestaurantRepository`/`UserRepository`.

## 5. Why It Changed?
Closes the real gap flagged at the end of report 238: there was no code path from "a Hub license
gets activated" to "a Restaurant + admin User exist in the Hub's own local Postgres." Task 5
(report 243) built the cloud-side endpoint that verifies the license and hands back restaurant +
admin credentials; this task is the Hub-side caller that invokes it exactly once on first boot,
persists the result locally, and stays silent on every subsequent boot. `HubProvisioningException`
exists as a single, narrow exception type so Task 7 can broaden `HubDashboard`'s existing catch
clause to also treat "activation failed" as a normal, retryable, non-crashing boot failure — same
UX contract already established for license/DB bootstrap failures.

## Verification
- `cd backend && ./mvnw test -Dtest=HubPropertiesTest,HubProvisioningRunnerTest` → 4/4 PASS (1 +
  3, run synchronously, blocking).
- `cd backend && ./mvnw test` (full suite, synchronous) → **830/830 PASS, 0 failures/errors**
  (827 pre-existing + 3 new `HubProvisioningRunnerTest` methods — matches the brief's own
  predicted total exactly; `HubProvisioningException` has no dedicated test file and the
  `HubPropertiesTest` change is one new assertion inside an existing test method, so neither adds
  to the count, as the brief anticipated). Note: report 243 recorded its own full-suite baseline
  as 828/828 ("one more than the brief's predicted 827 — a pre-existing unrelated test in this
  environment"); this run's 830 total implies a 827 baseline instead. The discrepancy is between
  two *baseline* readings taken in different sessions, not a regression introduced by this task —
  this run itself is 100% green with no failures or errors, and 827 unmodified pre-existing tests
  are exactly what one would expect if report 243's "+1" observation was itself sensitive to some
  environment-specific factor (e.g. a conditionally-run test) rather than a fixed count. Flagged
  here for visibility, not treated as a blocker.

## 6. Fix Wave (post-review, same task)

Code review of this task found two Important gaps in the plan brief's own literal code (not an
implementation error against the brief — the brief itself omitted these), both accepted and fixed
in-place rather than left open:

**Fix 1 — success path had zero test coverage.** All 3 original `HubProvisioningRunnerTest`
methods ended before ever calling `restaurantRepository.save()`/`userRepository.save()`. Added a
4th test, `run_successfulActivation_savesRestaurantAndAdminUser`, using a local
`com.sun.net.httpserver.HttpServer` (JDK-included, no new dependency) bound to an ephemeral port
(`new InetSocketAddress("localhost", 0)`, port read back via `stubServer.getAddress().getPort()`)
serving a canned 200 JSON response at `/hub-activations`. A fresh `HubProperties` points
`activationUrl` at that stub server (same construction pattern as the existing tests). Asserts via
`ArgumentCaptor<Restaurant>`/`ArgumentCaptor<User>` that the saved `Restaurant` has
`id == state.restaurantId()` and the response's `name`/`slug`, and the saved `User` has
`restaurantId(savedRestaurant)` (via `isSameAs`), `role == ADMIN`, and the untouched
`adminName`/`adminEmail`/`adminPasswordHash` from the response body. The stub server is stopped in
a new `@AfterEach` (`stubServer.stop(0)`, guarded null-check) so it never leaks between test runs.

**Fix 2 — no connect/request timeout on the outbound HTTP call.** `HubProvisioningRunner`'s
`HttpClient.newHttpClient().send(...)` had no `connectTimeout` and the request had no
`.timeout(...)`. Since this `ApplicationRunner` runs synchronously during Spring context refresh,
a real-world restrictive firewall/proxy that silently drops packets (rather than actively
refusing) could hang the entire boot forever with no exception and no boot-error message —
contradicting every other Hub boot-error path's "actionable message, clean non-zero exit, never a
silent hang" convention (see PROGRESS.md report-236). Fixed by building the client with
`HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()` and adding
`.timeout(Duration.ofSeconds(15))` to the `HttpRequest.newBuilder()` chain. No catch-block change
needed: a timeout throws `HttpTimeoutException` (an `IOException` subtype), already covered by the
existing `catch (IOException | InterruptedException e)` that wraps it into the same
`HubProvisioningException` "no se pudo conectar..." message.

### Re-verification (both fixes, synchronous, blocking)
- `cd backend && ./mvnw test -Dtest=HubPropertiesTest,HubProvisioningRunnerTest` → **5/5 PASS**
  (1 `HubPropertiesTest` + 4 `HubProvisioningRunnerTest`, including the new success-path test
  against the real `HttpServer` stub).
- `cd backend && ./mvnw test` (full suite) → **831/831 PASS, 0 failures/errors** (830 prior total
  + 1 new test method; no regressions from either fix).

### Files touched by the fix wave (same task, folded into the same commit set)
- `backend/src/main/java/com/vanter/ember/hub/provisioning/HubProvisioningRunner.java` (added
  `Duration` import, `HttpClient.newBuilder().connectTimeout(...)`, `.timeout(...)` on the request)
- `backend/src/test/java/com/vanter/ember/hub/provisioning/HubProvisioningRunnerTest.java` (added
  `run_successfulActivation_savesRestaurantAndAdminUser` + `@AfterEach` teardown + new imports)
