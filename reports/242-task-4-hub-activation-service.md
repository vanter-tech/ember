# Report 242 — Task 4: HubActivationService

## 1. Identification
- **Report Number:** 242
- **Task ID:** Task 4: HubActivationService
- **Predecessor:** Report 241 (Task 3: hub-license issuance endpoint)

## 2. Objective
Implement `HubActivationService` to verify a Hub license signature and resolve restaurant + admin user data for first-time Hub activation. This service answers a Hub's one-time activation call by:
1. Parsing and verifying the license signature (RSA)
2. Resolving the restaurant and its first ADMIN user
3. Enforcing server-side hardware lock (distinct activation per device)
4. Returning admin credentials to the Hub for initial login

## 3. Modified Files
**Created (4 files):**
- `backend/src/main/java/com/vanter/ember/licensing/model/dto/HubActivationRequest.java`
- `backend/src/main/java/com/vanter/ember/licensing/model/dto/HubActivationResponse.java`
- `backend/src/main/java/com/vanter/ember/licensing/service/HubActivationService.java`
- `backend/src/test/java/com/vanter/ember/licensing/service/HubActivationServiceTest.java`

## 4. What Changed?

### HubActivationRequest DTO
Request payload with two fields:
- `licenseKey` (String, @NotBlank): RSA-signed license key from the Hub
- `hardwareFingerprint` (String, @NotBlank): SHA-256 hash of CPU ID + board serial

### HubActivationResponse DTO
Response payload carrying restaurant metadata and admin user credentials:
- `name` (String): Restaurant name
- `slug` (String): Restaurant slug
- `adminName` (String): First admin user's name
- `adminEmail` (String): First admin user's email
- `adminPasswordHash` (String): Bcrypt hash for initial login

### HubActivationService (@Service)
Public method `activate(HubActivationRequest): HubActivationResponse throws InvalidLicenseException`

Flow:
1. **Parse & Verify:** Calls `LicenseKeyParser.parseAndVerify()` with the request's license key and the public key from `LicenseIssuingService`
2. **Validate Restaurant:** Looks up `Restaurant` by the license's `restaurantId`; throws `ResourceNotFoundException` if missing
3. **Enforce Hardware Lock:** Checks `HubActivationRepository.findByRestaurantId(restaurantId)`:
   - If absent: Creates new `HubActivation` record with the fingerprint and current timestamp
   - If present with same fingerprint: Idempotent — no second record written (retry safety)
   - If present with different fingerprint: Throws `IllegalStateException` with Spanish message "Esta licencia ya fue activada en otra PC."
4. **Resolve Admin:** Queries `UserRepository.findByRestaurantId_IdAndRole(restaurantId, ADMIN)`, takes the first result, throws `ResourceNotFoundException` if none
5. **Return Response:** Builder pattern response with restaurant + admin data

### HubActivationServiceTest
6 unit tests, all PASS:

1. **activate_firstTime_createsActivationAndReturnsAdminData** — Happy path: no prior activation, creates record, returns correct restaurant/admin data via ArgumentCaptor verification
2. **activate_retrySameFingerprint_doesNotCreateSecondActivation** — Idempotency: same fingerprint on second call skips `save()`, returns same response
3. **activate_differentFingerprint_throwsIllegalState** — Security: different hardware on second call throws with "otra PC" message
4. **activate_restaurantNotFound_throwsResourceNotFound** — Validation: missing restaurant throws before touching activation table
5. **activate_noAdminUser_throwsResourceNotFound** — Validation: missing admin user throws after validating restaurant
6. **activate_invalidSignature_throwsInvalidLicenseException** — Cryptography: wrong RSA key on signature verification throws

Test setup uses mocked repositories + `LicenseKeyParser.sign()` (from Task 2) to generate real test licenses signed with a random RSA keypair.

## 5. Why It Changed?

**Purpose:** Task 4 implements the critical Hub activation handshake. When a Hub boots for the first time, it:
- Presents its license key + hardware fingerprint
- Receives restaurant metadata + admin login credentials
- Caches the activation server-side to prevent license copying to another PC

**Server-side Hardware Lock Rationale:** The Hub already stores a hardware lock in `hub-state.json` (client-side, vulnerable to deletion). This service adds a second, persistent server-side lock: the same license key signed for a different PC will be rejected at the database level, not just in-memory. This prevents a copied `hub-state.json` from being sufficient to clone a Hub to another machine.

**Integration Point:** Consumed by Task 5's `HubActivationController` (endpoint not yet wired). This service is the bridge between the Hub's stateless license verification (`LicenseService`, Task 6 HUB-01) and its first-login provisioning (admin user credentials).

**Test Count:** 6 new tests added; full backend suite now 822/822 PASS (816 pre-existing + 6 new, as expected per the plan).

---

**Branch:** `emb-i18n-08`  
**System Health:** `cd backend && ./mvnw test` 822/822 PASS (6 new HubActivationServiceTest + 816 pre-existing)
