# Report 230 — HUB-01-08: `HubBeansConfig` (profile-gated Spring beans)

## 1. Identification
- **Report:** 230
- **Task ID:** HUB-01-08 (Task 8 of `docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`)
- **Predecessor Task:** HUB-01-07 (report 229)

## 2. Objective
Expose the pre-Spring Hub license/bootstrap services (`HubProperties`, `HardwareFingerprintService`,
`HubStateStore`, `LicenseKeyParser`, `LicenseService`) as `@Profile("hub")`-gated Spring `@Bean`s, so
Task 9 (`GracePeriodInterceptor`) and Task 10 (`HubTrayIcon`) can consume them via normal DI instead
of manual construction.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/config/HubBeansConfig.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/config/HubBeansConfigTest.java` (new)

## 4. What Changed?
Added `HubBeansConfig`, a `@Configuration @Profile("hub")` class with 5 `@Bean` methods:
`hubProperties()` (`HubProperties.fromEnvironment()`), `hardwareFingerprintService()`,
`hubStateStore(HubProperties)`, `licenseKeyParser()`, and `licenseService(...)` (loads the RSA public
key and wires the 4 other beans into a `LicenseService`). Taken verbatim from the plan except one
addition: `licenseService` is annotated `@Lazy`.

`HubBeansConfigTest` (2 tests, taken verbatim from the plan) confirms the 4 file-independent beans
register under `spring.profiles.active=hub` and that none of the hub beans exist under the default
profile.

## 5. Why It Changed?
The plan's test runs `ApplicationContextRunner` against `HubBeansConfig` and only asserts on
`HubProperties`/`HardwareFingerprintService`/`HubStateStore`/`LicenseKeyParser` — deliberately
avoiding `LicenseService` since that bean needs a real `license.key`/public-key file on disk (not
available until Task 11's manual Windows verification). But `ApplicationContextRunner.run()` performs
a full context refresh, which eagerly instantiates **every** non-lazy singleton bean in the
configuration regardless of what the test asserts on — so `licenseService()`'s factory method ran
during context startup and threw `InvalidLicenseException` (no `hub-public-key.der` exists anywhere
in the repo, correctly — bundling those files is HUB-03's job), failing
`hubProfile_registersFingerprintAndStateBeans` even though it never touches `LicenseService`.

Fixed by adding `@Lazy` to the `licenseService` bean method: defers its instantiation until an actual
consumer requests it. This is safe for production too — Task 7's pre-Spring `bootstrapHub()` already
validates the license before Spring even starts, so the Spring-managed `licenseService` bean has zero
consumers until Task 9/10 land; by the time those inject it, real files exist. Confirmed via
`./mvnw test -Dtest=HubBeansConfigTest` (2/2 PASS) and the full suite (805/805 PASS, up from 803 —
the 2 new tests, no regressions).
