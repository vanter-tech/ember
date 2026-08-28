# Report 267 — HEARTBEAT-03: cloud `HubHeartbeatService` + DTOs

## 1. Identification
- **Report number:** 267
- **Task ID:** HEARTBEAT-03 (task 3 of 7, License Heartbeat plan `docs/superpowers/plans/2026-08-28-hub-license-heartbeat.md`)
- **Predecessor:** report 266 (HEARTBEAT-02 — `LicenseService` suspended-grace logic + `HubProperties` fields)
- **Branch:** `feat/hub-license-heartbeat`

## 2. Objective
Add the cloud-side service that answers a Hub's periodic license heartbeat: verify the signed
license, confirm the calling PC matches the `HubActivation` fingerprint recorded at activation,
read `Restaurant.status`, and return `OK` / `SUSPENDED` plus `serverTime` and an optional
`latestVersion`. No controller/security wiring yet — that is HEARTBEAT-04.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/licensing/model/dto/HubHeartbeatRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/licensing/model/dto/HubHeartbeatResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/licensing/service/HubHeartbeatService.java` (new)
- `backend/src/test/java/com/vanter/ember/licensing/service/HubHeartbeatServiceTest.java` (new)

## 4. What Changed?
- **`HubHeartbeatRequest`** — `@Data` bean, `@NotBlank String licenseKey` + `@NotBlank String
  hardwareFingerprint`. Identical shape to `HubActivationRequest`.
- **`HubHeartbeatResponse`** — `@Data @Builder`: `String status`, `Instant serverTime`,
  `String latestVersion` (nullable).
- **`HubHeartbeatService`** (`@Service`):
  - Hand-written constructor with `@Lazy LicenseIssuingService` on the constructor parameter (NOT
    `@RequiredArgsConstructor` — Lombok does not copy a field-level `@Lazy` onto its generated
    constructor, and `LicenseIssuingService` is a non-profile-gated `@Lazy @Service` that throws
    on the unset `HUB_LICENSE_PRIVATE_KEY` placeholder in every non-`hub` context; reports 241,
    243). Also takes `@Value("${hub.latest-version:}") String latestVersion`.
  - `heartbeat(HubHeartbeatRequest)`:
    1. `new LicenseKeyParser().parseAndVerify(licenseKey, licenseIssuingService.publicKey())` —
       throws `InvalidLicenseException` on bad format / bad signature.
    2. `hubActivationRepository.findByRestaurantId(...)` — `InvalidLicenseException` if no
       activation row ("Esta licencia no está activada…").
    3. Fingerprint equality check — `InvalidLicenseException` ("…activada en otra PC…").
    4. `restaurantRepository.findById(...)` — `InvalidLicenseException` if missing.
    5. `status = restaurant.getStatus() == ACTIVE ? "OK" : "SUSPENDED"`.
    6. Returns `{status, serverTime = Instant.now(), latestVersion (null when blank)}`.
  - The response deliberately carries no restaurant/admin data — only the operate/not-operate
    verdict and clock.
- **`HubHeartbeatServiceTest`** — 6 tests: active→OK (asserts `latestVersion` echoed +
  `serverTime` non-null), suspended→SUSPENDED, fingerprint mismatch→`InvalidLicenseException`,
  no activation row→`InvalidLicenseException`, unknown restaurant→`InvalidLicenseException`,
  garbage license→`InvalidLicenseException`. Uses a real `KeyPairGenerator` RSA pair —
  `LicenseKeyParser.sign(...)` with the private half, `licenseIssuingService.publicKey()`
  stubbed to the public half — with mocked `HubActivationRepository` / `RestaurantRepository`.

### Plan drift
The plan's verbatim test omitted `throws` on the two happy-path methods
(`heartbeat_activeRestaurant_returnsOk`, `heartbeat_suspendedRestaurant_returnsSuspended`), which
call `service.heartbeat(...)` directly — `InvalidLicenseException` is checked, so the file did
not compile. Added `throws InvalidLicenseException` to those two methods. No change to the
`assertThatThrownBy` tests (lambda body, already fine).

## 5. Why It Changed?
Sub-project A1 (License Heartbeat) makes the Hub's 4-day offline grace reset on cloud contact and
lets a `/console` suspension stop a Hub after a 48h courtesy grace. This task is the cloud
endpoint's core logic, split from its HTTP/security shell (HEARTBEAT-04) so the verification
rules are unit-tested in isolation. Layering the server-side fingerprint check on top of
`LicenseService`'s client-side hardware lock means deleting `hub-state.json` locally cannot
defeat the "one activated PC" rule.

## 6. Verification
- `cd backend && ./mvnw test -Dtest=HubHeartbeatServiceTest` → **6/6 PASS**.
- `cd backend && ./mvnw test` (full suite) → **882/882 PASS**, 0 failures / 0 errors (876 prior +
  6 new). No context-load regression from the `@Lazy` constructor placement.
