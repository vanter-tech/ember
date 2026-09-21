# Restaurant Deployment Mode (Web vs Hub) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every restaurant is either Web (`CLOUD`) or Hub (`HUB`); the cloud refuses everything tenant-facing for a Hub restaurant, licenses/activation/heartbeat require `HUB`, the Console can create and switch modes, and a Hub whose restaurant moved to Web becomes read-only after a 48 h courtesy.

**Architecture:** A `deployment_mode` column on `restaurants` (default `CLOUD`, backfilled `HUB` for restaurants with a Hub activation or an issued license). A pure helper `DeploymentMode.isClosedToWeb(restaurant, enforced)` is used at the existing tenant gates (login, JWT filter, websocket CONNECT, QR join, public slug); the flag `ember.deployment-mode.enforced` is `false` inside the Hub. The already-signed Hub heartbeat gains a status `MIGRATED`; the Hub records `migratedSince` in its HMAC-protected state and, once the courtesy has elapsed, a new interceptor blocks every write.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Data JPA/Hibernate, Flyway, JUnit 5 + Mockito + AssertJ, React 19 + TypeScript + Vitest + Testing Library, pnpm.

**Spec:** `docs/superpowers/specs/2026-09-20-restaurant-deployment-mode-design.md`

## Global Constraints

- Enum `DeploymentMode { CLOUD, HUB }` in `com.vanter.ember.restaurant.model`; column `restaurants.deployment_mode varchar(10) NOT NULL DEFAULT 'CLOUD'` with a CHECK.
- Flag `ember.deployment-mode.enforced`: default `true`; `false` in `backend/src/main/resources/application-hub.yml`. Gates are read from a field `@Value("${ember.deployment-mode.enforced:true}") private boolean deploymentModeEnforced = true;` (same pattern as `PlanGateService.enforced`) — do NOT add a new bean dependency to `SecurityConfig` (it would force a `@MockBean` into dozens of `@WebMvcTest` classes).
- Cloud login answer for a Hub restaurant is byte-identical to a wrong password: `BadCredentialsException("Invalid credentials")`.
- Heartbeat status precedence: `MIGRATED` (mode is `CLOUD`) → `SUSPENDED` (restaurant not `ACTIVE`) → `OK`. The response stays RSA-signed; no protocol change.
- Hub courtesy before read-only reuses `ember.hub.suspended-grace-hours` (48 h) through `LicenseService.suspendedGrace`.
- The HMAC canonical string of `hub-state.json` must NOT change for states without `migratedSince` (append the field only when non-null) so files already written by installed Hubs keep verifying.
- Read-only Hub: POST/PUT/PATCH/DELETE answer 403 `{"error":"license_migrated","message":"Tu restaurante ahora usa Ember Web. Este Hub está en modo consulta."}`; GET/HEAD/OPTIONS, `/auth/**`, `/app/**`, `/actuator/**`, `/error`, `/ws/**` are never blocked.
- Migration `V15__restaurant_deployment_mode.sql` must be idempotent (production Flyway is not baselined). The local dev database is baselined past `V15`: run `ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS deployment_mode varchar(10) NOT NULL DEFAULT 'CLOUD';` by hand there.
- Console UI copy is Spanish and hard-coded (no i18n keys there). Frontend package manager is `pnpm` only.
- TDD: write the failing test, watch it fail, implement, watch it pass. Commits: conventional, lowercase, NO `Co-Authored-By`/signature lines.
- Backend commands run from `backend/` (`./mvnw test -Dtest=...`); frontend from `frontend/` (`pnpm exec vitest run <file>`); Hub UI from `ember-hub/ui/` (`pnpm run test`).

## File Structure

| File | Responsibility |
|---|---|
| `restaurant/model/DeploymentMode.java` (new) | enum + `isClosedToWeb` rule |
| `restaurant/model/Restaurant.java` | `deploymentMode` field |
| `db/migration/V15__restaurant_deployment_mode.sql` (new) | column, CHECK, backfill |
| `identity/service/AuthService.java` | login/PIN refuse Hub staff |
| `config/SecurityConfig.java` | JWT tenant gate also refuses Hub restaurants |
| `config/JwtChannelInterceptor.java` | websocket CONNECT refuses Hub restaurants |
| `session/service/SessionService.java` | QR/guest join refuses Hub restaurants |
| `restaurant/controller/PublicRestaurantController.java` | slug branding 404 for Hub |
| `platform/service/PlatformRestaurantService.java` (+ controller, DTOs, `RestaurantService`, `RestaurantRepository`) | create with mode, change mode, list filter, license only for Hub |
| `licensing/service/HubActivationService.java`, `HubHeartbeatService.java` | require `HUB`; heartbeat `MIGRATED` |
| `hub/license/HubState.java`, `HubStateStore.java`, `LicenseService.java` | `migratedSince` + courtesy |
| `hub/sync/HeartbeatScheduler.java` | handle `MIGRATED` |
| `hub/license/ReadOnlyModeInterceptor.java` (new), `hub/config/HubWebConfig.java` | read-only enforcement |
| `hub/control/HubOrchestrator.java`, `DefaultHubOrchestrator.java` | report `MIGRATED` |
| `frontend/src/lib/platformApi.ts`, `pages/console/*` | Console UI |
| `ember-hub/ui/src/lib/types.ts`, `components/LicenseCard.tsx` | Hub UI state |

---

### Task 1: Data model, migration and the Hub-side flag

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/restaurant/model/DeploymentMode.java`
- Create: `backend/src/main/resources/db/migration/V15__restaurant_deployment_mode.sql`
- Modify: `backend/src/main/java/com/vanter/ember/restaurant/model/Restaurant.java`
- Modify: `backend/src/main/java/com/vanter/ember/restaurant/repository/RestaurantRepository.java` (native `insertWithId`)
- Modify: `backend/src/main/resources/application-hub.yml`
- Test: `backend/src/test/java/com/vanter/ember/restaurant/model/DeploymentModeTest.java` (new)
- Test: `backend/src/test/java/com/vanter/ember/restaurant/repository/RestaurantDeploymentModeRepositoryTest.java` (new)

**Interfaces:**
- Produces: `DeploymentMode.CLOUD|HUB`; `static boolean DeploymentMode.isClosedToWeb(Restaurant restaurant, boolean enforced)`; `Restaurant.getDeploymentMode()/setDeploymentMode(DeploymentMode)` and `Restaurant.builder().deploymentMode(...)` (default `CLOUD`).

- [ ] **Step 0: Create the working branch**

```bash
git checkout spec/restaurant-deployment-mode
git checkout -b feat/restaurant-deployment-mode
```

- [ ] **Step 1: Write the failing tests**

`DeploymentModeTest.java`:

```java
package com.vanter.ember.restaurant.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeploymentModeTest {

    private static Restaurant restaurant(DeploymentMode mode) {
        return Restaurant.builder().id(UUID.randomUUID()).name("R").slug("r").deploymentMode(mode).build();
    }

    @Test
    void newRestaurantsDefaultToCloud() {
        assertThat(Restaurant.builder().build().getDeploymentMode()).isEqualTo(DeploymentMode.CLOUD);
    }

    @Test
    void aHubRestaurantIsClosedToWeb_whenEnforced() {
        assertThat(DeploymentMode.isClosedToWeb(restaurant(DeploymentMode.HUB), true)).isTrue();
    }

    @Test
    void aCloudRestaurantIsNeverClosed() {
        assertThat(DeploymentMode.isClosedToWeb(restaurant(DeploymentMode.CLOUD), true)).isFalse();
    }

    @Test
    void insideTheHubItself_nothingIsClosed() {
        assertThat(DeploymentMode.isClosedToWeb(restaurant(DeploymentMode.HUB), false)).isFalse();
    }

    @Test
    void aMissingRestaurantIsNotClosedByThisRule() {
        assertThat(DeploymentMode.isClosedToWeb(null, true)).isFalse();
    }
}
```

`RestaurantDeploymentModeRepositoryTest.java`:

```java
package com.vanter.ember.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.restaurant.model.DeploymentMode;
import com.vanter.ember.restaurant.model.Restaurant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TenantIdentifierResolver.class)
class RestaurantDeploymentModeRepositoryTest {

    @Autowired RestaurantRepository restaurantRepository;

    @Test
    void aNewRestaurantIsStoredAsCloudByDefault() {
        Restaurant saved = restaurantRepository.saveAndFlush(
                Restaurant.builder().name("Web Grill").slug("web-grill").build());

        assertThat(restaurantRepository.findById(saved.getId()).orElseThrow().getDeploymentMode())
                .isEqualTo(DeploymentMode.CLOUD);
    }

    @Test
    void hubModeRoundTrips() {
        Restaurant saved = restaurantRepository.saveAndFlush(Restaurant.builder()
                .name("Hub Grill").slug("hub-grill").deploymentMode(DeploymentMode.HUB).build());

        assertThat(restaurantRepository.findById(saved.getId()).orElseThrow().getDeploymentMode())
                .isEqualTo(DeploymentMode.HUB);
    }

