# Report 241: Task 3 — `POST /platform/restaurants/{id}/hub-license`

## 1. Identification
- **Report number:** 241
- **Task ID:** Task 3: hub-license issuance endpoint (third task of the 10-task
  `docs/superpowers/plans/2026-08-25-hub-license-activation/` plan)
- **Predecessor Task:** report 240 (Task 2: `LicenseIssuingService`)

## 2. Objective
Wire Task 2's `LicenseIssuingService` into the existing operator-facing tenant directory
(`PlatformRestaurantService`/`PlatformRestaurantController`) so a platform operator can issue a
signed Hub `license.key` for an existing restaurant via `POST
/platform/restaurants/{id}/hub-license`, auditing the action the same way every other operator
action here is audited.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java`
- `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`
- `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java`

## 4. What Changed?
- **`PlatformRestaurantService`:** added a `licenseIssuingService` field and a new
  `@Transactional issueHubLicense(UUID restaurantId, String operatorEmail): String` method —
  resolves the operator (`BadCredentialsException` if not found), 404s
  (`ResourceNotFoundException`) if the restaurant doesn't exist, signs the license via
  `LicenseIssuingService.issue(restaurantId)`, writes a `HUB_LICENSE_ISSUED` `PlatformAuditLog`
  row, returns the signed key.
- **`PlatformRestaurantController`:** added `POST /platform/restaurants/{id}/hub-license`
  (`produces = MediaType.TEXT_PLAIN_VALUE`), delegating to the new service method with the
  authenticated operator's email.
- **Both test files:** added the brief's specified tests verbatim — 2 new service tests
  (`issueHubLicense_returnsSignedKeyAndWritesAuditLog`,
  `issueHubLicense_throwsWhenRestaurantNotFound`) and 3 new controller tests
  (401 without auth, 200 with the plain-text license key body, 404 when the service throws
  `ResourceNotFoundException`).
- **Deviation from the brief's literal code (required to avoid a real regression):** the brief's
  exact field declaration —
  `private final com.vanter.ember.licensing.service.LicenseIssuingService licenseIssuingService;`
  under the class's existing `@RequiredArgsConstructor` — was implemented first, exactly as
  written, then found to break `SecurityAuditTest` (all 74 parameterized cases errored: full
  `ApplicationContext` refresh failure). Root cause: `LicenseIssuingService` is `@Lazy` at the
  class level (Task 2), but Spring's lazy-resolution-proxy substitution only checks the
  **injection point's** `@Lazy` annotation, not the target bean's own — and Lombok's
  `@RequiredArgsConstructor` does not copy a field-level `@Lazy` onto the generated constructor
  parameter (confirmed via `javap -v` on the compiled class: no
  `RuntimeVisibleParameterAnnotations` on the constructor at all). Since `PlatformRestaurantService`
  is an unconditional (non-`@Profile`-gated) singleton, wiring `LicenseIssuingService` into it as a
  plain constructor dependency forced Spring to eagerly construct `LicenseIssuingService` during
  every context refresh, including in the test profile — and that constructor throws
  (`PlaceholderResolutionException: Could not resolve placeholder 'HUB_LICENSE_PRIVATE_KEY'`)
  because the test `application.properties` deliberately has no such key (Task 2 confirmed this was
  intentional — "nothing should construct this bean yet"). Fixed by removing
  `@RequiredArgsConstructor` and adding an explicit constructor with `@Lazy` placed directly on the
  `licenseIssuingService` parameter — this correctly produces a deferred-resolution proxy, verified
  by re-running `SecurityAuditTest` (74/74 PASS) and the full suite (816/816 PASS, matching the
  brief's own expected count). All other 6 constructor parameters and all class behavior are
  otherwise unchanged; a short Javadoc on the constructor documents why it's manual instead of
  Lombok-generated, for the next person who touches this class.

## 5. Why It Changed?
The brief specifies the intended wiring and interface (`issueHubLicense`, the new endpoint, the
tests) precisely, and all of that was implemented verbatim. The one change beyond the brief's
literal text was not a design choice but a correctness fix: CLAUDE.md's Zero-Tolerance Build Policy
requires a clean full-suite run before a task is considered done, and the brief's own Step 7
explicitly expects 816/816 PASS — so the literal field declaration, which silently broke an
unrelated, already-passing security test via a Lombok/Spring `@Lazy` interaction gap, could not be
left as written. The fix preserves every observable behavior and interface the brief specifies
(same method signature, same call site, same test expectations) while ensuring
`LicenseIssuingService`'s real construction — and its dependency on `HUB_LICENSE_PRIVATE_KEY` —
stays deferred until an operator actually calls `issueHubLicense`, exactly as Task 2 intended when
it marked the bean `@Lazy` in the first place.

## Verification
- `cd backend && ./mvnw test -Dtest=PlatformRestaurantServiceTest,PlatformRestaurantControllerTest` — 27/27 PASS (12 + 15, incl. 2 + 3 new).
- `cd backend && ./mvnw test -Dtest=SecurityAuditTest` — 74/74 PASS (regression confirmed fixed).
- `cd backend && ./mvnw test` — 816/816 PASS, 0 failures, 0 errors.
