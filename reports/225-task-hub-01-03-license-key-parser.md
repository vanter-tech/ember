# Report 225

## 1. Identification
- **Report Number:** 225
- **Task ID:** HUB-01-03
- **Predecessor Task:** HUB-01-02 (report 224)

## 2. Objective
Add the RSA-signed `license.key` data model and (de)serialization/verification logic for Ember Hub, per Task 3 of `docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/license/LicenseKey.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/license/InvalidLicenseException.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/license/LicenseKeyParser.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/license/LicenseKeyParserTest.java` (new)

## 4. What Changed?
- `LicenseKey`: a record `(UUID restaurantId, Instant issuedAt)`.
- `InvalidLicenseException`: checked exception for any license parsing/verification failure.
- `LicenseKeyParser`: parses/verifies a `license.key` file whose contents are `base64(payloadJson) + "." + base64(signature)`, signature algorithm `SHA256withRSA` over the raw payload bytes. Provides `parseAndVerify(String, PublicKey): LicenseKey`, a static `sign(LicenseKey, PrivateKey): String` admin-side helper, and a static `loadPublicKey(Path): PublicKey`. Uses a private nested `LicensePayload` record + Jackson `ObjectMapper` (with `JavaTimeModule`) for JSON (de)serialization.
- `LicenseKeyParserTest`: 3 cases — sign/verify round-trip, rejection of a signature from a mismatched key pair, rejection of malformed content. All taken verbatim from the plan.

## 5. Why It Changed?
Third task of the HUB-01 bootstrap-and-licensing plan for the offline Ember Hub deployment mode. This is the core license file format/verification primitive that `LicenseService` (Task 5) and `EmberApplication.main` (Task 7) will consume to gate hub startup against a valid, hardware-matching license.

## Verification
`cd backend && ./mvnw test -Dtest=LicenseKeyParserTest` — 3/3 PASS.
`cd backend && ./mvnw test` (full suite) — PASS, 793/793 (up from 790/790 after report 224).