    @Test
    void insertWithId_seedsTheHubsOwnRestaurantAsHub() {
        UUID id = UUID.randomUUID();

        restaurantRepository.insertWithId(id, "Local Hub", "local-hub");

        assertThat(restaurantRepository.findById(id).orElseThrow().getDeploymentMode())
                .isEqualTo(DeploymentMode.HUB);
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=DeploymentModeTest,RestaurantDeploymentModeRepositoryTest`
Expected: compilation FAIL — `DeploymentMode` does not exist.

- [ ] **Step 3: Implement**

`DeploymentMode.java`:

```java
package com.vanter.ember.restaurant.model;

/** Where a restaurant runs: on Ember Web (cloud) or on an on-premise Ember Hub. */
public enum DeploymentMode {
    CLOUD, HUB;

    /**
     * True when the cloud must refuse this restaurant: it runs on a Hub, so nobody may use Ember
     * Web for it. {@code enforced} is false inside the Hub itself ({@code
     * ember.deployment-mode.enforced}), where its own restaurant is HUB and must keep working.
     */
    public static boolean isClosedToWeb(Restaurant restaurant, boolean enforced) {
        return enforced && restaurant != null && restaurant.getDeploymentMode() == HUB;
    }
}
```

`Restaurant.java` — after the `status` field add:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_mode", nullable = false)
    @Builder.Default
    private DeploymentMode deploymentMode = DeploymentMode.CLOUD;
```

`RestaurantRepository.insertWithId` — replace the query with:

```java
    @Query(value = "insert into restaurants (id, name, slug, plan, status, timezone, currency, deployment_mode, created_at) "
            + "values (:id, :name, :slug, 'FREE', 'ACTIVE', 'UTC', 'USD', 'HUB', now())", nativeQuery = true)
```

`V15__restaurant_deployment_mode.sql`:

```sql
-- Restaurant deployment mode (design: docs/superpowers/specs/2026-09-20-restaurant-deployment-mode-design.md).
-- Idempotent: production Flyway is not baselined; the local dev DB is baselined past this version
-- and needs the column added by hand. The backfill below only runs when Flyway applies this
-- migration for the first time; never re-run it by hand on a live database (it would undo an
-- operator's mode change).
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS deployment_mode varchar(10) NOT NULL DEFAULT 'CLOUD';

ALTER TABLE restaurants DROP CONSTRAINT IF EXISTS restaurants_deployment_mode_check;
ALTER TABLE restaurants ADD CONSTRAINT restaurants_deployment_mode_check
    CHECK (deployment_mode IN ('CLOUD', 'HUB'));

-- A restaurant that already has a Hub activation, or for which a Hub license was issued (even if
-- not activated yet), is a Hub restaurant; everything else stays CLOUD.
UPDATE restaurants SET deployment_mode = 'HUB'
 WHERE id IN (SELECT restaurant_id FROM hub_activations)
    OR id IN (SELECT restaurant_id FROM platform_audit_log
               WHERE action = 'HUB_LICENSE_ISSUED' AND restaurant_id IS NOT NULL);
```

`application-hub.yml` — under `ember:` (next to `plans:` / `hub:` / `printing:`) add:

```yaml
  deployment-mode:
    # Inside the Hub its own restaurant is HUB and must keep working; the "closed to Web" gates
    # only make sense in the cloud. See DeploymentMode.isClosedToWeb.
    enforced: false
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=DeploymentModeTest,RestaurantDeploymentModeRepositoryTest`
Expected: PASS (8 tests).

- [ ] **Step 5: Run the whole restaurant/hub-provisioning slice**

Run: `cd backend && ./mvnw test -Dtest='Restaurant*Test,HubProvisioningRunnerTest'`
Expected: PASS (nothing else depended on the old insert).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/restaurant backend/src/main/resources/db/migration/V15__restaurant_deployment_mode.sql backend/src/main/resources/application-hub.yml backend/src/test/java/com/vanter/ember/restaurant
git commit -m "feat(restaurant): deployment mode column (cloud/hub) with idempotent migration and hub flag"
```

---

### Task 2: Cloud login refuses a Hub restaurant's staff

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/identity/service/AuthService.java`
- Test: `backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: `DeploymentMode.isClosedToWeb(Restaurant, boolean)`, `User.getRestaurantId()` (a `Restaurant`).
- Produces: `login`/`loginWithPin` throw `BadCredentialsException("Invalid credentials")` for a non-customer whose restaurant is `HUB` (unless `deploymentModeEnforced` is false).

- [ ] **Step 1: Write the failing tests** — append to `AuthServiceTest` (add imports for `DeploymentMode`, `PinLoginRequest`, `BadCredentialsException`, `org.springframework.test.util.ReflectionTestUtils` and static `never` if missing):

```java
    private static Restaurant restaurantIn(DeploymentMode mode) {
        return Restaurant.builder().id(UUID.randomUUID()).name("R").slug("r").deploymentMode(mode).build();
    }

    private static User staffOf(Restaurant restaurant) {
        return User.builder()
                .id("user-1").name("Admin").email("admin@test.com")
                .passwordHash("hashed").pinHash("pin-hashed").role(Role.ADMIN).restaurantId(restaurant).build();
    }

    @Test
    void login_refusesAHubRestaurantsStaff_withTheSameErrorAsABadPassword() {
        User user = staffOf(restaurantIn(DeploymentMode.HUB));
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@test.com");
        req.setPassword("secret");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
        verify(jwtService, never()).generateToken(any(), anyMap());
    }

    @Test
    void login_stillWorksForACloudRestaurantsStaff() {
        User user = staffOf(restaurantIn(DeploymentMode.CLOUD));
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@test.com");
        req.setPassword("secret");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.generateToken(eq("admin@test.com"), anyMap())).thenReturn("jwt-token");

        assertThat(authService.login(req).getToken()).isEqualTo("jwt-token");
    }

    @Test
    void login_isNotBlockedInsideTheHubItself() {
        ReflectionTestUtils.setField(authService, "deploymentModeEnforced", false);
        User user = staffOf(restaurantIn(DeploymentMode.HUB));
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@test.com");
        req.setPassword("secret");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.generateToken(eq("admin@test.com"), anyMap())).thenReturn("jwt-token");

