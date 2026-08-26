# Hub License Activation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a Vanter operator issue a real `license.key` for an existing restaurant from `/console`, and let a Hub instance use that license to fetch its `Restaurant` + admin `User` (same password hash as the cloud) from the cloud exactly once, seeding its local Postgres so it works fully offline afterward.

**Architecture:** Two new backend pieces sharing `LicenseKeyParser` (unchanged, HUB-01-03): a cloud-side `com.vanter.ember.licensing` package (always active, no profile) that signs licenses and answers activation requests, and a Hub-side `com.vanter.ember.hub.provisioning` package (`@Profile("hub")`) that calls the activation endpoint once and seeds the local DB via a `Spring ApplicationRunner`. Two small frontend changes complete the loop: a "Emitir licencia Hub" button in `/console`, and hiding "Registrarse" on `Login.tsx` for the Hub build.

**Tech Stack:** Java 17 / Spring Boot 3.5.14 / Spring Data JPA / Flyway (backend, unchanged from the rest of the project); `java.net.http.HttpClient` (JDK, no new dependency) for the Hub's one outbound call; React 19 / TanStack Query / axios (frontend, unchanged).

**Spec:** `docs/superpowers/specs/2026-08-25-hub-license-activation-design.md`

## Global Constraints

- No new Maven or npm dependencies — every piece uses libraries already in this project (Jackson, JDK `HttpClient`, existing Lombok/JPA/axios/TanStack Query conventions).
- `HUB_LICENSE_PRIVATE_KEY` has no fallback default (fail-fast boot), matching every other secret in `.env.example`.
- Every new Java file follows this codebase's existing module shape: `model` / `model/dto` / `repository` / `service` / `controller` sub-packages (see `com.vanter.ember.platform.*` for the exact precedent).
- The admin's password hash is copied as-is between cloud and Hub — never re-hashed, never transmitted in plaintext.
- `/app` (the bundled Hub frontend prefix, report 238) and `/hub-activations` (this plan) must never collide with any real `@RequestMapping` — verify with `grep -rn '@RequestMapping' backend/src/main/java` before adding any new top-level path.

---

### Task 1: `HubActivation` entity, repository, and migration

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/licensing/model/HubActivation.java`
- Create: `backend/src/main/java/com/vanter/ember/licensing/repository/HubActivationRepository.java`
- Create: `backend/src/main/resources/db/migration/V2__hub_activations.sql`
- Test: `backend/src/test/java/com/vanter/ember/licensing/repository/HubActivationRepositoryTest.java`

**Interfaces:**
- Produces: `HubActivation` (fields: `UUID id`, `UUID restaurantId`, `String hardwareFingerprint`, `Instant activatedAt`, Lombok `@Builder`/`@Data`), `HubActivationRepository.findByRestaurantId(UUID): Optional<HubActivation>` — both consumed by Task 4's `HubActivationService`.

- [ ] **Step 1: Write the entity**

```java
package com.vanter.ember.licensing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Server-side record of a Hub activation — lets {@code HubActivationService} tell a legitimate
 * retry from the same PC (e.g. after a wiped local DB) apart from a license copied onto a
 * different machine, on top of the client-side hardware lock {@code LicenseService} already does
 * locally (which a deleted {@code hub-state.json} can bypass).
 */
@Entity
@Table(name = "hub_activations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HubActivation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    private UUID restaurantId;

    @Column(name = "hardware_fingerprint", nullable = false)
    private String hardwareFingerprint;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;
}
```

- [ ] **Step 2: Write the repository**

```java
package com.vanter.ember.licensing.repository;