        assertThat(authService.login(req).getToken()).isEqualTo("jwt-token");
    }

    @Test
    void loginWithPin_refusesAHubRestaurantsStaff_andCountsItAsAFailedAttempt() {
        User user = staffOf(restaurantIn(DeploymentMode.HUB));
        PinLoginRequest req = new PinLoginRequest();
        req.setEmail("admin@test.com");
        req.setPin("1234");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("1234", "pin-hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.loginWithPin(req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
        verify(pinAttemptGuard).recordFailure("admin@test.com");
        verify(pinAttemptGuard, never()).recordSuccess(any());
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=AuthServiceTest`
Expected: FAIL — `login_refusesAHubRestaurantsStaff...` and `loginWithPin_refuses...` (no exception thrown / token issued); the `ReflectionTestUtils` test errors because the field does not exist.

- [ ] **Step 3: Implement** — in `AuthService.java` add imports `com.vanter.ember.restaurant.model.DeploymentMode` and `org.springframework.beans.factory.annotation.Value`; add the field and helper after `pinAttemptGuard`:

```java
    /** False inside the Hub itself; see {@link DeploymentMode#isClosedToWeb}. */
    @Value("${ember.deployment-mode.enforced:true}")
    private boolean deploymentModeEnforced = true;

    /**
     * A Hub restaurant's staff have no business on Ember Web. Staff only: CUSTOMER accounts float
     * between restaurants and are gated when they join a table.
     */
    private boolean closedToWeb(User user) {
        return user.getRole() != Role.CUSTOMER
                && DeploymentMode.isClosedToWeb(user.getRestaurantId(), deploymentModeEnforced);
    }
```

In `login`, after the `getActive()` check add:

```java
        if (closedToWeb(user)) {
            throw new BadCredentialsException("Invalid credentials");
        }
```

In `loginWithPin`, extend the failure condition:

```java
        if (!passwordEncoder.matches(request.getPin(), user.getPinHash())
                || !Boolean.TRUE.equals(user.getActive())
                || closedToWeb(user)) {
            pinAttemptGuard.recordFailure(request.getEmail());
            throw new BadCredentialsException("Invalid credentials");
        }
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=AuthServiceTest,AuthControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/identity/service/AuthService.java backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java
git commit -m "feat(identity): refuse cloud login for staff of a hub restaurant"
```

---

### Task 3: Cloud tenant gates (JWT filter, websocket, QR join, public slug)

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/JwtChannelInterceptor.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/service/SessionService.java`
- Modify: `backend/src/main/java/com/vanter/ember/restaurant/controller/PublicRestaurantController.java`
- Test: `backend/src/test/java/com/vanter/ember/restaurant/DeploymentModeEnforcementTest.java` (new)
- Test: `backend/src/test/java/com/vanter/ember/config/JwtChannelInterceptorTest.java`
- Test: `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java`
- Test: `backend/src/test/java/com/vanter/ember/restaurant/controller/PublicRestaurantControllerTest.java`

**Interfaces:**
- Consumes: `DeploymentMode.isClosedToWeb(Restaurant, boolean)`.
- Produces: every tenant-facing cloud path refuses a `HUB` restaurant while `ember.deployment-mode.enforced` is true.

- [ ] **Step 1: Write the failing tests**

`DeploymentModeEnforcementTest.java` (same shape as `RestaurantStatusEnforcementTest`):

```java
package com.vanter.ember.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.DeploymentMode;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DeploymentModeEnforcementTest {

    private static final String PASSWORD = "password123";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Restaurant restaurant;
    private String waiterToken;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
        restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Mode Test Restaurant").slug("mode-test-" + UUID.randomUUID()).build());
        TenantContextHolder.setTenantId(restaurant.getId());
        userRepository.save(User.builder()
                .name("Waiter").email("waiter@mode-test.com").restaurantId(restaurant)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.WAITER).build());
        waiterToken = objectMapper.readTree(loginResult("waiter@mode-test.com", PASSWORD)).get("token").asText();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private String loginResult(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
    }

    private int loginStatus(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getStatus();
    }

    private void switchTo(DeploymentMode mode) {
        restaurant.setDeploymentMode(mode);
        restaurantRepository.save(restaurant);
    }

    @Test
    void cloudTenant_requestSucceeds() throws Exception {
        mockMvc.perform(get("/dashboard/status").header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk());
    }

    @Test
    void hubTenant_anExistingTokenStopsWorkingTheMomentTheModeChanges() throws Exception {
        switchTo(DeploymentMode.HUB);

        mockMvc.perform(get("/dashboard/status").header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void hubTenant_cannotLogIn_andTheAnswerIsTheSameAsForAWrongPassword() throws Exception {
        switchTo(DeploymentMode.HUB);

        int hubStatus = loginStatus("waiter@mode-test.com", PASSWORD);
        int wrongPasswordStatus = loginStatus("waiter@mode-test.com", "not-the-password");

        assertThat(hubStatus).isEqualTo(wrongPasswordStatus).isNotEqualTo(200);
    }

    @Test
    void hubTenant_publicBrandingIsNotFound() throws Exception {
        switchTo(DeploymentMode.HUB);

        mockMvc.perform(get("/public/restaurants/" + restaurant.getSlug() + "/branding"))
                .andExpect(status().isNotFound());
    }
}
```

Append to `JwtChannelInterceptorTest` (add `@Mock RestaurantRepository restaurantRepository;` next to the other mocks, and imports for `Restaurant`, `DeploymentMode`, `RestaurantRepository`, `Optional`, `MessageDeliveryException` if missing). Existing tests keep passing: an unstubbed `findById` returns an empty `Optional`.

```java
    @Test
    void connect_toARestaurantThatRunsOnAHub_isRejectedBeforeAnyUserIsLoaded() {
        UUID tenantId = UUID.randomUUID();
        when(jwtService.isTokenValid("hub.jwt")).thenReturn(true);
        when(jwtService.extractTenantId("hub.jwt")).thenReturn(tenantId);
        when(restaurantRepository.findById(tenantId)).thenReturn(Optional.of(Restaurant.builder()
                .id(tenantId).name("Hub").slug("hub").deploymentMode(DeploymentMode.HUB).build()));

        assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer hub.jwt"), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verify(userDetailsService, never()).loadUserByUsername(any());
    }
```

Append to `SessionServiceTest`, right after `joinCode_refusesWhenTheResolvedRestaurantIsSuspended`:

```java
    @Test
    void joinCode_refusesWhenTheResolvedRestaurantRunsOnAHub() {
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(sessionRepository.findByJoinCodeAndStatus("AB3CD", SessionStatus.OPEN))
                .thenReturn(List.of(openSessionWithCapacity(4, List.of())));
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(
                Restaurant.builder().id(RESTAURANT_ID).status(RestaurantStatus.ACTIVE)
                        .deploymentMode(DeploymentMode.HUB).build()));

        assertThatThrownBy(() -> sessionService.joinSessionCode("AB3CD", "user-1"))
                .isInstanceOf(AccessDeniedException.class);
        verify(sessionRepository, never()).save(any());
    }
```
(add `import com.vanter.ember.restaurant.model.DeploymentMode;`)

Append to `PublicRestaurantControllerTest` (mirror an existing "not found" test's `mockMvc.perform(get(...))` style; the file already builds restaurants with a `restaurant(id, slug)` helper at ~line 48 — build the Hub one inline):

```java
    @Test
    void branding_isNotFoundForARestaurantThatRunsOnAHub() throws Exception {
        Restaurant hub = Restaurant.builder().id(UUID.randomUUID()).name("Hub Grill").slug("hub-grill")
                .deploymentMode(DeploymentMode.HUB).build();
        when(restaurantRepository.findBySlug("hub-grill")).thenReturn(Optional.of(hub));

        mockMvc.perform(get("/public/restaurants/hub-grill/branding"))
                .andExpect(status().isNotFound());
    }
```
(add `import com.vanter.ember.restaurant.model.DeploymentMode;` and the static `when`/`get`/`status` imports if missing)

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./mvnw test -Dtest='DeploymentModeEnforcementTest,JwtChannelInterceptorTest,SessionServiceTest,PublicRestaurantControllerTest'`
Expected: FAIL — `hubTenant_anExistingToken...` gets 200, `connect_toARestaurantThatRunsOnAHub` does not throw, the QR join does not throw, the two branding tests get 200/500 instead of 404.

- [ ] **Step 3: Implement**

`SecurityConfig.java` — imports `com.vanter.ember.restaurant.model.DeploymentMode` and `org.springframework.beans.factory.annotation.Value`; field after `objectMapper`:

```java
    /** False inside the Hub itself; see {@link DeploymentMode#isClosedToWeb}. */
    @Value("${ember.deployment-mode.enforced:true}")
    private boolean deploymentModeEnforced = true;
```

Replace the status check inside `jwtAuthFilter()`:

```java
                    boolean closedToWeb = DeploymentMode.isClosedToWeb(restaurant, deploymentModeEnforced);
                    if (restaurant == null || restaurant.getStatus() != RestaurantStatus.ACTIVE || closedToWeb) {
                        writeSuspendedTenantResponse(request, response, restaurant, closedToWeb);
                        return;
                    }
```

and the response writer:

```java
            private void writeSuspendedTenantResponse(HttpServletRequest request,
                                                       HttpServletResponse response,
                                                       Restaurant restaurant,
                                                       boolean closedToWeb) throws IOException {
                String detail = restaurant == null
                        ? "Tenant account not found."
                        : closedToWeb
                                ? "This restaurant is not available on Ember Web."
                                : "This tenant account is " + restaurant.getStatus().name().toLowerCase()
                                        + "; access is blocked pending resolution.";
```
(keep the rest of the method unchanged).

`JwtChannelInterceptor.java` — add fields, imports `com.vanter.ember.restaurant.model.DeploymentMode`, `com.vanter.ember.restaurant.repository.RestaurantRepository`, `org.springframework.beans.factory.annotation.Value`:

```java
    private final RestaurantRepository restaurantRepository;

    /** False inside the Hub itself; see {@link DeploymentMode#isClosedToWeb}. */
    @Value("${ember.deployment-mode.enforced:true}")
    private boolean deploymentModeEnforced = true;
```

In the CONNECT branch replace

```java
        String email = jwtService.extractSubject(token);
```
with
```java
        UUID tenantId = jwtService.extractTenantId(token);
        if (tenantId != null && DeploymentMode.isClosedToWeb(
                restaurantRepository.findById(tenantId).orElse(null), deploymentModeEnforced)) {
            throw new MessageDeliveryException(message, "This restaurant is not available on Ember Web");
        }

        String email = jwtService.extractSubject(token);
```
and delete the later duplicate declaration `UUID tenantId = jwtService.extractTenantId(token);` (keep the `Map<String, Object> sessionAttributes = ...` line that follows it).

`SessionService.java` — import `com.vanter.ember.restaurant.model.DeploymentMode` and `org.springframework.beans.factory.annotation.Value`; add next to the other fields:

```java
    /** False inside the Hub itself; see {@link DeploymentMode#isClosedToWeb}. */
    @Value("${ember.deployment-mode.enforced:true}")
    private boolean deploymentModeEnforced = true;
```
and in `bindResolvedTenant`:

```java
        if (restaurant.getStatus() != RestaurantStatus.ACTIVE
                || DeploymentMode.isClosedToWeb(restaurant, deploymentModeEnforced)) {
            throw new AccessDeniedException("This restaurant is not accepting orders right now");
        }
```

`PublicRestaurantController.java` — imports as above; field:

```java
    /** False inside the Hub itself; see {@link DeploymentMode#isClosedToWeb}. */
    @Value("${ember.deployment-mode.enforced:true}")
    private boolean deploymentModeEnforced = true;
```
and the lookup:

```java
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .filter(r -> !DeploymentMode.isClosedToWeb(r, deploymentModeEnforced))
                .orElseThrow(() -> new ResourceNotFoundException("No restaurant found for slug: " + slug));
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && ./mvnw test -Dtest='DeploymentModeEnforcementTest,RestaurantStatusEnforcementTest,JwtChannelInterceptorTest,SessionServiceTest,PublicRestaurantControllerTest'`
Expected: PASS.

- [ ] **Step 5: Run the whole backend suite (the JWT filter and the interceptor are shared by everything)**

Run: `cd backend && ./mvnw test`
Expected: PASS, no `@WebMvcTest` class needs a new bean.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat(security): close every tenant-facing cloud path to hub restaurants"
```

---

### Task 4: License issuance, Hub activation and heartbeat follow the mode

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java` (`issueHubLicense`)
- Modify: `backend/src/main/java/com/vanter/ember/licensing/service/HubActivationService.java`
- Modify: `backend/src/main/java/com/vanter/ember/licensing/service/HubHeartbeatService.java`
- Test: `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`
- Test: `backend/src/test/java/com/vanter/ember/licensing/service/HubActivationServiceTest.java`
- Test: `backend/src/test/java/com/vanter/ember/licensing/service/HubHeartbeatServiceTest.java`

**Interfaces:**
- Produces: `issueHubLicense` and `activate` throw `IllegalStateException` (mapped to 409) unless the restaurant is `HUB`; the heartbeat `status` is `MIGRATED` for a `CLOUD` restaurant.

- [ ] **Step 1: Update the existing tests that assume the old rules, then add the new ones**

1. `HubHeartbeatServiceTest` — in `restaurantWithStatus(...)` add `restaurant.setDeploymentMode(DeploymentMode.HUB);` (import `com.vanter.ember.restaurant.model.DeploymentMode`).
2. `HubActivationServiceTest` — every `Restaurant` the tests build must be `HUB`: run `grep -n "Restaurant" backend/src/test/java/com/vanter/ember/licensing/service/HubActivationServiceTest.java` and add `.deploymentMode(DeploymentMode.HUB)` to each builder (or `setDeploymentMode`).
3. `PlatformRestaurantServiceTest` — the `issueHubLicense_*` tests stub `restaurantRepository.existsById(id)`; change them to `when(restaurantRepository.findById(id)).thenReturn(Optional.of(Restaurant.builder().id(id).deploymentMode(DeploymentMode.HUB).build()))` (and the not-found test to `findById(...)` returning `Optional.empty()`).

Append the new tests.

`HubHeartbeatServiceTest`:

```java
    @Test
    void heartbeat_restaurantThatMovedToWeb_returnsMigrated() throws InvalidLicenseException {
        Restaurant migrated = restaurantWithStatus(RestaurantStatus.ACTIVE);
        migrated.setDeploymentMode(DeploymentMode.CLOUD);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(migrated));

        assertThat(service.heartbeat(request(FP), IP).getStatus()).isEqualTo("MIGRATED");
    }

    @Test
    void heartbeat_migratedTakesPrecedenceOverSuspended() throws InvalidLicenseException {
        Restaurant migrated = restaurantWithStatus(RestaurantStatus.SUSPENDED);
        migrated.setDeploymentMode(DeploymentMode.CLOUD);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(migrated));

        assertThat(service.heartbeat(request(FP), IP).getStatus()).isEqualTo("MIGRATED");
    }
```

`HubActivationServiceTest` (adapt the request/fixture helpers already in the file; the assertion is what matters):

```java
    @Test
    void activate_refusesARestaurantThatIsNotInHubMode() throws Exception {
        // Use the same fixture the other activate_* tests use, but with a CLOUD restaurant.
        Restaurant web = Restaurant.builder().id(restaurantId).name("Web Grill").slug("web-grill")
                .deploymentMode(DeploymentMode.CLOUD).build();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(web));

        assertThatThrownBy(() -> service.activate(request(FP)))
                .isInstanceOf(IllegalStateException.class);
        verify(hubActivationRepository, never()).save(any());
    }
```
(Use the actual field names of that test class for the service, request helper and ids; keep the assertion identical.)

`PlatformRestaurantServiceTest`:

```java
    @Test
    void issueHubLicense_refusesARestaurantInWebMode() {
        UUID restaurantId = UUID.randomUUID();
        when(platformOperatorRepository.findByEmail("operator@ember.local"))
                .thenReturn(Optional.of(PlatformOperator.builder().id(UUID.randomUUID()).email("operator@ember.local").build()));
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(
                Restaurant.builder().id(restaurantId).deploymentMode(DeploymentMode.CLOUD).build()));

        assertThatThrownBy(() -> platformRestaurantService.issueHubLicense(restaurantId, "operator@ember.local"))
                .isInstanceOf(IllegalStateException.class);
        verify(licenseIssuingService, never()).issue(any());
    }
```
(copy the operator-stub style of the neighbouring `issueHubLicense_*` test if `PlatformOperator` has no builder)

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./mvnw test -Dtest='HubHeartbeatServiceTest,HubActivationServiceTest,PlatformRestaurantServiceTest'`
Expected: FAIL on the three new tests only.

- [ ] **Step 3: Implement**

`PlatformRestaurantService.issueHubLicense` — replace the `existsById` block:

```java
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));
        if (restaurant.getDeploymentMode() != DeploymentMode.HUB) {
            throw new IllegalStateException(
                    "Este restaurante usa Ember Web. Cambia su modo a Hub antes de emitir una licencia.");
        }
```
(import `com.vanter.ember.restaurant.model.DeploymentMode`)

`HubActivationService.activate` — right after `restaurant` is loaded:

```java
        if (restaurant.getDeploymentMode() != DeploymentMode.HUB) {
            throw new IllegalStateException("Este restaurante no está configurado para usar Ember Hub.");
        }
```

`HubHeartbeatService.heartbeat` — replace the `String status = ...` line:

```java
        // A restaurant that moved to Ember Web no longer belongs to any Hub; this beats SUSPENDED
        // so the Hub goes read-only instead of waiting on a reactivation that will never come.
        String status;
        if (restaurant.getDeploymentMode() != DeploymentMode.HUB) {
            status = "MIGRATED";
        } else {
            status = restaurant.getStatus() == RestaurantStatus.ACTIVE ? "OK" : "SUSPENDED";
        }
```
(imports as above)

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && ./mvnw test -Dtest='HubHeartbeatServiceTest,HubActivationServiceTest,HubActivationControllerTest,HubHeartbeatControllerTest,PlatformRestaurantServiceTest,PlatformRestaurantControllerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat(licensing): hub license, activation and heartbeat follow the restaurant's mode"
```

---

### Task 5: Console backend — create with mode, change mode, list filter

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantModeUpdateRequest.java`
- Modify: `platform/model/dto/PlatformRestaurantCreateRequest.java`, `PlatformRestaurantSummaryResponse.java`, `PlatformRestaurantDetailResponse.java`
- Modify: `restaurant/service/RestaurantService.java`, `restaurant/repository/RestaurantRepository.java`
- Modify: `platform/service/PlatformRestaurantService.java`, `platform/controller/PlatformRestaurantController.java`
- Test: `platform/service/PlatformRestaurantServiceTest.java`, `platform/controller/PlatformRestaurantControllerTest.java`

**Interfaces:**
- Produces: `POST /platform/restaurants` requires `deploymentMode`; `PATCH /platform/restaurants/{id}/mode` body `{ "mode": "CLOUD|HUB", "confirmSlug": "..." }`; `GET /platform/restaurants?mode=CLOUD|HUB`; `PlatformRestaurantService.updateDeploymentMode(UUID, DeploymentMode, String confirmSlug, String operatorEmail)`; `RestaurantService.updateDeploymentMode(UUID, DeploymentMode)`; summary/detail responses carry `deploymentMode`; audit action `RESTAURANT_MODE_CHANGED`.

- [ ] **Step 1: Write the failing tests**

In `PlatformRestaurantServiceTest` add:

```java
    @Test
    void create_storesTheChosenDeploymentMode() {
        PlatformRestaurantCreateRequest request = new PlatformRestaurantCreateRequest();
        request.setName("Hub Grill");
        request.setSlug("hub-grill");
        request.setDeploymentMode(DeploymentMode.HUB);
        request.setAdminName("Jane");
        request.setAdminEmail("jane@hub.test");
        request.setAdminPassword("Secret#123");
        when(platformOperatorRepository.findByEmail("operator@ember.local"))
                .thenReturn(Optional.of(operator()));
        when(restaurantRepository.existsBySlug("hub-grill")).thenReturn(false);
        when(userRepository.existsByEmail("jane@hub.test")).thenReturn(false);
        when(restaurantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        PlatformRestaurantSummaryResponse response = platformRestaurantService.create(request, "operator@ember.local");

        assertThat(response.getDeploymentMode()).isEqualTo(DeploymentMode.HUB);
    }

    @Test
    void updateDeploymentMode_changesTheModeAndWritesAnAuditRow() {
        UUID id = UUID.randomUUID();
        Restaurant hub = Restaurant.builder().id(id).slug("tenant-grill").deploymentMode(DeploymentMode.HUB).build();
        Restaurant web = Restaurant.builder().id(id).slug("tenant-grill").deploymentMode(DeploymentMode.CLOUD).build();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator()));
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(hub));
        when(restaurantService.updateDeploymentMode(id, DeploymentMode.CLOUD)).thenReturn(web);

        PlatformRestaurantSummaryResponse response = platformRestaurantService.updateDeploymentMode(
                id, DeploymentMode.CLOUD, "tenant-grill", "operator@ember.local");

        assertThat(response.getDeploymentMode()).isEqualTo(DeploymentMode.CLOUD);
        ArgumentCaptor<PlatformAuditLog> audit = ArgumentCaptor.forClass(PlatformAuditLog.class);
        verify(platformAuditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("RESTAURANT_MODE_CHANGED");
        assertThat(audit.getValue().getOldValue()).isEqualTo("HUB");
        assertThat(audit.getValue().getNewValue()).isEqualTo("CLOUD");
        assertThat(audit.getValue().getRestaurantId()).isEqualTo(id);
    }

    @Test
    void updateDeploymentMode_refusesAWrongConfirmationSlug() {
        UUID id = UUID.randomUUID();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator()));
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(
                Restaurant.builder().id(id).slug("tenant-grill").deploymentMode(DeploymentMode.HUB).build()));

        assertThatThrownBy(() -> platformRestaurantService.updateDeploymentMode(
                id, DeploymentMode.CLOUD, "another-slug", "operator@ember.local"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(restaurantService, never()).updateDeploymentMode(any(), any());
    }

    @Test
    void updateDeploymentMode_refusesTheModeItAlreadyHas() {
        UUID id = UUID.randomUUID();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator()));
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(
                Restaurant.builder().id(id).slug("tenant-grill").deploymentMode(DeploymentMode.HUB).build()));

        assertThatThrownBy(() -> platformRestaurantService.updateDeploymentMode(
                id, DeploymentMode.HUB, "tenant-grill", "operator@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getAll_withAModeFilter_usesTheModeQueries() {
        Pageable pageable = PageRequest.of(0, 10);
        when(restaurantRepository.findByStatusNotAndDeploymentMode(RestaurantStatus.DELETED, DeploymentMode.HUB, pageable))
                .thenReturn(Page.empty());
        when(restaurantRepository.findByDeploymentMode(DeploymentMode.HUB, pageable)).thenReturn(Page.empty());

        platformRestaurantService.getAll(pageable, false, DeploymentMode.HUB);
        platformRestaurantService.getAll(pageable, true, DeploymentMode.HUB);

        verify(restaurantRepository).findByStatusNotAndDeploymentMode(RestaurantStatus.DELETED, DeploymentMode.HUB, pageable);
        verify(restaurantRepository).findByDeploymentMode(DeploymentMode.HUB, pageable);
    }
```
(add a private helper `operator()` returning the `PlatformOperator` the other tests build — copy the construction used by `updatePlan_updatesRestaurantAndWritesAuditLog`; import `ArgumentCaptor`, `Page`, `PageRequest`, `Pageable`, `DeploymentMode`, `PlatformAuditLog` if missing)

In `PlatformRestaurantControllerTest`:
1. Add `"deploymentMode":"HUB"` to every valid create-request JSON and `deploymentMode(DeploymentMode.HUB)` to every `PlatformRestaurantSummaryResponse` the tests build.
2. Change every `platformRestaurantService.getAll(...)` stub/verification to the three-argument form `getAll(any(), eq(false), isNull())` (or the matching literal).
3. Add:

```java
    @Test
    @WithMockUser(roles = "PLATFORM_OPERATOR")
    void create_withoutADeploymentMode_isRejected() throws Exception {
        mockMvc.perform(post("/platform/restaurants").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tenant Grill\",\"slug\":\"tenant-grill\","
                                + "\"adminName\":\"Jane\",\"adminEmail\":\"jane@x.test\",\"adminPassword\":\"Secret#123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_OPERATOR")
    void updateMode_delegatesToTheServiceWithTheTypedSlug() throws Exception {
        UUID id = UUID.randomUUID();
        when(platformRestaurantService.updateDeploymentMode(eq(id), eq(DeploymentMode.CLOUD), eq("tenant-grill"), any()))
                .thenReturn(PlatformRestaurantSummaryResponse.builder().id(id).slug("tenant-grill")
                        .deploymentMode(DeploymentMode.CLOUD).build());

        mockMvc.perform(patch("/platform/restaurants/" + id + "/mode").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"CLOUD\",\"confirmSlug\":\"tenant-grill\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deploymentMode").value("CLOUD"));
    }
```
(match the role/annotation style, `MockMvc` imports and mocked-service field name used by the existing tests in that file)

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./mvnw test -Dtest='PlatformRestaurantServiceTest,PlatformRestaurantControllerTest'`
Expected: compilation FAIL — `setDeploymentMode` on the request, `updateDeploymentMode`, three-argument `getAll`, repository methods do not exist.

- [ ] **Step 3: Implement**

`PlatformRestaurantModeUpdateRequest.java`:

```java
package com.vanter.ember.platform.model.dto;

import com.vanter.ember.restaurant.model.DeploymentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlatformRestaurantModeUpdateRequest {

    @NotNull(message = "Mode is required")
    private DeploymentMode mode;

    /** The restaurant's slug, typed by the operator as a deliberate confirmation. */
    @NotBlank(message = "Slug confirmation is required")
    private String confirmSlug;
}
```

`PlatformRestaurantCreateRequest` — add (imports `DeploymentMode`, `jakarta.validation.constraints.NotNull`):

```java
    @NotNull(message = "Deployment mode is required")
    private DeploymentMode deploymentMode;
```

`PlatformRestaurantSummaryResponse` and `PlatformRestaurantDetailResponse` — add the field `private DeploymentMode deploymentMode;` and `.deploymentMode(restaurant.getDeploymentMode())` in each `builder()` chain.

`RestaurantService` — add next to `updatePlan`:

```java
    public Restaurant updateDeploymentMode(UUID restaurantId, DeploymentMode mode) {
        Restaurant restaurant = getCurrent(restaurantId);
        restaurant.setDeploymentMode(mode);
        return restaurantRepository.save(restaurant);
    }
```

`RestaurantRepository` — add:

```java
    Page<Restaurant> findByDeploymentMode(DeploymentMode mode, Pageable pageable);

    Page<Restaurant> findByStatusNotAndDeploymentMode(RestaurantStatus status, DeploymentMode mode, Pageable pageable);