import com.vanter.ember.licensing.model.HubActivation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HubActivationRepository extends JpaRepository<HubActivation, UUID> {
    Optional<HubActivation> findByRestaurantId(UUID restaurantId);
}
```

- [ ] **Step 3: Write the Flyway migration**

```sql
CREATE TABLE hub_activations (
    id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    hardware_fingerprint varchar(255) NOT NULL,
    activated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_hub_activations PRIMARY KEY (id),
    CONSTRAINT uk_hub_activations_restaurant_id UNIQUE (restaurant_id)
);
```

- [ ] **Step 4: Write the repository test**

```java
package com.vanter.ember.licensing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.licensing.model.HubActivation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class HubActivationRepositoryTest {

    @Autowired HubActivationRepository repository;

    @Test
    void findByRestaurantId_returnsEmptyWhenNoneExists() {
        assertThat(repository.findByRestaurantId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByRestaurantId_returnsSavedActivation() {
        UUID restaurantId = UUID.randomUUID();
        repository.save(HubActivation.builder()
                .restaurantId(restaurantId)
                .hardwareFingerprint("fp-1")
                .activatedAt(Instant.now())
                .build());

        assertThat(repository.findByRestaurantId(restaurantId))
                .isPresent()
                .get()
                .extracting(HubActivation::getHardwareFingerprint)
                .isEqualTo("fp-1");
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HubActivationRepositoryTest`
Expected: 2/2 PASS. (`@DataJpaTest` uses H2 with `ddl-auto=create-drop`, so the new entity is enough — the Postgres migration is exercised the next time the real backend boots.)

- [ ] **Step 6: Run the full suite to confirm no regression**

Run: `cd backend && ./mvnw test`
Expected: 809/809 PASS (807 + 2 new).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/licensing/model/HubActivation.java \
        backend/src/main/java/com/vanter/ember/licensing/repository/HubActivationRepository.java \
        backend/src/main/resources/db/migration/V2__hub_activations.sql \
        backend/src/test/java/com/vanter/ember/licensing/repository/HubActivationRepositoryTest.java
git commit -m "feat(licensing): add HubActivation entity and repository"
```

---

### Task 2: `LicenseIssuingService` (signs licenses, derives the matching public key)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/licensing/service/LicenseIssuingService.java`
- Test: `backend/src/test/java/com/vanter/ember/licensing/service/LicenseIssuingServiceTest.java`
- Modify: `backend/src/main/resources/application.yml` (add `hub.license.private-key`)
- Modify: `.env.example` (add `HUB_LICENSE_PRIVATE_KEY` + document the generation command)

**Interfaces:**
- Consumes: `com.vanter.ember.hub.license.LicenseKey` (record: `restaurantId`, `issuedAt`), `com.vanter.ember.hub.license.LicenseKeyParser.sign(LicenseKey, PrivateKey): String` — both unchanged from HUB-01-03.
- Produces: `LicenseIssuingService.issue(UUID restaurantId): String` and `LicenseIssuingService.publicKey(): PublicKey` — both consumed by Task 3 (issuance endpoint) and Task 4 (`HubActivationService`).

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.licensing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vanter.ember.hub.license.LicenseKey;
import com.vanter.ember.hub.license.LicenseKeyParser;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LicenseIssuingServiceTest {

    @Test
    void issue_producesLicenseKeyVerifiableWithDerivedPublicKey() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        LicenseIssuingService service = new LicenseIssuingService(privateKeyBase64);
        UUID restaurantId = UUID.randomUUID();

        String licenseKeyContents = service.issue(restaurantId);

        LicenseKey parsed = new LicenseKeyParser().parseAndVerify(licenseKeyContents, service.publicKey());
        assertThat(parsed.restaurantId()).isEqualTo(restaurantId);
    }

    @Test
    void constructor_throwsOnMalformedKey() {
        assertThatThrownBy(() -> new LicenseIssuingService("not-valid-base64!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("private-key");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=LicenseIssuingServiceTest`
Expected: FAIL — `LicenseIssuingService` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

```java
package com.vanter.ember.licensing.service;

import com.vanter.ember.hub.license.LicenseKey;
import com.vanter.ember.hub.license.LicenseKeyParser;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Signs {@code license.key} files for {@code /console}'s "Emitir licencia Hub" action and
 * verifies them again for {@code /hub-activations} — both directions share one key pair, so this
 * is the single source of truth for it. The public half is derived from the private key at
 * startup (RSA private keys generated by Java's default provider are CRT keys, which expose the
 * modulus/public exponent needed to reconstruct the public key) — no separate public key config
 * to keep in sync. The corresponding {@code hub-public-key.der} every Hub install ships with is a
 * one-time manual export from the SAME key pair (see the generation command in `.env.example`),
 * unrelated to this derivation.
 */
@Service
public class LicenseIssuingService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public LicenseIssuingService(@Value("${hub.license.private-key}") String privateKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateCrtKey =
                    (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            this.privateKey = privateCrtKey;
            this.publicKey = factory.generatePublic(
                    new RSAPublicKeySpec(privateCrtKey.getModulus(), privateCrtKey.getPublicExponent()));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException(
                    "hub.license.private-key inválida: no se pudo cargar como clave RSA PKCS8.", e);
        }
    }

    public String issue(UUID restaurantId) {
        try {
            return LicenseKeyParser.sign(new LicenseKey(restaurantId, Instant.now()), privateKey);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("No se pudo firmar la licencia.", e);
        }
    }

    public PublicKey publicKey() {
        return publicKey;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=LicenseIssuingServiceTest`
Expected: 2/2 PASS.

- [ ] **Step 5: Wire the config property and the `.env` secret**

In `backend/src/main/resources/application.yml`, add this block right after the existing `platform:` block (before `minio:`):

```yaml
hub:
  license:
    # RSA private key (base64 PKCS8 DER) that signs every Hub license.key issued from /console.
    # No fallback — see .env.example for how to generate it.
    private-key: ${HUB_LICENSE_PRIVATE_KEY}
```

In `.env.example`, add `HUB_LICENSE_PRIVATE_KEY` to the header comment's fail-fast list (edit the existing line to read `SPRING_DATASOURCE_PASSWORD / JWT_SECRET / PLATFORM_JWT_SECRET / MINIO_ACCESS_KEY / MINIO_SECRET_KEY / HUB_LICENSE_PRIVATE_KEY have no in-code fallback...`), and append this block after the `PLATFORM_JWT_EXPIRATION_MS` line:

```
# Ember Hub licensing — RSA private key (base64-encoded PKCS8 DER) that signs every license.key
# issued from /console. Generate ONCE, keep secret, never commit or share outside this file:
#   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -outform DER | base64 -w0
# The matching public key ships to every Hub install as hub-public-key.der (HUB-01) — a separate,
# one-time manual export from this same key pair, done outside the app:
#   openssl genpkey ... | openssl rsa -pubout -outform DER -out hub-public-key.der
HUB_LICENSE_PRIVATE_KEY=
```

- [ ] **Step 6: Run the full suite to confirm no regression**

Run: `cd backend && ./mvnw test`
Expected: 811/811 PASS (809 + 2 new). `HUB_LICENSE_PRIVATE_KEY` being empty in test's `application.yml` profile is fine — nothing constructs `LicenseIssuingService` as a Spring bean yet (that starts in Task 3), so the empty property is never resolved into the constructor this task.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/licensing/service/LicenseIssuingService.java \
        backend/src/test/java/com/vanter/ember/licensing/service/LicenseIssuingServiceTest.java \
        backend/src/main/resources/application.yml \
        .env.example
git commit -m "feat(licensing): add LicenseIssuingService signing/public-key derivation"
```

---

### Task 3: `POST /platform/restaurants/{id}/hub-license` (issuance endpoint)

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- Modify: `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java`
- Modify: `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java`

**Interfaces:**
- Consumes: `LicenseIssuingService.issue(UUID): String` (Task 2).
- Produces: `PlatformRestaurantService.issueHubLicense(UUID restaurantId, String operatorEmail): String` — not consumed elsewhere in this plan, but this is the method Task 8's frontend button calls via the new controller endpoint.

- [ ] **Step 1: Add the service method**

In `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`, add a new field (next to the other `@RequiredArgsConstructor`-managed fields):

```java
    private final com.vanter.ember.licensing.service.LicenseIssuingService licenseIssuingService;
```

Then add this method (place it after `create`, at the end of the class before the closing brace):

```java
    /**
     * Operator-driven license issuance: signs a {@code license.key} for a restaurant that already
     * exists (created via {@link #create}), and audits it the same way as every other operator
     * action here.
     */
    @Transactional
    public String issueHubLicense(UUID restaurantId, String operatorEmail) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(operatorEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException("Restaurant not found: " + restaurantId);
        }

        String licenseKey = licenseIssuingService.issue(restaurantId);

        platformAuditLogRepository.save(PlatformAuditLog.builder()
                .operatorId(operator.getId())
                .operatorEmail(operator.getEmail())
                .restaurantId(restaurantId)
                .action("HUB_LICENSE_ISSUED")
                .build());

        return licenseKey;
    }
```

- [ ] **Step 2: Add the service test**

In `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`, add the mock field next to the others:

```java
    @Mock com.vanter.ember.licensing.service.LicenseIssuingService licenseIssuingService;
```

Then add these two tests (anywhere inside the class, e.g. after `create_throwsWhenOperatorNotFound`):

```java
    @Test
    void issueHubLicense_returnsSignedKeyAndWritesAuditLog() {
        PlatformOperator operator = PlatformOperator.builder()
                .id(UUID.randomUUID())
                .email("operator@ember.local")
                .build();
        UUID restaurantId = UUID.randomUUID();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator));
        when(restaurantRepository.existsById(restaurantId)).thenReturn(true);
        when(licenseIssuingService.issue(restaurantId)).thenReturn("signed-license-key");

        String result = platformRestaurantService.issueHubLicense(restaurantId, "operator@ember.local");

        assertThat(result).isEqualTo("signed-license-key");

        ArgumentCaptor<com.vanter.ember.platform.model.PlatformAuditLog> captor =
                ArgumentCaptor.forClass(com.vanter.ember.platform.model.PlatformAuditLog.class);
        org.mockito.Mockito.verify(platformAuditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("HUB_LICENSE_ISSUED");
        assertThat(captor.getValue().getRestaurantId()).isEqualTo(restaurantId);
    }

    @Test
    void issueHubLicense_throwsWhenRestaurantNotFound() {
        PlatformOperator operator = PlatformOperator.builder()
                .id(UUID.randomUUID())
                .email("operator@ember.local")
                .build();
        UUID restaurantId = UUID.randomUUID();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator));
        when(restaurantRepository.existsById(restaurantId)).thenReturn(false);

        assertThatThrownBy(() -> platformRestaurantService.issueHubLicense(restaurantId, "operator@ember.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
```

- [ ] **Step 3: Run the service test**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantServiceTest`
Expected: all PASS (existing tests + 2 new).

- [ ] **Step 4: Add the controller endpoint**

In `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java`, add the import:

```java
import org.springframework.http.MediaType;
```

Then add this method (place it after `create`):

```java
    @Operation(summary = "Issue a Hub license.key for this restaurant")
    @PostMapping(value = "/{id}/hub-license", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> issueHubLicense(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(platformRestaurantService.issueHubLicense(id, authentication.getName()));
    }
```

- [ ] **Step 5: Add the controller test**

In `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java`, add these tests (anywhere inside the class):

```java
    @Test
    void issueHubLicense_returns401WithoutAuthHeader() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/platform/restaurants/" + id + "/hub-license"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issueHubLicense_returns200WithLicenseKeyText() throws Exception {
        authenticate();
        UUID id = UUID.randomUUID();
        when(platformRestaurantService.issueHubLicense(id, OPERATOR_EMAIL)).thenReturn("signed-license-key");

        mockMvc.perform(post("/platform/restaurants/" + id + "/hub-license")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("signed-license-key"));
    }

    @Test
    void issueHubLicense_returns404WhenRestaurantNotFound() throws Exception {
        authenticate();
        UUID id = UUID.randomUUID();
        when(platformRestaurantService.issueHubLicense(id, OPERATOR_EMAIL))
                .thenThrow(new ResourceNotFoundException("Restaurant not found: " + id));

        mockMvc.perform(post("/platform/restaurants/" + id + "/hub-license")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }
```

- [ ] **Step 6: Run the controller test**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantControllerTest`
Expected: all PASS (existing tests + 3 new).

- [ ] **Step 7: Run the full suite to confirm no regression**

Run: `cd backend && ./mvnw test`
Expected: 816/816 PASS (811 + 2 service + 3 controller).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java \
        backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java \
        backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java \
        backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java
git commit -m "feat(platform): add POST /platform/restaurants/{id}/hub-license"
```

---

### Task 4: `HubActivationService` (verify + provision-lookup logic)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/licensing/model/dto/HubActivationRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/licensing/model/dto/HubActivationResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/licensing/service/HubActivationService.java`
- Test: `backend/src/test/java/com/vanter/ember/licensing/service/HubActivationServiceTest.java`

**Interfaces:**
- Consumes: `RestaurantRepository` (`findById`, unchanged), `UserRepository.findByRestaurantId_IdAndRole(UUID, Role)` (unchanged), `HubActivationRepository` (Task 1), `LicenseIssuingService.publicKey()` (Task 2), `com.vanter.ember.hub.license.LicenseKeyParser.parseAndVerify(String, PublicKey): LicenseKey throws InvalidLicenseException` (unchanged, HUB-01-03).
- Produces: `HubActivationService.activate(HubActivationRequest): HubActivationResponse throws InvalidLicenseException` — consumed by Task 5's controller.

- [ ] **Step 1: Write the request/response DTOs**

```java
package com.vanter.ember.licensing.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HubActivationRequest {

    @NotBlank(message = "licenseKey is required")
    private String licenseKey;

    @NotBlank(message = "hardwareFingerprint is required")
    private String hardwareFingerprint;
}
```

```java
package com.vanter.ember.licensing.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HubActivationResponse {
    private String name;
    private String slug;
    private String adminName;
    private String adminEmail;
    private String adminPasswordHash;
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.vanter.ember.licensing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.hub.license.LicenseKey;
import com.vanter.ember.hub.license.LicenseKeyParser;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.licensing.model.dto.HubActivationRequest;
import com.vanter.ember.licensing.model.dto.HubActivationResponse;
import com.vanter.ember.licensing.repository.HubActivationRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HubActivationServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @Mock UserRepository userRepository;
    @Mock HubActivationRepository hubActivationRepository;

    private KeyPair keyPair;
    private LicenseIssuingService licenseIssuingService;
    private HubActivationService hubActivationService;
    private UUID restaurantId;
    private String signedLicenseKey;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        licenseIssuingService =
                new LicenseIssuingService(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        hubActivationService = new HubActivationService(
                restaurantRepository, userRepository, hubActivationRepository, licenseIssuingService);
        restaurantId = UUID.randomUUID();
        signedLicenseKey = LicenseKeyParser.sign(new LicenseKey(restaurantId, Instant.now()), keyPair.getPrivate());
    }

    private HubActivationRequest request(String fingerprint) {
        HubActivationRequest request = new HubActivationRequest();
        request.setLicenseKey(signedLicenseKey);
        request.setHardwareFingerprint(fingerprint);
        return request;
    }

    private Restaurant restaurant() {
        return Restaurant.builder().id(restaurantId).name("Tenant Grill").slug("tenant-grill").build();
    }

    private User admin() {
        return User.builder()
                .id("u-1")
                .name("Owner Admin")
                .email("owner@tenant-grill.local")
                .passwordHash("bcrypt-hash")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    void activate_firstTime_createsActivationAndReturnsAdminData() throws Exception {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant()));
        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.empty());
        when(userRepository.findByRestaurantId_IdAndRole(restaurantId, Role.ADMIN)).thenReturn(List.of(admin()));

        HubActivationResponse result = hubActivationService.activate(request("fp-1"));

        assertThat(result.getName()).isEqualTo("Tenant Grill");
        assertThat(result.getSlug()).isEqualTo("tenant-grill");
        assertThat(result.getAdminEmail()).isEqualTo("owner@tenant-grill.local");
        assertThat(result.getAdminPasswordHash()).isEqualTo("bcrypt-hash");

        org.mockito.ArgumentCaptor<HubActivation> captor = org.mockito.ArgumentCaptor.forClass(HubActivation.class);
        org.mockito.Mockito.verify(hubActivationRepository).save(captor.capture());
        assertThat(captor.getValue().getRestaurantId()).isEqualTo(restaurantId);
        assertThat(captor.getValue().getHardwareFingerprint()).isEqualTo("fp-1");
    }

    @Test
    void activate_retrySameFingerprint_doesNotCreateSecondActivation() throws Exception {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant()));
        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.of(
                HubActivation.builder().restaurantId(restaurantId).hardwareFingerprint("fp-1")
                        .activatedAt(Instant.now()).build()));
        when(userRepository.findByRestaurantId_IdAndRole(restaurantId, Role.ADMIN)).thenReturn(List.of(admin()));

        HubActivationResponse result = hubActivationService.activate(request("fp-1"));

        assertThat(result.getAdminEmail()).isEqualTo("owner@tenant-grill.local");
        org.mockito.Mockito.verify(hubActivationRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void activate_differentFingerprint_throwsIllegalState() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant()));
        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.of(
                HubActivation.builder().restaurantId(restaurantId).hardwareFingerprint("fp-1")
                        .activatedAt(Instant.now()).build()));

        assertThatThrownBy(() -> hubActivationService.activate(request("fp-2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("otra PC");
    }

    @Test
    void activate_restaurantNotFound_throwsResourceNotFound() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hubActivationService.activate(request("fp-1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void activate_noAdminUser_throwsResourceNotFound() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant()));
        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.empty());
        when(userRepository.findByRestaurantId_IdAndRole(restaurantId, Role.ADMIN)).thenReturn(List.of());

        assertThatThrownBy(() -> hubActivationService.activate(request("fp-1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void activate_invalidSignature_throwsInvalidLicenseException() throws Exception {
        KeyPair otherKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        HubActivationRequest badRequest = new HubActivationRequest();
        badRequest.setLicenseKey(
                LicenseKeyParser.sign(new LicenseKey(restaurantId, Instant.now()), otherKeyPair.getPrivate()));
        badRequest.setHardwareFingerprint("fp-1");

        assertThatThrownBy(() -> hubActivationService.activate(badRequest))
                .isInstanceOf(com.vanter.ember.hub.license.InvalidLicenseException.class);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HubActivationServiceTest`
Expected: FAIL — `HubActivationService` doesn't exist yet (compile error).

- [ ] **Step 4: Write the implementation**

```java
package com.vanter.ember.licensing.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.hub.license.LicenseKey;
import com.vanter.ember.hub.license.LicenseKeyParser;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.licensing.model.dto.HubActivationRequest;
import com.vanter.ember.licensing.model.dto.HubActivationResponse;
import com.vanter.ember.licensing.repository.HubActivationRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers a Hub's one-time activation call: verifies the license signature, resolves the
 * restaurant + its first ADMIN user, and enforces the server-side hardware lock (a defense the
 * client-side {@code hub-state.json} lock can't provide on its own — deleting that file resets
 * nothing server-side).
 */
@Service
@RequiredArgsConstructor
public class HubActivationService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final HubActivationRepository hubActivationRepository;
    private final LicenseIssuingService licenseIssuingService;

    @Transactional
    public HubActivationResponse activate(HubActivationRequest request) throws InvalidLicenseException {
        LicenseKey licenseKey = new LicenseKeyParser()
                .parseAndVerify(request.getLicenseKey(), licenseIssuingService.publicKey());
        UUID restaurantId = licenseKey.restaurantId();

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        Optional<HubActivation> existing = hubActivationRepository.findByRestaurantId(restaurantId);
        if (existing.isPresent()) {
            if (!existing.get().getHardwareFingerprint().equals(request.getHardwareFingerprint())) {
                throw new IllegalStateException("Esta licencia ya fue activada en otra PC.");
            }
        } else {
            hubActivationRepository.save(HubActivation.builder()
                    .restaurantId(restaurantId)
                    .hardwareFingerprint(request.getHardwareFingerprint())
                    .activatedAt(Instant.now())
                    .build());
        }

        User admin = userRepository.findByRestaurantId_IdAndRole(restaurantId, Role.ADMIN).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant " + restaurantId + " has no ADMIN user"));

        return HubActivationResponse.builder()
                .name(restaurant.getName())
                .slug(restaurant.getSlug())
                .adminName(admin.getName())
                .adminEmail(admin.getEmail())
                .adminPasswordHash(admin.getPasswordHash())
                .build();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HubActivationServiceTest`
Expected: 6/6 PASS.

- [ ] **Step 6: Run the full suite to confirm no regression**

Run: `cd backend && ./mvnw test`
Expected: 822/822 PASS (816 + 6 new).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/licensing/model/dto/HubActivationRequest.java \
        backend/src/main/java/com/vanter/ember/licensing/model/dto/HubActivationResponse.java \
        backend/src/main/java/com/vanter/ember/licensing/service/HubActivationService.java \
        backend/src/test/java/com/vanter/ember/licensing/service/HubActivationServiceTest.java
git commit -m "feat(licensing): add HubActivationService"
```

---

### Task 5: `POST /hub-activations` (public endpoint) + `SecurityConfig` + `GlobalExceptionHandler`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/licensing/controller/HubActivationController.java`
- Test: `backend/src/test/java/com/vanter/ember/licensing/controller/HubActivationControllerTest.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `HubActivationService.activate(HubActivationRequest): HubActivationResponse throws InvalidLicenseException` (Task 4).
- Produces: `POST /hub-activations` (public HTTP endpoint) — consumed by Task 6's `HubProvisioningRunner`.

- [ ] **Step 1: Add the `InvalidLicenseException` mapping to `GlobalExceptionHandler`**

In `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`, add this method right after `handleInvalidModifierSelection` (matches its fully-qualified-inline style, no new top-level import needed):

```java
    @ExceptionHandler(com.vanter.ember.hub.license.InvalidLicenseException.class)
    public ProblemDetail handleInvalidLicense(
            com.vanter.ember.hub.license.InvalidLicenseException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }
```

- [ ] **Step 2: Add the public route to `SecurityConfig`**

In `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`, in the `authorizeHttpRequests` block, add this line right after the `/app/**` line added in report 238 (before `.anyRequest().authenticated()`):

```java
                        // The Hub's one-time activation call — authenticates via the license
                        // signature itself (HubActivationService), not a bearer token.
                        .requestMatchers("/hub-activations").permitAll()
```

- [ ] **Step 3: Write the failing controller test**

```java
package com.vanter.ember.licensing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.licensing.model.dto.HubActivationResponse;
import com.vanter.ember.licensing.service.HubActivationService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HubActivationController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class HubActivationControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean HubActivationService hubActivationService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private static final String VALID_BODY =
            "{\"licenseKey\":\"abc.def\",\"hardwareFingerprint\":\"fp-1\"}";

    @Test
    void activate_withNoAuthHeader_stillReaches200() throws Exception {
        when(hubActivationService.activate(any())).thenReturn(HubActivationResponse.builder()
                .name("Tenant Grill")
                .slug("tenant-grill")
                .adminName("Owner Admin")
                .adminEmail("owner@tenant-grill.local")
                .adminPasswordHash("bcrypt-hash")
                .build());

        mockMvc.perform(post("/hub-activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-grill"))
                .andExpect(jsonPath("$.adminPasswordHash").value("bcrypt-hash"));
    }

    @Test
    void activate_returns400OnMissingFields() throws Exception {
        mockMvc.perform(post("/hub-activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activate_returns400OnInvalidLicense() throws Exception {
        when(hubActivationService.activate(any()))
                .thenThrow(new InvalidLicenseException("La firma de license.key no es válida."));

        mockMvc.perform(post("/hub-activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activate_returns409OnAlreadyActivatedElsewhere() throws Exception {
        when(hubActivationService.activate(any()))
                .thenThrow(new IllegalStateException("Esta licencia ya fue activada en otra PC."));

        mockMvc.perform(post("/hub-activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void activate_returns404WhenRestaurantNotFound() throws Exception {
        when(hubActivationService.activate(any()))
                .thenThrow(new ResourceNotFoundException("Restaurant not found"));

        mockMvc.perform(post("/hub-activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HubActivationControllerTest`
Expected: FAIL — `HubActivationController` doesn't exist yet (compile error).

- [ ] **Step 5: Write the controller**

```java
package com.vanter.ember.licensing.controller;

import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.licensing.model.dto.HubActivationRequest;
import com.vanter.ember.licensing.model.dto.HubActivationResponse;
import com.vanter.ember.licensing.service.HubActivationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public — the Hub authenticates via the license signature itself, not a bearer token. */
@RestController
@RequestMapping("/hub-activations")
@RequiredArgsConstructor
public class HubActivationController {

    private final HubActivationService hubActivationService;

    @PostMapping
    public ResponseEntity<HubActivationResponse> activate(@Valid @RequestBody HubActivationRequest request)
            throws InvalidLicenseException {
        return ResponseEntity.ok(hubActivationService.activate(request));
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HubActivationControllerTest`
Expected: 5/5 PASS.

- [ ] **Step 7: Run the full suite to confirm no regression**

Run: `cd backend && ./mvnw test`
Expected: 827/827 PASS (822 + 5 new).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/licensing/controller/HubActivationController.java \
        backend/src/test/java/com/vanter/ember/licensing/controller/HubActivationControllerTest.java \
        backend/src/main/java/com/vanter/ember/config/SecurityConfig.java \
        backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java
git commit -m "feat(licensing): add public POST /hub-activations endpoint"
```

---

### Task 6: `HubProvisioningRunner` (Hub-side, seeds the local DB)

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java` (add `activationUrl`)
- Modify: `backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/provisioning/HubProvisioningException.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/provisioning/HubProvisioningRunner.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/provisioning/HubProvisioningRunnerTest.java`

**Interfaces:**
- Consumes: `POST /hub-activations` (Task 5, over HTTP — this task's test mocks the repositories instead of hitting a real server), `HubStateStore.load(): Optional<HubState>` (unchanged, HUB-01-04), `HardwareFingerprintService.currentFingerprint(): String` (unchanged, HUB-01-02), `RestaurantRepository`/`UserRepository` (unchanged).
- Produces: `HubProvisioningException extends RuntimeException` — consumed by Task 7's broadened `HubDashboard` catch clause.

- [ ] **Step 1: Add `activationUrl` to `HubProperties`**

In `backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java`, add the field to the record and its `fromEnvironment()` construction:

```java
public record HubProperties(
        Path dataDir,
        Path postgresBinDir,
        Path licenseFile,
        Path publicKeyFile,
        Path stateFile,
        int postgresPort,
        int serverPort,
        String activationUrl) {

    public static HubProperties fromEnvironment() {
        return new HubProperties(
                Path.of(env("EMBER_HUB_DATA_DIR", "./data/postgres")),
                Path.of(env("EMBER_HUB_POSTGRES_BIN_DIR", "./postgres/bin")),
                Path.of(env("EMBER_HUB_LICENSE_FILE", "./license.key")),
                Path.of(env("EMBER_HUB_PUBLIC_KEY_FILE", "./hub-public-key.der")),
                Path.of(env("EMBER_HUB_STATE_FILE", "./hub-state.json")),
                Integer.parseInt(env("EMBER_HUB_POSTGRES_PORT", "5432")),
                Integer.parseInt(env("EMBER_HUB_SERVER_PORT", "8080")),
                env("EMBER_HUB_ACTIVATION_URL", ""));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value != null ? value : fallback;
    }
}
```

- [ ] **Step 2: Update `HubPropertiesTest`**

In `backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java`, add this line after the existing `serverPort()` assertion:

```java
        assertThat(properties.activationUrl()).isEmpty();
```

- [ ] **Step 3: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HubPropertiesTest`
Expected: 1/1 PASS.

- [ ] **Step 4: Write the exception type**

```java
package com.vanter.ember.hub.provisioning;

/** Anything that stops the Hub from seeding its local Restaurant/admin User the first time. */
public class HubProvisioningException extends RuntimeException {

    public HubProvisioningException(String message) {
        super(message);
    }

    public HubProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 5: Write the failing runner test**

```java
package com.vanter.ember.hub.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HardwareFingerprintService;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

class HubProvisioningRunnerTest {

    @TempDir Path tempDir;

    private HubProperties properties;
    private HubStateStore stateStore;
    private HardwareFingerprintService fingerprintService;
    private RestaurantRepository restaurantRepository;
    private UserRepository userRepository;
    private UUID restaurantId;

    @BeforeEach
    void setUp() throws Exception {
        restaurantId = UUID.randomUUID();
        Path licenseFile = tempDir.resolve("license.key");
        Files.writeString(licenseFile, "irrelevant-for-this-test.signature");

        properties = new HubProperties(
                tempDir, tempDir, licenseFile, tempDir.resolve("pub.der"), tempDir.resolve("state.json"),
                5432, 8080, "http://localhost:9999/hub-activations");
        stateStore = new HubStateStore(properties.stateFile());
        stateStore.save(new HubState("fp-1", restaurantId, Instant.now()));

        fingerprintService = mock(HardwareFingerprintService.class);
        when(fingerprintService.currentFingerprint()).thenReturn("fp-1");
        restaurantRepository = mock(RestaurantRepository.class);
        userRepository = mock(UserRepository.class);
    }

    @Test
    void run_restaurantAlreadyExists_skipsActivationCall() throws Exception {
        when(restaurantRepository.existsById(restaurantId)).thenReturn(true);
        HubProvisioningRunner runner = new HubProvisioningRunner(
                properties, stateStore, fingerprintService, restaurantRepository, userRepository);

        runner.run(new DefaultApplicationArguments());

        verify(restaurantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void run_blankActivationUrl_throwsProvisioningException() {
        HubProperties propertiesWithoutUrl = new HubProperties(
                tempDir, tempDir, properties.licenseFile(), tempDir.resolve("pub.der"), properties.stateFile(),
                5432, 8080, "");
        when(restaurantRepository.existsById(restaurantId)).thenReturn(false);
        HubProvisioningRunner runner = new HubProvisioningRunner(
                propertiesWithoutUrl, stateStore, fingerprintService, restaurantRepository, userRepository);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(HubProvisioningException.class)
                .hasMessageContaining("EMBER_HUB_ACTIVATION_URL");
    }

    @Test
    void run_unreachableActivationUrl_throwsProvisioningException() {
        when(restaurantRepository.existsById(restaurantId)).thenReturn(false);
        HubProvisioningRunner runner = new HubProvisioningRunner(
                properties, stateStore, fingerprintService, restaurantRepository, userRepository);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(HubProvisioningException.class)
                .hasMessageContaining("conexión a internet");
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HubProvisioningRunnerTest`
Expected: FAIL — `HubProvisioningRunner` doesn't exist yet (compile error).

- [ ] **Step 7: Write the implementation**

```java
package com.vanter.ember.hub.provisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HardwareFingerprintService;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs once per boot, after Spring's own context is fully up (but before {@code
 * ApplicationReadyEvent} — see {@code HubDashboard}/{@code HubTrayIcon}, neither shows "ready"
 * until this finishes) — see spec `docs/superpowers/specs/2026-08-25-hub-license-activation-design.md`
 * §5.1. First boot ever: calls {@code POST /hub-activations} once and seeds {@code Restaurant} +
 * the admin {@code User} locally, reusing the SAME id as the license's {@code restaurantId}
 * (Hibernate's {@code GenerationType.UUID} respects a pre-assigned id — confirmed during design).
 * Every later boot: a no-op, zero network calls, since the restaurant already exists locally.
 */
@Component
@Profile("hub")
public class HubProvisioningRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HubProvisioningRunner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HubProperties properties;
    private final HubStateStore stateStore;
    private final HardwareFingerprintService fingerprintService;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    public HubProvisioningRunner(
            HubProperties properties,
            HubStateStore stateStore,
            HardwareFingerprintService fingerprintService,
            RestaurantRepository restaurantRepository,
            UserRepository userRepository) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.fingerprintService = fingerprintService;
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        HubState state = stateStore.load()
                .orElseThrow(() -> new IllegalStateException(
                        "hub-state.json missing after startup license validation"));

        if (restaurantRepository.existsById(state.restaurantId())) {
            log.info("Restaurant {} already provisioned locally, skipping activation call.",
                    state.restaurantId());
            return;
        }
        if (properties.activationUrl().isBlank()) {
            throw new HubProvisioningException(
                    "No se configuró EMBER_HUB_ACTIVATION_URL; no se puede activar por primera vez.");
        }

        ActivationResponseBody body = callActivationEndpoint(state);

        Restaurant restaurant = Restaurant.builder()
                .id(state.restaurantId())
                .name(body.name())
                .slug(body.slug())
                .build();
        restaurantRepository.save(restaurant);

        userRepository.save(User.builder()
                .restaurantId(restaurant)
                .name(body.adminName())
                .email(body.adminEmail())
                .passwordHash(body.adminPasswordHash())
                .role(Role.ADMIN)
                .build());

        log.info("Provisioned restaurant {} ({}) locally.", restaurant.getId(), restaurant.getSlug());
    }

    private ActivationResponseBody callActivationEndpoint(HubState state) throws IOException {
        String licenseKeyContents = Files.readString(properties.licenseFile());
        String fingerprint = fingerprintService.currentFingerprint();

        String requestBody;
        try {
            requestBody = MAPPER.writeValueAsString(
                    new ActivationRequestBody(licenseKeyContents, fingerprint));
        } catch (IOException e) {
            throw new HubProvisioningException("No se pudo preparar la solicitud de activación.", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.activationUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new HubProvisioningException(
                    "No se pudo conectar para activar Ember Hub. Verifica tu conexión a internet "
                            + "e intenta de nuevo.", e);
        }

        if (response.statusCode() != 200) {
            throw new HubProvisioningException(
                    "La activación falló (HTTP " + response.statusCode() + "): " + response.body());
        }

        return MAPPER.readValue(response.body(), ActivationResponseBody.class);
    }

    private record ActivationRequestBody(String licenseKey, String hardwareFingerprint) {}

    private record ActivationResponseBody(
            String name, String slug, String adminName, String adminEmail, String adminPasswordHash) {}
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HubProvisioningRunnerTest`
Expected: 3/3 PASS. (The third test relies on `http://localhost:9999` genuinely refusing the connection — no server runs on that port in the test environment, so `HttpClient.send` throws `ConnectException`, caught and rewrapped as `HubProvisioningException`.)

- [ ] **Step 9: Run the full suite to confirm no regression**

Run: `cd backend && ./mvnw test`
Expected: 830/830 PASS (827 + 3 new `HubProvisioningRunnerTest` methods; the `HubPropertiesTest` change is one new assertion inside an existing test method, and `HubProvisioningException` has no test file of its own, so neither adds to the count).

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java \
        backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java \
        backend/src/main/java/com/vanter/ember/hub/provisioning/HubProvisioningException.java \
        backend/src/main/java/com/vanter/ember/hub/provisioning/HubProvisioningRunner.java \
        backend/src/test/java/com/vanter/ember/hub/provisioning/HubProvisioningRunnerTest.java
git commit -m "feat(hub): add HubProvisioningRunner, seeds Restaurant+admin on first boot"
```

---

### Task 7: `HubDashboard` — broaden error handling to cover provisioning failures

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java`

**Interfaces:**
- Consumes: nothing new — `SpringApplication.run()` (already called) now also runs Task 6's `HubProvisioningRunner`, whose `HubProvisioningException` needs to reach the same dialog the existing catch already shows.

No automated test for this one file (matches this class's existing precedent from report 237 — Swing/`SpringApplication.run()` failure paths need a real desktop + a real failing boot to exercise, verified manually in Task 8 below, same deferral `HubTrayIcon`/`PortableDatabaseBootstrap`'s real-binary paths already use).

- [ ] **Step 1: Widen the catch clause**

In `backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java`, change:

```java
        } catch (InvalidLicenseException | PortableDatabaseException e) {
```

to:

```java
        } catch (Exception e) {
```

This one clause already covered `InvalidLicenseException`/`PortableDatabaseException` (both thrown pre-Spring, by `bootstrapRunner.startServices()`); widening it to `Exception` additionally covers whatever `SpringApplication.run()` propagates when `HubProvisioningRunner` (an `ApplicationRunner` bean) throws — Spring Boot re-throws an `ApplicationRunner` failure from `run()`, so this single broadened catch is enough; no second try/catch needed.

- [ ] **Step 2: Remove the now-unused imports**

`InvalidLicenseException` and `PortableDatabaseException` are no longer referenced by name in this file — remove these two import lines:

```java
import com.vanter.ember.hub.bootstrap.PortableDatabaseException;
import com.vanter.ember.hub.license.InvalidLicenseException;
```

- [ ] **Step 3: Run the backend build to confirm it compiles**

Run: `cd backend && ./mvnw test -Dtest=HubDashboard` — this will report "no tests found," which is expected (there is no test class for this file); use it only to confirm the module compiles. Then run the full suite:

Run: `cd backend && ./mvnw test`
Expected: 830/830 PASS, unchanged from Task 6 (no new tests here).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java
git commit -m "fix(hub): show provisioning failures in the dashboard's retry dialog"
```

---

### Task 8: Frontend — "Emitir licencia Hub" button in `/console`

**Files:**
- Modify: `frontend/src/lib/platformApi.ts`
- Modify: `frontend/src/pages/console/ConsoleRestaurantDetail.tsx`

**Interfaces:**
- Consumes: `POST /platform/restaurants/{id}/hub-license` (Task 3), returns `text/plain`.
- Produces: `platformRestaurantService.issueHubLicense(id: string): Promise<string>` — used only by this task's button.

- [ ] **Step 1: Add the API method**

In `frontend/src/lib/platformApi.ts`, add this method to the `platformRestaurantService` object (after `updateStatus`):

```ts
  issueHubLicense: async (id: string): Promise<string> => {
    const { data } = await platformApi.post<string>(`/platform/restaurants/${id}/hub-license`)
    return data
  },
```

- [ ] **Step 2: Add the button + download handler**

In `frontend/src/pages/console/ConsoleRestaurantDetail.tsx`, add a mutation next to `toggleStatus` (after it, before the `if (isLoading)` guard):

```tsx
  const issueHubLicense = useMutation({
    mutationFn: () => platformRestaurantService.issueHubLicense(id!),
    onSuccess: (licenseKeyContents) => {
      const blob = new Blob([licenseKeyContents], { type: 'text/plain' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = 'license.key'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
    },
  })
```

Then, in the header `<div className="flex items-center justify-between">` block, add the new button next to the existing Suspender/Reactivar one:

```tsx
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={issueHubLicense.isPending}
            onClick={() => issueHubLicense.mutate()}
          >
            {issueHubLicense.isPending ? 'Emitiendo...' : 'Emitir licencia Hub'}
          </Button>
          <Button
            type="button"
            disabled={toggleStatus.isPending}
            onClick={() => toggleStatus.mutate(nextStatus(restaurant.status))}
          >
            {restaurant.status === 'SUSPENDED' ? 'Reactivar' : 'Suspender'}
          </Button>
        </div>
```

(This replaces the single `<Button>` that used to sit directly inside that header `<div>` — the two buttons are now wrapped together in a new inner `flex items-center gap-2` div, sitting where the old lone `<Button>` was.)

- [ ] **Step 3: Verify the frontend build**

Run: `cd frontend && pnpm run build`
Expected: PASS, no TypeScript errors.

- [ ] **Step 4: Manual check**

Run: `cd frontend && pnpm run dev`, log into `/console` as a platform operator, open any restaurant's detail page, click "Emitir licencia Hub", confirm a `license.key` file downloads. (This will 500 until `HUB_LICENSE_PRIVATE_KEY` is set in the backend's real `.env` — that's expected and covered in Task 10's end-to-end verification, not this task.)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/platformApi.ts frontend/src/pages/console/ConsoleRestaurantDetail.tsx
git commit -m "feat(console): add 'Emitir licencia Hub' button"
```

---

### Task 9: Frontend — hide "Registrarse" on the Hub build

**Files:**
- Modify: `frontend/src/pages/auth/Login.tsx`

**Interfaces:**
- Consumes: `import.meta.env.BASE_URL` (Vite built-in, already used the same way in `App.tsx`'s `routerBasename`, report 238) — no new signal introduced.

- [ ] **Step 1: Hide the link**

In `frontend/src/pages/auth/Login.tsx`, add this constant near the top of the file (after the imports, before `createLoginSchema`):

```tsx
// Same signal App.tsx already uses for the router basename: only the Hub build (vite build
// --base=/app/, see ember-hub/build-frontend.ps1) has a non-"/" BASE_URL. The Hub's admin is
// always pre-provisioned (HubProvisioningRunner) — self-registration is a customer-only flow
// there (join-table/collaborative cart), never the entry point for the restaurant's own admin.
const isHubBuild = import.meta.env.BASE_URL !== '/'
```

Then change:

```tsx
              <Button asChild variant="outline" className="w-full text-center mb-3">
                <Link to="/register">{tAuth('registerLink')}</Link>
              </Button>
```

to:

```tsx
              {!isHubBuild && (
                <Button asChild variant="outline" className="w-full text-center mb-3">
                  <Link to="/register">{tAuth('registerLink')}</Link>
                </Button>
              )}
```

- [ ] **Step 2: Verify both builds**

Run: `cd frontend && pnpm run build` — expected PASS, "Registrarse" still present (default `BASE_URL` is `/`, so `isHubBuild` is `false`).
Run: `cd frontend && pnpm run build:hub` — expected PASS. Manually confirm by grepping the output: `grep -o 'Registrarse\|registerLink' backend/src/main/resources/static/assets/*.js` should print nothing for this build (the string only exists in source, and dead-code the conditional strips depends on the bundler — if the grep still finds it, that's fine too, since the check is correct either way: what matters is runtime behavior, not whether the string survives minification. Confirm the real behavior instead by running the Hub build through the dashboard and looking at `/app/login` in a browser, same as Task 10.)

- [ ] **Step 3: Run the frontend test suite**

Run: `cd frontend && pnpm run test:run`
Expected: 36/36 PASS (no existing test covers `Login.tsx`'s register link, so none needed updating).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/auth/Login.tsx
git commit -m "feat(hub): hide customer self-register link on the Hub build's Login page"
```

---

### Task 10: Manual end-to-end verification

**Files:** none — verification only, same shape as HUB-01-11 (report 236).

- [ ] **Step 1: Generate a real key pair and configure the cloud backend**

```powershell
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -outform DER -out hub-private-key.der
openssl pkey -in hub-private-key.der -pubout -outform DER -out hub-public-key.der
```

(If `openssl` isn't available, as during HUB-01-11 — use a throwaway JUnit test calling `KeyPairGenerator`, write both `.der` files, delete the test after use, exactly like report 236's `ScratchLicenseGenTest` did.)

Set `HUB_LICENSE_PRIVATE_KEY` in the real `.env` to the base64 of `hub-private-key.der`'s bytes. Restart the backend (`cd backend && ./mvnw spring-boot:run`, no `hub` profile — this is the cloud/dev backend).

- [ ] **Step 2: Create a restaurant via `/console`**

Log into `/console` as a platform operator (or create one first if none exists locally — see `PlatformOperatorRepository`/existing console docs). Create a new restaurant with a real admin email/password.

- [ ] **Step 3: Issue its Hub license**

Open the restaurant's detail page, click "Emitir licencia Hub" (Task 8), confirm `license.key` downloads successfully now that the real private key is configured.

- [ ] **Step 4: Boot a test Hub with the real license**

Copy `license.key` + `hub-public-key.der` (from Step 1) into a Hub test directory (reuse the `C:\ember-hub-test\` layout from HUB-01-11 if convenient). Set `EMBER_HUB_ACTIVATION_URL` to the cloud backend's real URL (e.g. `http://localhost:8080/v1/hub-activations` — remember the cloud backend still has `context-path: /v1/`, only the `hub` profile overrides it to `/`). Boot via the dashboard (`SPRING_PROFILES_ACTIVE=hub`, same env vars as HUB-01-11, `EMBER_HUB_POSTGRES_PORT=5433` if port 5432 is still taken locally).

- [ ] **Step 5: Confirm provisioning and login**

Click Iniciar. Confirm (via the backend log or a debugger, matching HUB-01-11's verification style) that `HubProvisioningRunner` ran, and check the Hub's local Postgres directly (`psql` against the portable instance) for the new `restaurants`/`users` rows. Click "Abrir en navegador", log in with the SAME admin email/password used in Step 2 — confirm it succeeds (not a 401).

- [ ] **Step 6: Confirm idempotency**

Click Detener, then Iniciar again. Confirm (via log or a quick DB check) that `HubProvisioningRunner` made zero network calls the second time — the restaurant already exists locally.

- [ ] **Step 7: Record the results**

Write a report (`reports/XXX-task-10-hub-license-activation-verification.md`, matching this project's numbering) documenting what was verified, any bugs found+fixed along the way (same pattern as reports 233–236), and update `PROGRESS.md`.

- [ ] **Step 8: Commit**

```bash
git add reports/XXX-task-10-hub-license-activation-verification.md PROGRESS.md
git commit -m "docs(hub): record license activation end-to-end verification"
```