```

`PlatformRestaurantService`:
- in `create(...)` add `.deploymentMode(request.getDeploymentMode())` to the `Restaurant.builder()`;
- replace `getAll(Pageable, boolean)`:

```java
    public Page<PlatformRestaurantSummaryResponse> getAll(Pageable pageable, boolean includeDeleted) {
        return getAll(pageable, includeDeleted, null);
    }

    public Page<PlatformRestaurantSummaryResponse> getAll(Pageable pageable, boolean includeDeleted,
                                                          DeploymentMode mode) {
        Page<Restaurant> page;
        if (mode == null) {
            page = includeDeleted
                    ? restaurantRepository.findAll(pageable)
                    : restaurantRepository.findByStatusNot(RestaurantStatus.DELETED, pageable);
        } else {
            page = includeDeleted
                    ? restaurantRepository.findByDeploymentMode(mode, pageable)
                    : restaurantRepository.findByStatusNotAndDeploymentMode(RestaurantStatus.DELETED, mode, pageable);
        }
        List<UUID> ids = page.getContent().stream().map(Restaurant::getId).toList();
        Map<UUID, HubActivation> byRestaurant = ids.isEmpty()
                ? Map.of()
                : hubActivationRepository.findByRestaurantIdIn(ids).stream()
                        .collect(Collectors.toMap(HubActivation::getRestaurantId, a -> a));
        return page.map(r -> PlatformRestaurantSummaryResponse.from(r, byRestaurant.get(r.getId())));
    }
```
- add after `updatePlan`:

```java
    /**
     * Operator-driven switch between Ember Web and Ember Hub. No data moves: each side keeps its
     * own. The typed slug is verified here too, not only in the UI. Audited in the same
     * transaction, like every other operator action.
     */
    @Transactional
    public PlatformRestaurantSummaryResponse updateDeploymentMode(UUID restaurantId, DeploymentMode newMode,
                                                                  String confirmSlug, String operatorEmail) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(operatorEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        if (!restaurant.getSlug().equals(confirmSlug)) {
            throw new IllegalArgumentException("El slug de confirmación no coincide.");
        }
        DeploymentMode oldMode = restaurant.getDeploymentMode();
        if (oldMode == newMode) {
            throw new IllegalStateException("El restaurante ya usa el modo " + newMode + ".");
        }

        Restaurant updated = restaurantService.updateDeploymentMode(restaurantId, newMode);

        platformAuditLogRepository.save(PlatformAuditLog.builder()
                .operatorId(operator.getId())
                .operatorEmail(operator.getEmail())
                .restaurantId(restaurantId)
                .action("RESTAURANT_MODE_CHANGED")
                .oldValue(oldMode.name())
                .newValue(newMode.name())
                .build());

        return PlatformRestaurantSummaryResponse.from(updated);
    }
```

`PlatformRestaurantController` — replace `getAll` and add the endpoint (imports `DeploymentMode`, `PlatformRestaurantModeUpdateRequest`):

```java
    @Operation(summary = "List all tenants, paginated; soft-deleted excluded unless includeDeleted=true; optional mode filter")
    @GetMapping
    public ResponseEntity<Page<PlatformRestaurantSummaryResponse>> getAll(
            Pageable pageable,
            @RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(name = "mode", required = false) DeploymentMode mode) {
        return ResponseEntity.ok(platformRestaurantService.getAll(pageable, includeDeleted, mode));
    }

    @Operation(summary = "Change a tenant's deployment mode (Web/Hub), audited; the slug must be typed as confirmation")
    @PatchMapping("/{id}/mode")
    public ResponseEntity<PlatformRestaurantSummaryResponse> updateMode(
            @PathVariable UUID id,
            @Valid @RequestBody PlatformRestaurantModeUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(platformRestaurantService.updateDeploymentMode(
                id, request.getMode(), request.getConfirmSlug(), authentication.getName()));
    }
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && ./mvnw test -Dtest='PlatformRestaurantServiceTest,PlatformRestaurantControllerTest,RestaurantServiceTest,Restaurant*RepositoryTest'`
Expected: PASS.

- [ ] **Step 5: Run the whole backend suite**

Run: `cd backend && ./mvnw test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat(platform): create restaurants with a mode, switch mode with audit, filter the list by mode"
```

---

### Task 6: Console frontend — selector, badge, filter, change-mode dialog

**Files:**
- Modify: `frontend/src/lib/platformApi.ts`
- Modify: `frontend/src/pages/console/ConsoleRestaurantCreate.tsx`, `ConsoleRestaurants.tsx`, `ConsoleRestaurantDetail.tsx`
- Test: `frontend/src/pages/console/ConsoleRestaurantDetail.test.tsx`, `ConsoleRestaurants.test.tsx`
- Test: `frontend/src/pages/console/ConsoleRestaurantCreate.test.tsx` (new)

**Interfaces:**
- Consumes: backend endpoints of Task 5.
- Produces: `DeploymentMode` type; `platformRestaurantService.updateMode(id, mode, confirmSlug)`; `platformRestaurantService.getAll(page, size, includeDeleted, mode?)`.

- [ ] **Step 1: Write the failing tests**

`ConsoleRestaurantCreate.test.tsx`:

```tsx
import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ConsoleRestaurantCreate from '@/pages/console/ConsoleRestaurantCreate'
import { platformRestaurantService } from '@/lib/platformApi'

vi.mock('react-hot-toast', () => ({
  default: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }),
}))

vi.mock('@/lib/platformApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/platformApi')>()
  return {
    ...actual,
    platformRestaurantService: { ...actual.platformRestaurantService, create: vi.fn() },
  }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  )

describe('ConsoleRestaurantCreate', () => {
  beforeEach(() => vi.clearAllMocks())

  test('the mode is required: no default, and the form does not submit without it', async () => {
    wrap(<ConsoleRestaurantCreate />)

    fireEvent.change(screen.getByLabelText('Nombre del restaurante'), { target: { value: 'Hub Grill' } })
    fireEvent.change(screen.getByLabelText('Slug'), { target: { value: 'hub-grill' } })
    fireEvent.change(screen.getByLabelText('Nombre del administrador'), { target: { value: 'Jane' } })
    fireEvent.change(screen.getByLabelText('Email del administrador'), { target: { value: 'jane@hub.test' } })
    fireEvent.change(screen.getByLabelText('Contraseña inicial'), { target: { value: 'Secret#123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear restaurante' }))

    expect(await screen.findByText('Elige el modo de uso')).toBeInTheDocument()
    await waitFor(() => expect(platformRestaurantService.create).not.toHaveBeenCalled())
  })

  test('offers the two modes with a label an operator understands', () => {
    wrap(<ConsoleRestaurantCreate />)

    expect(screen.getByText('Modo de uso')).toBeInTheDocument()
  })
})
```

`ConsoleRestaurantDetail.test.tsx` — (a) in the `vi.mock` block add `updateMode: vi.fn()`; (b) in the `detail()` helper add `deploymentMode: 'HUB',`; (c) add tests:

```tsx
  test('shows the mode and lets the operator change it only after typing the slug', async () => {
    vi.mocked(platformRestaurantService.getById).mockResolvedValue(detail({ deploymentMode: 'HUB' }) as never)
    vi.mocked(platformRestaurantService.updateMode).mockResolvedValue({} as never)

    wrap(<ConsoleRestaurantDetail />)

    expect(await screen.findByText('Hub')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar modo' }))

    // consequences and the plan reminder are on screen
    expect(screen.getByText(/su Hub queda en modo consulta/i)).toBeInTheDocument()
    expect(screen.getByText(/Plan actual:/)).toBeInTheDocument()
    const confirm = screen.getByRole('button', { name: 'Confirmar cambio' })
    expect(confirm).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Escribe el slug para confirmar'), { target: { value: 'tenant-grill' } })
    expect(confirm).toBeEnabled()
    fireEvent.click(confirm)

    await waitFor(() =>
      expect(platformRestaurantService.updateMode).toHaveBeenCalledWith('r-1', 'CLOUD', 'tenant-grill'))
  })

  test('a Web restaurant cannot be issued a Hub license from here', async () => {
    vi.mocked(platformRestaurantService.getById).mockResolvedValue(
      detail({ deploymentMode: 'CLOUD', status: 'ACTIVE' }) as never)

    wrap(<ConsoleRestaurantDetail />)

    expect(await screen.findByRole('button', { name: 'Emitir licencia Hub' })).toBeDisabled()
  })
```
(the dialog opens for the label the tests use: keep the exact strings `Cambiar modo`, `Confirmar cambio`, `Escribe el slug para confirmar`, `Plan actual:`)

`ConsoleRestaurants.test.tsx` — update the existing assertion at ~line 62 to `toHaveBeenLastCalledWith(0, 10, true, undefined)` and add:

```tsx
  test('shows a mode badge per row and filters by mode', async () => {
    vi.mocked(platformRestaurantService.getAll).mockResolvedValue(
      page([row({ deploymentMode: 'HUB' })]) as never)

    wrap(<ConsoleRestaurants />)

    expect(await screen.findByText('Hub')).toBeInTheDocument()
    expect(screen.getByLabelText('Filtrar por modo')).toBeInTheDocument()
  })
```
(use the existing `row(...)`/`page(...)`/`wrap` helpers of that file; if `row` does not accept overrides, add `deploymentMode: 'CLOUD'` to its default and spread the overrides)

- [ ] **Step 2: Run to verify they fail**

Run: `cd frontend && pnpm exec vitest run src/pages/console`
Expected: FAIL — the new labels/buttons do not exist.

- [ ] **Step 3: Implement**

`platformApi.ts`:
- add `export type DeploymentMode = 'CLOUD' | 'HUB'`;
- add `deploymentMode: DeploymentMode` to `PlatformRestaurantSummary` and `PlatformRestaurantDetail`, and `deploymentMode: DeploymentMode` to `PlatformRestaurantCreateRequest`;
- replace `getAll`:

```ts
  getAll: async (
    page = 0,
    size = 10,
    includeDeleted = false,
    mode?: DeploymentMode
  ): Promise<Page<PlatformRestaurantSummary>> => {
    const { data } = await platformApi.get<Page<PlatformRestaurantSummary>>(
      '/platform/restaurants',
      { params: { page, size, includeDeleted, ...(mode ? { mode } : {}) } }
    )
    return data
  },
```
- add after `updatePlan`:

```ts
  updateMode: async (
    id: string,
    mode: DeploymentMode,
    confirmSlug: string
  ): Promise<PlatformRestaurantSummary> => {
    const { data } = await platformApi.patch<PlatformRestaurantSummary>(
      `/platform/restaurants/${id}/mode`,
      { mode, confirmSlug }
    )
    return data
  },
```

`ConsoleRestaurantCreate.tsx`:
- import `type DeploymentMode` from `@/lib/platformApi`;
- schema field (after `plan`): `deploymentMode: z.enum(['', 'CLOUD', 'HUB']).refine((v) => v !== '', 'Elige el modo de uso'),`
- `defaultValues`: add `deploymentMode: '',`
- mutation: `mutationFn: (data: CreateFormInputs) => platformRestaurantService.create({ ...data, deploymentMode: data.deploymentMode as DeploymentMode }),`
- new field (right after the `plan` field):

```tsx
              <FormField
                control={form.control}
                name="deploymentMode"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Modo de uso</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder="Selecciona Web o Hub" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="CLOUD">Web (Ember en la nube)</SelectItem>
                        <SelectItem value="HUB">Hub (instalado en el local)</SelectItem>
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
```

`ConsoleRestaurants.tsx`:
- imports `Select, SelectContent, SelectItem, SelectTrigger, SelectValue` from `@/components/ui/select` and `type DeploymentMode` from `@/lib/platformApi`;
- state `const [mode, setMode] = useState<DeploymentMode | 'ALL'>('ALL')`;
- query: `queryKey: ['platformRestaurants', page, includeDeleted, mode]`, `queryFn: () => platformRestaurantService.getAll(page, 10, includeDeleted, mode === 'ALL' ? undefined : mode)`;
- helper (top of file):

```tsx
const modeBadgeClass = (mode: string) =>
  mode === 'HUB' ? 'bg-amber-100 text-amber-800' : 'bg-blue-100 text-blue-700'
const modeLabel = (mode: string) => (mode === 'HUB' ? 'Hub' : 'Web')
```
- filter control next to the "Ver eliminados" switch:

```tsx
        <Select
          value={mode}
          onValueChange={(value) => {
            setMode(value as DeploymentMode | 'ALL')
            setPage(0)
          }}
        >
          <SelectTrigger className="w-40" aria-label="Filtrar por modo">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todos los modos</SelectItem>
            <SelectItem value="CLOUD">Web</SelectItem>
            <SelectItem value="HUB">Hub</SelectItem>
          </SelectContent>
        </Select>
```
- table: add `<TableHead>Modo</TableHead>` after `Plan`, the cell `<TableCell><Badge className={modeBadgeClass(restaurant.deploymentMode)}>{modeLabel(restaurant.deploymentMode)}</Badge></TableCell>` after the plan cell, and change the three `colSpan={6}` to `colSpan={7}`.

`ConsoleRestaurantDetail.tsx`:
- imports `type DeploymentMode`; state `const [showModeConfirm, setShowModeConfirm] = useState(false)` and `const [modeSlugInput, setModeSlugInput] = useState('')`;
- helpers next to `nextStatus`:

```tsx
const modeBadgeClass = (mode: string) =>
  mode === 'HUB' ? 'bg-amber-100 text-amber-800' : 'bg-blue-100 text-blue-700'
const modeLabel = (mode: string) => (mode === 'HUB' ? 'Hub' : 'Web')
const otherMode = (mode: DeploymentMode): DeploymentMode => (mode === 'HUB' ? 'CLOUD' : 'HUB')
```
- mutation after `changePlan`:

```tsx
  const changeMode = useMutation({
    mutationFn: (mode: DeploymentMode) =>
      platformRestaurantService.updateMode(id!, mode, modeSlugInput),
    onSuccess: () => {
      setShowModeConfirm(false)
      setModeSlugInput('')
      invalidateAll()
    },
    onError: (error) => {
      const detail =
        axios.isAxiosError(error) &&
        typeof (error.response?.data as { detail?: unknown })?.detail === 'string'
          ? (error.response!.data as { detail: string }).detail
          : undefined
      toast.error(detail ?? 'No se pudo cambiar el modo', { id: 'console-mode-error', duration: 5000 })
    },
  })
```
- the license button: `disabled={issueHubLicense.isPending || restaurant.deploymentMode !== 'HUB'}` and `title={restaurant.deploymentMode !== 'HUB' ? 'Solo restaurantes en modo Hub' : undefined}`;
- in the "Datos" card grid add (after the Plan block):

```tsx
          <div>
            <div className="text-zinc-500">Modo de uso</div>
            <div className="flex items-center gap-2">
              <Badge className={modeBadgeClass(restaurant.deploymentMode)}>
                {modeLabel(restaurant.deploymentMode)}
              </Badge>
              <Button
                variant="outline"
                size="sm"
                disabled={restaurant.status === 'DELETED'}
                onClick={() => setShowModeConfirm(true)}
              >
                Cambiar modo
              </Button>
            </div>
          </div>
```
- the dialog, next to the delete dialog:

```tsx
      <Dialog
        open={showModeConfirm}
        onOpenChange={(open) => {
          setShowModeConfirm(open)
          if (!open) setModeSlugInput('')
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cambiar modo de uso</DialogTitle>
            <DialogDescription>
              {restaurant.deploymentMode === 'HUB' ? (
                <>
                  Pasar <span className="font-medium">{restaurant.name}</span> a <strong>Web</strong>:
                  su Hub queda en modo consulta (solo lectura) tras 48 h y la cuenta web empieza
                  vacía. No se copian datos.
                </>
              ) : (
                <>
                  Pasar <span className="font-medium">{restaurant.name}</span> a <strong>Hub</strong>:
                  el acceso a Ember Web se bloquea de inmediato y sus datos en la nube quedan
                  guardados pero inaccesibles. No se copian datos.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          <p className="text-sm text-zinc-600">
            Plan actual: <span className="font-medium">{restaurant.plan}</span>. Asigna el plan
            acordado después del cambio.
          </p>
          <div className="flex flex-col gap-2">
            <Label htmlFor="mode-slug" className="text-sm text-zinc-600">
              Escribe el slug para confirmar
            </Label>
            <input
              id="mode-slug"
              className="rounded-md border border-zinc-300 px-2 py-1 text-sm"
              value={modeSlugInput}
              onChange={(e) => setModeSlugInput(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowModeConfirm(false)
                setModeSlugInput('')
              }}
            >
              Cancelar
            </Button>
            <Button
              disabled={modeSlugInput !== restaurant.slug || changeMode.isPending}
              onClick={() => changeMode.mutate(otherMode(restaurant.deploymentMode))}
            >
              Confirmar cambio
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd frontend && pnpm exec vitest run src/pages/console`
Expected: PASS.

- [ ] **Step 5: Type-check, lint and full test run**

Run: `cd frontend && pnpm run test:run && pnpm run lint && pnpm run build`
Expected: tests PASS, lint 0 errors, build clean.

- [ ] **Step 6: Commit**

```bash
git add frontend/src
git commit -m "feat(console): choose the mode when creating, show it, filter by it and switch it with a typed-slug confirmation"
```

---

### Task 7: Hub state — `migratedSince`, MAC compatibility, courtesy and heartbeat handling

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/hub/license/HubState.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/license/HubStateStore.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/license/LicenseService.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/sync/HeartbeatScheduler.java`
- Test: `hub/license/HubStateStoreTest.java`, `hub/license/LicenseServiceTest.java`, `hub/sync/HeartbeatSchedulerTest.java`

**Interfaces:**
- Produces: `HubState.migratedSince()` (6th record component, JSON key omitted when null); `HubState.withMigratedSince(Instant)`; `HubState.withHeartbeatNow(boolean)` clears it; `HubStateStore.canonical(HubState)` (package-private static); `LicenseService.recordMigrated(HubState)`; `LicenseService.isMigratedGraceExpired(HubState)`.

- [ ] **Step 1: Write the failing tests**

`HubStateStoreTest`:

```java
    @Test
    void save_thenLoad_roundTripsMigratedSince() {
        HubStateStore store = new HubStateStore(tempDir.resolve("migrated/hub-state.json"));
        Instant since = Instant.parse("2026-09-21T10:00:00Z");
        HubState state = new HubState("fp", UUID.randomUUID(), Instant.now(), null, Instant.now(), since);

        store.save(state);

        assertThat(store.load().orElseThrow().migratedSince()).isEqualTo(since);
    }

    @Test
    void save_omitsTheMigratedSinceKeyWhenNull_soOlderHubsCanStillReadTheFile() throws Exception {
        Path stateFile = tempDir.resolve("nokey/hub-state.json");
        new HubStateStore(stateFile).save(new HubState("fp", UUID.randomUUID(), Instant.now()));

        assertThat(Files.readString(stateFile)).doesNotContain("migratedSince");
    }

    @Test
    void canonical_ofAStateWithoutMigratedSince_isTheLegacyFiveFieldString() {
        // The MAC of every hub-state.json already written by an installed Hub was computed over
        // exactly these five fields; changing this string would fail-close every existing install.
        UUID restaurantId = UUID.randomUUID();
        Instant heartbeat = Instant.parse("2026-09-20T10:00:00Z");
        Instant seen = Instant.parse("2026-09-20T10:05:00Z");
        HubState state = new HubState("fp", restaurantId, heartbeat, null, seen);

        assertThat(HubStateStore.canonical(state))
                .isEqualTo("fp|" + restaurantId + "|" + heartbeat + "||" + seen);
    }

    @Test
    void canonical_appendsMigratedSinceOnlyWhenPresent() {
        UUID restaurantId = UUID.randomUUID();
        Instant since = Instant.parse("2026-09-21T10:00:00Z");
        HubState state = new HubState("fp", restaurantId, Instant.EPOCH, null, null, since);

        assertThat(HubStateStore.canonical(state)).endsWith("|" + since);
    }

    @Test
    void load_aMigratedSinceThatWasEditedByHand_failsClosed() throws Exception {
        Path stateFile = tempDir.resolve("edited/hub-state.json");
        HubStateStore store = new HubStateStore(stateFile);
        store.save(new HubState("fp", UUID.randomUUID(), Instant.now(), null, Instant.now(),
                Instant.now().minus(3, ChronoUnit.DAYS)));

        Files.writeString(stateFile, Files.readString(stateFile).replaceAll("\"migratedSince\"\\s*:\\s*[^,\\n}]+", "\"migratedSince\":null"));

        assertThat(store.load().orElseThrow().lastHeartbeatAt()).isEqualTo(Instant.EPOCH);
    }
```

`LicenseServiceTest` (the class already has `licenseService` with a 48 h courtesy and `newServiceWithGrace(...)`/`mockStateStore` helpers):

```java
    @Test
    void recordMigrated_firstTime_stampsAndPersists() {
        HubState before = new HubState("fp", UUID.randomUUID(), Instant.now(), null);
        LicenseService service = newServiceWithGrace(Duration.ofHours(48));

        HubState after = service.recordMigrated(before);

        assertThat(after.migratedSince()).isNotNull();
        verify(mockStateStore).save(after);
    }

    @Test
    void recordMigrated_alreadyMigrated_isNoOp_soTheCourtesyClockIsNotReset() {
        Instant original = Instant.now().minus(5, ChronoUnit.HOURS);
        HubState before = new HubState("fp", UUID.randomUUID(), Instant.now(), null, null, original);
        LicenseService service = newServiceWithGrace(Duration.ofHours(48));

        HubState after = service.recordMigrated(before);

        assertThat(after.migratedSince()).isEqualTo(original);
        verify(mockStateStore, never()).save(any());
    }

    @Test
    void isMigratedGraceExpired_falseWhenNotMigrated_falseWithin48h_trueAfter() {
        LicenseService service = newServiceWithGrace(Duration.ofHours(48));
        UUID id = UUID.randomUUID();

        assertThat(service.isMigratedGraceExpired(new HubState("fp", id, Instant.now()))).isFalse();
        assertThat(service.isMigratedGraceExpired(new HubState("fp", id, Instant.now(), null, null,
                Instant.now().minus(10, ChronoUnit.HOURS)))).isFalse();
        assertThat(service.isMigratedGraceExpired(new HubState("fp", id, Instant.now(), null, null,
                Instant.now().minus(49, ChronoUnit.HOURS)))).isTrue();
    }

    @Test
    void recordHeartbeatSuccess_clearsMigrated_soFlippingTheModeBackResumesTheHub() {
        HubState migrated = new HubState("fp", UUID.randomUUID(), Instant.now(), null, null,
                Instant.now().minus(3, ChronoUnit.DAYS));
        stateStore.save(migrated);

        HubState after = licenseService.recordHeartbeatSuccess(migrated, Instant.now());

        assertThat(after.migratedSince()).isNull();
        assertThat(licenseService.isMigratedGraceExpired(after)).isFalse();
    }
```

`HeartbeatSchedulerTest` (uses the file's `startSigningServer(status, signer, wrongNonce)` helper):

```java
    @Test
    void migrated_stampsMigratedSince_andLeavesTheHeartbeat() throws Exception {
        Instant oldHeartbeat = Instant.now().minus(2, ChronoUnit.DAYS);
        stateStore.save(new HubState("fp", restaurantId, oldHeartbeat, null));
        startSigningServer("MIGRATED", cloudKeys.getPrivate(), false);

        new HeartbeatScheduler(propsPointingAt(url()), stateStore, licenseService).runHeartbeat();

        HubState after = stateStore.load().orElseThrow();
        assertThat(after.migratedSince()).isNotNull();
        assertThat(after.lastHeartbeatAt()).isEqualTo(oldHeartbeat);
    }

    @Test
    void unsignedMigrated_isIgnored() throws Exception {
        stateStore.save(new HubState("fp", restaurantId, Instant.now().minus(2, ChronoUnit.DAYS), null));
        startServer(200, "{\"status\":\"MIGRATED\",\"serverTime\":\"2026-08-28T00:00:00Z\",\"latestVersion\":null}");

        new HeartbeatScheduler(propsPointingAt(url()), stateStore, licenseService).runHeartbeat();

        assertThat(stateStore.load().orElseThrow().migratedSince()).isNull();
    }

    @Test
    void okAfterMigrated_clearsMigratedSince() throws Exception {
        stateStore.save(new HubState("fp", restaurantId, Instant.now().minus(2, ChronoUnit.DAYS), null, null,
                Instant.now().minus(1, ChronoUnit.DAYS)));
        startSigningServer("OK", cloudKeys.getPrivate(), false);

        new HeartbeatScheduler(propsPointingAt(url()), stateStore, licenseService).runHeartbeat();

        assertThat(stateStore.load().orElseThrow().migratedSince()).isNull();
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./mvnw test -Dtest='HubStateStoreTest,LicenseServiceTest,HeartbeatSchedulerTest'`
Expected: compilation FAIL — six-argument `HubState`, `canonical`, `recordMigrated`, `isMigratedGraceExpired` do not exist.

- [ ] **Step 3: Implement**

`HubState.java` — replace the record header and constructors, and carry the new field through every `with*`:

```java
public record HubState(
        String hardwareFingerprint,
        UUID restaurantId,
        Instant lastHeartbeatAt,
        Instant suspendedSince,
        Instant lastSeenAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant migratedSince) {

    /** Back-compat constructor for the common "no suspension" case. */
    public HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt) {
        this(hardwareFingerprint, restaurantId, lastHeartbeatAt, null, null, null);
    }

    public HubState(
            String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt, Instant suspendedSince) {
        this(hardwareFingerprint, restaurantId, lastHeartbeatAt, suspendedSince, null, null);
    }

    public HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt,
                    Instant suspendedSince, Instant lastSeenAt) {
        this(hardwareFingerprint, restaurantId, lastHeartbeatAt, suspendedSince, lastSeenAt, null);
    }

    /** A successful (OK) heartbeat clears both a suspension and a migration. */
    public HubState withHeartbeatNow(boolean clockAgreesWithCloud) {
        Instant now = Instant.now();
        Instant seen = clockAgreesWithCloud ? now : latest(lastSeenAt, now);
        return new HubState(hardwareFingerprint, restaurantId, now, null, seen, null);
    }

    public HubState withSuspendedSince(Instant when) {
        return new HubState(hardwareFingerprint, restaurantId, lastHeartbeatAt, when, lastSeenAt, migratedSince);
    }

    public HubState withMigratedSince(Instant when) {
        return new HubState(hardwareFingerprint, restaurantId, lastHeartbeatAt, suspendedSince, lastSeenAt, when);
    }

    public HubState withLastHeartbeatAt(Instant when) {
        return new HubState(hardwareFingerprint, restaurantId, when, suspendedSince, lastSeenAt, migratedSince);
    }

    public HubState withLastSeenAtAdvancedTo(Instant now) {
        return new HubState(hardwareFingerprint, restaurantId, lastHeartbeatAt, suspendedSince,
                latest(lastSeenAt, now), migratedSince);
    }
```
(keep the private `latest` helper; add `import com.fasterxml.jackson.annotation.JsonInclude;`)

`HubStateStore.java` — extract and extend the canonical string:

```java
    /**
     * The exact text the MAC is computed over. {@code migratedSince} is appended ONLY when set:
     * every hub-state.json written before that field existed carries a MAC over the five original
     * fields, and it must keep verifying.
     */
    static String canonical(HubState state) {
        String canonical = String.join("|",
                String.valueOf(state.hardwareFingerprint()),
                String.valueOf(state.restaurantId()),
                field(state.lastHeartbeatAt()),
                field(state.suspendedSince()),
                field(state.lastSeenAt()));
        if (state.migratedSince() != null) {
            canonical += "|" + field(state.migratedSince());
        }
        return canonical;
    }
```
and in `mac(...)` replace the inline `String canonical = String.join(...)` with `String canonical = canonical(state);`. In `load()` the tampered branch must keep the migration marker so tampering cannot reopen writes: change it to

```java
                return Optional.of(new HubState(state.hardwareFingerprint(), state.restaurantId(),
                        Instant.EPOCH, Instant.EPOCH, state.lastSeenAt(), state.migratedSince()));
```

`LicenseService.java` — add after `recordSuspended`:

```java
    /**
     * Stamps {@code migratedSince} the first time the cloud reports MIGRATED (the restaurant moved
     * to Ember Web) and persists it; later cycles are a no-op so the courtesy clock is not reset.
     */
    public HubState recordMigrated(HubState state) {
        if (state.migratedSince() != null) {
            return state;
        }
        HubState updated = state.withMigratedSince(Instant.now());
        stateStore.save(updated);
        return updated;
    }

    /** True once the courtesy window after MIGRATED has passed: from then on the Hub is read-only. */
    public boolean isMigratedGraceExpired(HubState state) {
        return state.migratedSince() != null
                && Duration.between(state.migratedSince(), Instant.now()).compareTo(suspendedGrace) > 0;
    }
```

`HeartbeatScheduler.java` — add the branch between `SUSPENDED` and the `else`:

```java
            } else if ("MIGRATED".equals(body.status())) {
                licenseService.recordMigrated(state);
                log.warn("Heartbeat reports the restaurant now runs on Ember Web (MIGRATED); this Hub "
                        + "goes read-only after the courtesy period.");
            } else {
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && ./mvnw test -Dtest='HubStateStoreTest,LicenseServiceTest,HeartbeatSchedulerTest,GracePeriodInterceptorTest,DefaultHubOrchestratorTest,HubProvisioningRunnerTest'`
Expected: PASS (every existing `new HubState(...)` call still compiles through the back-compat constructors).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub backend/src/test/java/com/vanter/ember/hub
git commit -m "feat(hub): record a migrated restaurant in the signed state, with courtesy and mac compatibility"
```

---

### Task 8: Hub read-only interceptor and Hub UI state

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/license/ReadOnlyModeInterceptor.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/config/HubWebConfig.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/control/HubOrchestrator.java`, `DefaultHubOrchestrator.java`
- Modify: `ember-hub/ui/src/lib/types.ts`, `ember-hub/ui/src/components/LicenseCard.tsx`
- Test: `backend/src/test/java/com/vanter/ember/hub/license/ReadOnlyModeInterceptorTest.java` (new), `hub/control/DefaultHubOrchestratorTest.java`
- Test: `ember-hub/ui/src/components/LicenseCard.test.tsx`

**Interfaces:**
- Consumes: `LicenseService.isMigratedGraceExpired(HubState)`, `HubStateStore.load()`.
- Produces: `ReadOnlyModeInterceptor(LicenseService, HubStateStore)`; `HubOrchestrator.LicenseSnapshot.MIGRATED` and `migratedSince()` (a three-argument constructor stays for existing callers); UI status `'MIGRATED'`.

- [ ] **Step 1: Write the failing tests**

`ReadOnlyModeInterceptorTest.java`:

```java
package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReadOnlyModeInterceptorTest {

    private final LicenseService licenseService = mock(LicenseService.class);
    private final HubStateStore stateStore = mock(HubStateStore.class);
    private final ReadOnlyModeInterceptor interceptor = new ReadOnlyModeInterceptor(licenseService, stateStore);
    private final HubState migrated =
            new HubState("fp", UUID.randomUUID(), Instant.now(), null, null, Instant.now().minusSeconds(3 * 86400));

    private HttpServletRequest request(String method) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        return request;
    }

    private boolean handle(String method, StringWriter body, HttpServletResponse response) throws Exception {
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        return interceptor.preHandle(request(method), response, new Object());
    }

    @Test
    void afterTheCourtesy_everyWriteIsRefused_withAClearMessage() throws Exception {
        when(stateStore.load()).thenReturn(Optional.of(migrated));
        when(licenseService.isMigratedGraceExpired(migrated)).thenReturn(true);

        for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
            HttpServletResponse response = mock(HttpServletResponse.class);
            StringWriter body = new StringWriter();

            assertThat(handle(method, body, response)).as(method).isFalse();

            org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            assertThat(body.toString()).contains("license_migrated").contains("modo consulta");
        }
    }

    @Test
    void reads_areNeverBlocked() throws Exception {
        when(stateStore.load()).thenReturn(Optional.of(migrated));
        when(licenseService.isMigratedGraceExpired(migrated)).thenReturn(true);

        for (String method : new String[] {"GET", "HEAD", "OPTIONS"}) {
            assertThat(handle(method, new StringWriter(), mock(HttpServletResponse.class))).as(method).isTrue();
        }
    }

    @Test
    void duringTheCourtesy_writesStillWork() throws Exception {
        when(stateStore.load()).thenReturn(Optional.of(migrated));
        when(licenseService.isMigratedGraceExpired(migrated)).thenReturn(false);

        assertThat(handle("POST", new StringWriter(), mock(HttpServletResponse.class))).isTrue();
    }

    @Test
    void aHubThatWasNeverMigrated_isUntouched() throws Exception {
        HubState healthy = new HubState("fp", UUID.randomUUID(), Instant.now());
        when(stateStore.load()).thenReturn(Optional.of(healthy));
        when(licenseService.isMigratedGraceExpired(healthy)).thenReturn(false);

        assertThat(handle("DELETE", new StringWriter(), mock(HttpServletResponse.class))).isTrue();
    }

    @Test
    void withNoStateFile_theInterceptorLeavesItToTheOtherGuards() throws Exception {
        when(stateStore.load()).thenReturn(Optional.empty());

        assertThat(handle("POST", new StringWriter(), mock(HttpServletResponse.class))).isTrue();
    }
}
```

`DefaultHubOrchestratorTest` — add:

```java
    @Test
    void snapshot_migratedState_reportsMigratedWithItsSince() {
        Path stateFile = tempDir.resolve("hub-state.json");
        Instant migratedSince = Instant.parse("2026-09-21T08:00:00Z");
        HubState migrated = new HubState("fp-1", UUID.randomUUID(), Instant.parse("2026-09-20T08:00:00Z"))
                .withMigratedSince(migratedSince);
        new HubStateStore(stateFile).save(migrated);

        HubOrchestrator.LicenseSnapshot license =
                new DefaultHubOrchestrator(propertiesWithStateFile(stateFile)).snapshot().license();

        assertEquals(HubOrchestrator.LicenseSnapshot.MIGRATED, license.status());
        assertEquals(migratedSince, license.migratedSince());
    }
```

`LicenseCard.test.tsx` — add (mirror the existing "Suspendida" test's render call):

```tsx
  it('shows a "Migrada a Web (solo lectura)" badge and explains the read-only mode', () => {
    render(
      <LicenseCard
        license={{ status: 'MIGRATED', lastHeartbeatAt: null, suspendedSince: null, migratedSince: new Date().toISOString() }}
        onSelectLicense={() => {}}
        onRemoveLicense={() => {}}
      />
    );

    expect(screen.getByText('Migrada a Web (solo lectura)')).toBeInTheDocument();
    expect(screen.getByText(/modo consulta/i)).toBeInTheDocument();
  });
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./mvnw test -Dtest='ReadOnlyModeInterceptorTest,DefaultHubOrchestratorTest'` and `cd ember-hub/ui && pnpm run test`
Expected: FAIL — `ReadOnlyModeInterceptor`, `MIGRATED`, `migratedSince()` do not exist; the badge is missing.

- [ ] **Step 3: Implement**

`ReadOnlyModeInterceptor.java`:

```java
package com.vanter.ember.hub.license;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * "Modo consulta": once the restaurant has moved to Ember Web and the courtesy window has passed,
 * this Hub keeps answering reads (history, the Excel export) but refuses every write. Nothing local
 * is ever deleted. Registered in {@code HubWebConfig} for every path except login, the SPA and the
 * health endpoints.
 */
public class ReadOnlyModeInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final LicenseService licenseService;
    private final HubStateStore stateStore;

    public ReadOnlyModeInterceptor(LicenseService licenseService, HubStateStore stateStore) {
        this.licenseService = licenseService;
        this.stateStore = stateStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return true;
        }
        Optional<HubState> state = stateStore.load();
        if (state.isEmpty() || !licenseService.isMigratedGraceExpired(state.get())) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"license_migrated\","
                        + "\"message\":\"Tu restaurante ahora usa Ember Web. "
                        + "Este Hub está en modo consulta.\"}");
        return false;
    }
}
```

`HubWebConfig.java` — add the field, build it in the constructor, register it:

```java
    private final ReadOnlyModeInterceptor readOnlyModeInterceptor;

    public HubWebConfig(LicenseService licenseService, HubStateStore stateStore) {
        this.gracePeriodInterceptor = new GracePeriodInterceptor(licenseService, stateStore);
        this.readOnlyModeInterceptor = new ReadOnlyModeInterceptor(licenseService, stateStore);
    }
```
```java
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gracePeriodInterceptor)
                .addPathPatterns("/sessions/*/items", "/sessions/*/participants/*/confirm", "/billing/**");
        registry.addInterceptor(readOnlyModeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/app/**", "/actuator/**", "/error", "/ws/**");
    }
```
(import `com.vanter.ember.hub.license.ReadOnlyModeInterceptor`)

`HubOrchestrator.LicenseSnapshot`:

```java
    record LicenseSnapshot(String status, Instant lastHeartbeatAt, Instant suspendedSince, Instant migratedSince) {
        public static final String OK = "OK";
        public static final String SUSPENDED = "SUSPENDED";
        public static final String MIGRATED = "MIGRATED";
        public static final String NONE = "NONE";

        public LicenseSnapshot(String status, Instant lastHeartbeatAt, Instant suspendedSince) {
            this(status, lastHeartbeatAt, suspendedSince, null);
        }
    }
```

`DefaultHubOrchestrator.toLicenseSnapshot`:

```java
    private LicenseSnapshot toLicenseSnapshot(HubState state) {
        String status = state.migratedSince() != null
                ? LicenseSnapshot.MIGRATED
                : state.suspendedSince() != null ? LicenseSnapshot.SUSPENDED : LicenseSnapshot.OK;
        return new LicenseSnapshot(status, state.lastHeartbeatAt(), state.suspendedSince(), state.migratedSince());
    }
```

`ember-hub/ui/src/lib/types.ts`:

```ts
export type LicenseStatus = 'OK' | 'SUSPENDED' | 'MIGRATED' | 'NONE';

export interface LicenseSnapshot {
  status: LicenseStatus;
  lastHeartbeatAt: string | null;
  suspendedSince: string | null;
  migratedSince?: string | null;
}
```

`LicenseCard.tsx` — extend both maps and the body:

```tsx
const STATUS_LABEL: Record<LicenseSnapshot['status'], string> = {
  OK: 'OK',
  SUSPENDED: 'Suspendida',
  MIGRATED: 'Migrada a Web (solo lectura)',
  NONE: 'Sin licencia'
};

const STATUS_VARIANT: Record<LicenseSnapshot['status'], 'success' | 'danger' | 'neutral'> = {
  OK: 'success',
  SUSPENDED: 'danger',
  MIGRATED: 'danger',
  NONE: 'neutral'
};
```
```tsx
      {license.status === 'MIGRATED' && (
        <p className="text-sm text-muted-foreground mb-3">
          Este restaurante ahora usa Ember Web. Tu Hub queda en modo consulta: puedes ver tu
          historial y descargar el Excel, pero no registrar ventas nuevas.
        </p>
      )}
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && ./mvnw test -Dtest='ReadOnlyModeInterceptorTest,DefaultHubOrchestratorTest,HubStateStoreTest,GracePeriodInterceptorTest'` and `cd ember-hub/ui && pnpm run test && pnpm run build`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src ember-hub/ui/src
git commit -m "feat(hub): read-only mode after the restaurant moves to web, shown on the license card"
```

---

### Task 9: Verification, version, report and rollout notes

**Files:**
- Modify: `backend/pom.xml` (version)
- Create: `reports/533-restaurant-deployment-mode.md`
- Modify: `PROGRESS.md`

- [ ] **Step 1: Full verification**

Run (each must be green):
- `cd backend && ./mvnw test` — expected: BUILD SUCCESS, all tests pass.
- `cd frontend && pnpm run test:run && pnpm run lint && pnpm run build` — tests pass, lint 0 errors, build clean.
- `cd ember-hub/ui && pnpm run test && pnpm run build`.

- [ ] **Step 2: Manual acceptance on the local machine (real Hub + real backend profile)**

1. Add the column to the local dev DB: `ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS deployment_mode varchar(10) NOT NULL DEFAULT 'CLOUD';`
2. Console: create one restaurant as Web and one as Hub; the list shows the badges and the filter works.
3. Log in to the web with the Hub restaurant's admin: rejected with the normal invalid-credentials message; the Web restaurant's admin logs in.
4. In the Console, switch the Hub restaurant to Web (typing the slug): the audit log shows `RESTAURANT_MODE_CHANGED`; issuing a Hub license for a Web restaurant is refused.
5. With a Hub installed for that restaurant: after the next heartbeat the license card shows "Migrada a Web (solo lectura)"; within the 48 h courtesy sales still work; to shorten the wait, set `EMBER_HUB_SUSPENDED_GRACE_HOURS` low (or edit `ember.hub.suspended-grace-hours`) in a test build: afterwards any write returns 403 `license_migrated` while history and the Excel export still load; switching the restaurant back to Hub and waiting a heartbeat resumes normal operation.

- [ ] **Step 3: Version bump**

In `backend/pom.xml` change `<version>0.2.9</version>` (the `ember` artifact) to `<version>0.3.0</version>` — a new capability plus a migration.

- [ ] **Step 4: Report and PROGRESS**

Write `reports/533-restaurant-deployment-mode.md` with the five mandatory sections (Identification: report 533, task RESTAURANT-DEPLOYMENT-MODE, predecessor 532; Objective; Modified Files; What Changed; Why It Changed) summarising this plan and the verification counts. In `PROGRESS.md`: prepend a "Last Completed Task" entry for report 533 (keep the file under 180 lines by trimming the oldest "Prev" text), add a Task Queue section `RESTAURANT-DEPLOYMENT-MODE` with the nine tasks ticked, and record the rollout rule: **deploy the cloud first** (V15 + gates), then release the Hub; the local dev DB needs the column by hand; the data importer stays out of scope (medium term); retention of the cloud data left behind is undecided.

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml reports/533-restaurant-deployment-mode.md PROGRESS.md
git commit -m "chore: bump to 0.3.0 and record the restaurant deployment mode work"
```

- [ ] **Step 6: Hand off**

Open one PR (squash) from `feat/restaurant-deployment-mode`; after the merge, tag `v0.3.0`, deploy the cloud with `./deploy/deploy.sh 0.3.0` (no trailing dot), then build and publish the Hub installer. Do not publish the Hub before the cloud is deployed.
