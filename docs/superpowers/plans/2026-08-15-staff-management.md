# Staff Management Backend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mock data behind `/admin/employees` ("Gestión de Personal") with real tenant-scoped staff data, and remove the duplicate search bar/button the page currently renders on top of the existing global `TopNav`.

**Architecture:** Staff are the existing `User` rows with `role != CUSTOMER`, extended with 7 new nullable HR columns added directly to `users` (no separate entity/module — always a strict 1:1, a second table would only add a join with no benefit). Two new endpoints (`GET`/`PATCH /admin/staff`) live in the existing `UserAdminController`. On the frontend, `MOCK_STAFF` is replaced by a `useQuery` against a new `staffService`, and a small `searchTerm` slot is added to the existing `useUIStore` so `TopNav`'s already-present (but previously unwired) search box drives the page's filtering instead of a second, duplicate input.

**Tech Stack:** Java 17 / Spring Boot 3.5.14 / JPA+Postgres (backend), React 19 / TypeScript / TanStack Query / Zustand (frontend).

**Spec:** `docs/superpowers/specs/2026-08-15-staff-management-design.md`

## Global Constraints

- No pagination on `GET /admin/staff` — filtering stays client-side (small roster per tenant, matches `ProductPerformance`/`TableAnalytics`).
- No staff-creation endpoint or UI — "+ Nuevo empleado" stays inert. No edit UI for the new PATCH endpoint either — the endpoint must exist and work, but no form calls it from the UI yet.
- No "Cleaning" department/role — filters only cover `WAITER`/`KITCHEN`/`ADMIN`.
- `active` is a manually-set flag, not real presence/session tracking.
- Every new backend route must be added to `SecurityAuditTest`'s 401 matrix.
- Every schema change goes in `db/migration/` AND the `User` entity mapping (project convention).
- `backend-types.ts` is NOT regenerated in this plan (no live backend to run `pnpm run openapi` against) — new frontend types are hand-written in `api.ts`, mirroring how `platformApi.ts` already does this.
- Frontend verification in this repo is `pnpm run build` (`tsc -b && vite build`) + `pnpm exec eslint <touched files>` — there is no established frontend unit-test convention to extend (only one pre-existing smoke test in the whole repo), so frontend tasks are NOT written TDD-style; backend tasks ARE (this codebase has a real JUnit/MockMvc testing culture — `SecurityAuditTest`, `UserAdminControllerTest`, `*TenantIsolationTest`).

---

## Task 1: Migration + `User` entity fields + tenant-scoped staff query

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__staff_profile_fields.sql`
- Modify: `backend/src/main/java/com/vanter/ember/identity/model/User.java`
- Modify: `backend/src/main/java/com/vanter/ember/identity/repository/UserRepository.java`
- Test: `backend/src/test/java/com/vanter/ember/identity/repository/UserRepositoryStaffQueryTest.java`

**Interfaces:**
- Produces: `User` gains `getActive()/setActive(Boolean)`, `getJobTitle()/setJobTitle(String)`, `getShift()/setShift(String)`, `getContractType()/setContractType(String)`, `getLocation()/setLocation(String)`, `getEfficiencyPercentage()/setEfficiencyPercentage(BigDecimal)`, `getPendingHours()/setPendingHours(BigDecimal)` (Lombok `@Data`, all already implied). `UserRepository.findByRestaurantId_IdAndRoleNot(UUID restaurantId, Role role): List<User>` — used by Task 2's service.

- [ ] **Step 1: Write the failing repository test**

Create `backend/src/test/java/com/vanter/ember/identity/repository/UserRepositoryStaffQueryTest.java`:

```java
package com.vanter.ember.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.AbstractTenantIsolationTest;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryStaffQueryTest extends AbstractTenantIsolationTest {

    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;

    @Override
    protected void deleteAll() {
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void newStaffUser_defaultsActiveTrueAndZeroPendingHours() {
        Restaurant restaurant = restaurantRepository.save(
                Restaurant.builder().name("Ember A").slug("ember-a-staff-defaults").build());

        User saved = readAs(TENANT_A, () -> userRepository.save(
                User.builder()
                        .restaurantId(restaurant)
                        .name("Waiter A")
                        .email("waiter-a-defaults@example.com")
                        .passwordHash("hash")
                        .role(Role.WAITER)
                        .build()));

        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getPendingHours()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getShift()).isNull();
        assertThat(saved.getJobTitle()).isNull();
    }

    @Test
    void findByRestaurantId_IdAndRoleNot_returnsOnlyNonCustomerStaffForThatTenant() {
        Restaurant restaurantA = restaurantRepository.save(
                Restaurant.builder().name("Ember A").slug("ember-a-staff-query").build());
        Restaurant restaurantB = restaurantRepository.save(
                Restaurant.builder().name("Ember B").slug("ember-b-staff-query").build());

        User waiterA = readAs(TENANT_A, () -> userRepository.save(User.builder()
                .restaurantId(restaurantA).name("Waiter A").email("waiter-a2@example.com")
                .passwordHash("hash").role(Role.WAITER).build()));
        readAs(TENANT_A, () -> userRepository.save(User.builder()
                .restaurantId(restaurantA).name("Customer A").email("customer-a@example.com")
                .passwordHash("hash").role(Role.CUSTOMER).build()));
        readAs(TENANT_B, () -> userRepository.save(User.builder()
                .restaurantId(restaurantB).name("Waiter B").email("waiter-b@example.com")
                .passwordHash("hash").role(Role.WAITER).build()));

        List<User> staffForA = readAs(TENANT_A, () ->
                userRepository.findByRestaurantId_IdAndRoleNot(restaurantA.getId(), Role.CUSTOMER));

        assertThat(staffForA).extracting(User::getId).containsExactly(waiterA.getId());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=UserRepositoryStaffQueryTest`
Expected: FAIL to compile — `User` has no `getActive()`/`getJobTitle()`/etc., and `UserRepository` has no `findByRestaurantId_IdAndRoleNot`.

- [ ] **Step 3: Add the migration**

Create `backend/src/main/resources/db/migration/V6__staff_profile_fields.sql`:

```sql
-- Staff Management (2026-08-15 design spec) — HR-flavored profile fields added directly to
-- `users` rather than a separate table: every WAITER/KITCHEN/ADMIN row gets exactly one of
-- these, so a second table would only add a join with no functional benefit.
--
-- `active`/`pending_hours` carry a literal DEFAULT, so Postgres backfills every pre-existing
-- row in the same statement — no separate runtime backfill job needed.

ALTER TABLE users ADD COLUMN IF NOT EXISTS active boolean NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS job_title varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS shift varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS contract_type varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS location varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS efficiency_percentage numeric(5,2);
ALTER TABLE users ADD COLUMN IF NOT EXISTS pending_hours numeric(6,2) NOT NULL DEFAULT 0;
```

- [ ] **Step 4: Add the fields to `User.java`**

In `backend/src/main/java/com/vanter/ember/identity/model/User.java`, add `import java.math.BigDecimal;` to the imports, and insert these fields between `role` and `createdAt`:

```java
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "job_title")
    private String jobTitle;

    private String shift;

    @Column(name = "contract_type")
    private String contractType;

    private String location;

    @Column(name = "efficiency_percentage")
    private BigDecimal efficiencyPercentage;

    @Column(name = "pending_hours", nullable = false)
    @Builder.Default
    private BigDecimal pendingHours = BigDecimal.ZERO;
```

`@Builder.Default` is required on `active`/`pendingHours`: every existing call site (`AuthService.register`, `PlatformRestaurantService.create`, test fixtures) builds a `User` without setting these, and Lombok's builder sends an explicit `NULL` for any field without `@Builder.Default` — which would violate the new `NOT NULL` constraint. `jobTitle`/`shift`/`contractType`/`location`/`efficiencyPercentage` stay plain (genuinely optional, `null` is a valid value).

- [ ] **Step 5: Add the repository method**

In `backend/src/main/java/com/vanter/ember/identity/repository/UserRepository.java`, add inside the interface (after `findByRestaurantId_IdAndRole`):

```java
    /** Non-CUSTOMER users for a tenant — the Staff Management roster. */
    List<User> findByRestaurantId_IdAndRoleNot(UUID restaurantId, Role role);
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=UserRepositoryStaffQueryTest`
Expected: PASS (2 tests).

- [ ] **Step 7: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS — no other test references `User`'s constructor positionally (it uses `@Builder`/`@AllArgsConstructor` only via named fields in existing tests), so the new fields shouldn't break anything else.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V6__staff_profile_fields.sql backend/src/main/java/com/vanter/ember/identity/model/User.java backend/src/main/java/com/vanter/ember/identity/repository/UserRepository.java backend/src/test/java/com/vanter/ember/identity/repository/UserRepositoryStaffQueryTest.java
git commit -m "feat(backend): add staff profile columns and tenant-scoped staff query"
```

**Note:** `spring.flyway.enabled=false` in the H2 test profile means `./mvnw test` never runs this migration SQL — it only exercises the entity/Hibernate side. Verify the migration itself only by pointing `./mvnw spring-boot:run` (or `psql`) at the real `ember-postgres-1` container and checking `flyway_schema_history` — a green test suite says nothing about the SQL file's correctness (established project gotcha, see `PROGRESS.md`).

---

## Task 2: `StaffMemberResponse`/`UpdateStaffProfileRequest` DTOs + `UserAdminService` methods

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/identity/dto/StaffMemberResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/identity/dto/UpdateStaffProfileRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java`
- Test: `backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository.findByRestaurantId_IdAndRoleNot(UUID, Role): List<User>` (Task 1), `UserRepository.findById(String): Optional<User>` (existing), `ResourceNotFoundException` (existing, `com.vanter.ember.config`).
- Produces: `UserAdminService.getStaff(UUID tenantId): List<StaffMemberResponse>` and `UserAdminService.updateProfile(String userId, UUID tenantId, UpdateStaffProfileRequest request): StaffMemberResponse` — used by Task 3's controller.

- [ ] **Step 1: Write the failing service test**

Create `backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java`:

```java
package com.vanter.ember.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.dto.UpdateStaffProfileRequest;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserAdminService userAdminService;

    private static final UUID TENANT_A = UUID.randomUUID();

    private User waiterFor(UUID tenantId) {
        return User.builder()
                .id("u-1")
                .name("Ana")
                .email("ana@test.com")
                .role(Role.WAITER)
                .passwordHash("hash")
                .restaurantId(Restaurant.builder().id(tenantId).name("Test").slug("test-" + tenantId).build())
                .build();
    }

    @Test
    void getStaff_mapsUserFieldsIntoResponse() {
        when(userRepository.findByRestaurantId_IdAndRoleNot(TENANT_A, Role.CUSTOMER))
                .thenReturn(List.of(waiterFor(TENANT_A)));

        List<com.vanter.ember.identity.dto.StaffMemberResponse> result = userAdminService.getStaff(TENANT_A);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("u-1");
        assertThat(result.get(0).role()).isEqualTo(Role.WAITER);
        assertThat(result.get(0).active()).isTrue();
    }

    @Test
    void updateProfile_appliesOnlyNonNullFields() {
        User existing = waiterFor(TENANT_A);
        existing.setShift("Mañana");
        when(userRepository.findById("u-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = userAdminService.updateProfile(
                "u-1", TENANT_A, new UpdateStaffProfileRequest(false, null, null, null, null, null, null));

        assertThat(result.active()).isFalse();
        assertThat(result.shift()).isEqualTo("Mañana");
    }

    @Test
    void updateProfile_throwsWhenUserBelongsToAnotherTenant() {
        when(userRepository.findById("u-1")).thenReturn(Optional.of(waiterFor(UUID.randomUUID())));

        assertThatThrownBy(() -> userAdminService.updateProfile(
                "u-1", TENANT_A, new UpdateStaffProfileRequest(false, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_throwsWhenUserDoesNotExist() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAdminService.updateProfile(
                "missing", TENANT_A, new UpdateStaffProfileRequest(false, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=UserAdminServiceTest`
Expected: FAIL to compile — no `StaffMemberResponse`/`UpdateStaffProfileRequest`/`getStaff`/`updateProfile` exist yet.

- [ ] **Step 3: Create `StaffMemberResponse`**

Create `backend/src/main/java/com/vanter/ember/identity/dto/StaffMemberResponse.java`:

```java
package com.vanter.ember.identity.dto;

import com.vanter.ember.identity.model.Role;
import java.math.BigDecimal;
import java.time.Instant;

public record StaffMemberResponse(
        String id,
        String name,
        String email,
        Role role,
        Instant createdAt,
        Boolean active,
        String jobTitle,
        String shift,
        String contractType,
        String location,
        BigDecimal efficiencyPercentage,
        BigDecimal pendingHours) {}
```

- [ ] **Step 4: Create `UpdateStaffProfileRequest`**

Create `backend/src/main/java/com/vanter/ember/identity/dto/UpdateStaffProfileRequest.java`:

```java
package com.vanter.ember.identity.dto;

import java.math.BigDecimal;

/** Every field optional — a PATCH only applies the ones the caller actually sent. */
public record UpdateStaffProfileRequest(
        Boolean active,
        String jobTitle,
        String shift,
        String contractType,
        String location,
        BigDecimal efficiencyPercentage,
        BigDecimal pendingHours) {}
```

- [ ] **Step 5: Add the service methods**

Replace the full contents of `backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java` with:

```java
package com.vanter.ember.identity.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.dto.StaffMemberResponse;
import com.vanter.ember.identity.dto.UpdateStaffProfileRequest;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;

    public User updateRole(String userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setRole(newRole);
        return userRepository.save(user);
    }

    public List<StaffMemberResponse> getStaff(UUID tenantId) {
        return userRepository.findByRestaurantId_IdAndRoleNot(tenantId, Role.CUSTOMER).stream()
                .map(UserAdminService::toStaffResponse)
                .toList();
    }

    public StaffMemberResponse updateProfile(
            String userId, UUID tenantId, UpdateStaffProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getRestaurantId() == null || !user.getRestaurantId().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }

        if (request.active() != null) user.setActive(request.active());
        if (request.jobTitle() != null) user.setJobTitle(request.jobTitle());
        if (request.shift() != null) user.setShift(request.shift());
        if (request.contractType() != null) user.setContractType(request.contractType());
        if (request.location() != null) user.setLocation(request.location());
        if (request.efficiencyPercentage() != null) {
            user.setEfficiencyPercentage(request.efficiencyPercentage());
        }
        if (request.pendingHours() != null) user.setPendingHours(request.pendingHours());

        return toStaffResponse(userRepository.save(user));
    }

    private static StaffMemberResponse toStaffResponse(User user) {
        return new StaffMemberResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getActive(),
                user.getJobTitle(),
                user.getShift(),
                user.getContractType(),
                user.getLocation(),
                user.getEfficiencyPercentage(),
                user.getPendingHours());
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=UserAdminServiceTest`
Expected: PASS (4 tests).

- [ ] **Step 7: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/identity/dto/StaffMemberResponse.java backend/src/main/java/com/vanter/ember/identity/dto/UpdateStaffProfileRequest.java backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java
git commit -m "feat(backend): add staff listing and profile-update service methods"
```

---

## Task 3: `GET`/`PATCH /admin/staff` endpoints + security tests

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/identity/controller/UserAdminController.java`
- Modify: `backend/src/test/java/com/vanter/ember/identity/controller/UserAdminControllerTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`

**Interfaces:**
- Consumes: `UserAdminService.getStaff(UUID): List<StaffMemberResponse>`, `UserAdminService.updateProfile(String, UUID, UpdateStaffProfileRequest): StaffMemberResponse` (Task 2), `TenantContextHolder.requireTenantId(): UUID` (existing, `com.vanter.ember.config`).
- Produces: `GET /admin/staff` and `PATCH /admin/staff/{userId}`, both `@PreAuthorize("hasRole('ADMIN')")` — consumed by Task 5's `staffService`.

- [ ] **Step 1: Write the failing controller tests**

Append to `backend/src/test/java/com/vanter/ember/identity/controller/UserAdminControllerTest.java` — add these imports alongside the existing ones:

```java
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.dto.StaffMemberResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
```

Add this field next to the other `@Autowired`/`@MockBean` fields:

```java
    private static final UUID TENANT_ID = UUID.randomUUID();
```

Add this method (mirrors `AnalyticsControllerTest`'s tenant cleanup):

```java
    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }
```

Add these test methods inside the class:

```java
    @Test
    @WithMockUser(roles = "ADMIN")
    void getStaff_returnsStaffForCurrentTenant() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userAdminService.getStaff(TENANT_ID)).thenReturn(List.of(new StaffMemberResponse(
                "u-1", "Ana", "ana@test.com", Role.WAITER, Instant.now(),
                true, "Mesera", "Mañana", "Tiempo completo", null, null, BigDecimal.ZERO)));

        mockMvc.perform(get("/admin/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("u-1"))
                .andExpect(jsonPath("$[0].role").value("WAITER"));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void getStaff_forbiddenForWaiter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/admin/staff"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStaff_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/admin/staff"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStaffProfile_updatesActiveFlag() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userAdminService.updateProfile(eq("u-1"), eq(TENANT_ID), any())).thenReturn(
                new StaffMemberResponse(
                        "u-1", "Ana", "ana@test.com", Role.WAITER, Instant.now(),
                        false, "Mesera", null, null, null, null, BigDecimal.ZERO));

        mockMvc.perform(patch("/admin/staff/u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=UserAdminControllerTest`
Expected: FAIL — `/admin/staff` routes don't exist yet (404s where 200/403/401 are expected).

- [ ] **Step 3: Add the controller endpoints**

Replace the full contents of `backend/src/main/java/com/vanter/ember/identity/controller/UserAdminController.java` with:

```java
package com.vanter.ember.identity.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.dto.StaffMemberResponse;
import com.vanter.ember.identity.dto.UpdateStaffProfileRequest;
import com.vanter.ember.identity.dto.UpdateUserRoleRequest;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "User and role management (ADMIN only)")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "Assign a role to a user (ADMIN)")
    @PatchMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public User updateRole(@PathVariable String userId,
                           @Valid @RequestBody UpdateUserRoleRequest request) {
        return userAdminService.updateRole(userId, request.role());
    }

    @Operation(summary = "List the current tenant's staff, i.e. every non-CUSTOMER user (ADMIN)")
    @GetMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public List<StaffMemberResponse> getStaff() {
        return userAdminService.getStaff(TenantContextHolder.requireTenantId());
    }

    @Operation(summary = "Update a staff member's HR profile fields (ADMIN)")
    @PatchMapping("/staff/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public StaffMemberResponse updateStaffProfile(
            @PathVariable String userId,
            @RequestBody UpdateStaffProfileRequest request) {
        return userAdminService.updateProfile(
                userId, TenantContextHolder.requireTenantId(), request);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=UserAdminControllerTest`
Expected: PASS (all tests, old and new).

- [ ] **Step 5: Add the new routes to `SecurityAuditTest`'s 401 matrix**

In `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`, add these two lines to the `@CsvSource` array, right after `"PATCH, /api/admin/users/u-1/role",`:

```java
        "GET,  /api/admin/staff",
        "PATCH, /api/admin/staff/u-1",
```

- [ ] **Step 6: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/identity/controller/UserAdminController.java backend/src/test/java/com/vanter/ember/identity/controller/UserAdminControllerTest.java backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java
git commit -m "feat(backend): expose GET/PATCH /admin/staff endpoints"
```

---

## Task 4: `TopNav` search wiring + route rename to `/admin/employees`

**Files:**
- Modify: `frontend/src/store/uiStore.ts`
- Modify: `frontend/src/components/TopNav.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/FloatingNav.tsx`

**Interfaces:**
- Produces: `useUIStore` gains `searchTerm: string` and `setSearchTerm(value: string): void` — consumed by Task 6's `Staff.tsx`. Route `/admin/employees` replaces `/admin/staff`.

- [ ] **Step 1: Add `searchTerm` to `useUIStore`**

In `frontend/src/store/uiStore.ts`, change the `UIState` interface and store to:

```ts
interface UIState {
  activeModal: ModalType
  modalPayload: any
  searchTerm: string
  openModal: (modal: ModalType, payload?: any) => void
  closeModal: () => void
  setSearchTerm: (value: string) => void
}

export const useUIStore = create<UIState>((set) => ({

    activeModal: null,
    modalPayload: null,
    searchTerm: '',


  openModal: (modal, payload = null) => set({

    activeModal: modal,
    modalPayload: payload
    
  }),

  closeModal: () => set({
    activeModal: null,
    modalPayload: null
  }),

  setSearchTerm: (value) => set({ searchTerm: value })

}));
```

- [ ] **Step 2: Wire `TopNav`'s search input to the store and reset it on navigation**

In `frontend/src/components/TopNav.tsx`, add `useEffect` to the React import at the top of the file:

```ts
import { useEffect } from 'react'
```

Change the destructuring of `useUIStore` from:

```ts
  const { openModal } = useUIStore()
```

to:

```ts
  const { openModal, searchTerm, setSearchTerm } = useUIStore()
```

Add this effect right after `const path = location.pathname`, and BEFORE the two early `return null` guards — hooks must run unconditionally on every render, so it cannot go after them:

```ts
  useEffect(() => {
    setSearchTerm('')
  }, [path, setSearchTerm])
```

The resulting order at the top of the component must be: the existing hooks (`useAuthStore`, `settingStore`, `useUIStore`, `useLocation`, the `path` assignment, `useMatch` x2, `isMenuItemsRouteId`) and this new `useEffect`, ALL before `if (!role || role === 'CUSTOMER') return null`.

Update the `<input>` element to be controlled:

```tsx
          <input
            className="peer h-full w-full outline-none
                    text-sm text-zinc-700 bg-transparent pr-2"
            type="text"
            placeholder={searchPlaceholder}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
```

- [ ] **Step 3: Rename the route in `App.tsx`**

In `frontend/src/App.tsx`, change:

```tsx
            <Route path="staff" element={<Staff />} />
```

to:

```tsx
            <Route path="employees" element={<Staff />} />
```

- [ ] **Step 4: Update the `FloatingNav` link**

In `frontend/src/components/FloatingNav.tsx`, change:

```tsx
          <Link
            to="/admin/staff"
            className={navItemClass('/admin/staff')}
            title="Personal"
          >
```

to:

```tsx
          <Link
            to="/admin/employees"
            className={navItemClass('/admin/employees')}
            title="Personal"
          >
```

- [ ] **Step 5: Verify the build**

Run: `cd frontend && pnpm run build`
Expected: PASS (0 TypeScript errors).

Run: `cd frontend && pnpm exec eslint src/store/uiStore.ts src/components/TopNav.tsx src/App.tsx src/components/FloatingNav.tsx`
Expected: PASS (0 errors/warnings).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/store/uiStore.ts frontend/src/components/TopNav.tsx frontend/src/App.tsx frontend/src/components/FloatingNav.tsx
git commit -m "feat(frontend): wire TopNav search to a shared store, rename staff route"
```

---

## Task 5: `staffService` + hand-typed staff types in `api.ts`

**Files:**
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Produces: `StaffRole`, `StaffMemberResponse`, `UpdateStaffProfileRequest` types; `staffService.getAll(): Promise<StaffMemberResponse[]>`, `staffService.updateProfile(userId: string, request: UpdateStaffProfileRequest): Promise<StaffMemberResponse>` — consumed by Task 6.

- [ ] **Step 1: Append the staff types and service**

Add this to the end of `frontend/src/lib/api.ts` (after the closing `}` of `analyticsService`):

```ts

// Hand-typed: backend-types.ts has no components['schemas'] for the /admin/staff endpoints
// yet (no live backend to regenerate against). Mirrors StaffMemberResponse/
// UpdateStaffProfileRequest (backend/src/main/java/com/vanter/ember/identity/dto).
export type StaffRole = 'WAITER' | 'KITCHEN' | 'ADMIN'

export interface StaffMemberResponse {
  id: string
  name: string
  email: string
  role: StaffRole
  createdAt: string
  active: boolean
  jobTitle: string | null
  shift: string | null
  contractType: string | null
  location: string | null
  efficiencyPercentage: number | null
  pendingHours: number
}

export interface UpdateStaffProfileRequest {
  active?: boolean
  jobTitle?: string
  shift?: string
  contractType?: string
  location?: string
  efficiencyPercentage?: number
  pendingHours?: number
}

export const staffService = {
  getAll: async (): Promise<StaffMemberResponse[]> => {
    const { data } = await api.get<StaffMemberResponse[]>('/admin/staff')
    return data
  },
  updateProfile: async (
    userId: string,
    request: UpdateStaffProfileRequest
  ): Promise<StaffMemberResponse> => {
    const { data } = await api.patch<StaffMemberResponse>(`/admin/staff/${userId}`, request)
    return data
  },
}
```

- [ ] **Step 2: Verify the build**

Run: `cd frontend && pnpm run build`
Expected: PASS.

Run: `cd frontend && pnpm exec eslint src/lib/api.ts`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(frontend): add staffService for the /admin/staff endpoints"
```

---

## Task 6: Rewire the whole `pages/admin/staff/` tree to the real `StaffMemberResponse` shape

This is one task, not split further: `types.ts` dropping `StaffMember` and `Staff.tsx`/`mock-data.ts` still depending on it are not independently completable — splitting them would leave an intermediate commit with a broken build.

**Files:**
- Modify: `frontend/src/pages/admin/staff/types.ts`
- Modify: `frontend/src/pages/admin/staff/components/StaffCard.tsx`
- Modify: `frontend/src/pages/admin/staff/components/StaffGrid.tsx`
- Modify: `frontend/src/pages/admin/staff/components/StaffKpis.tsx`
- Modify: `frontend/src/pages/admin/staff/components/StaffHeader.tsx`
- Modify: `frontend/src/pages/admin/staff/Staff.tsx`
- Delete: `frontend/src/pages/admin/staff/mock-data.ts`

**Interfaces:**
- Consumes: `StaffMemberResponse`, `StaffRole` from `@/lib/api` (Task 5), `staffService.getAll` (Task 5), `useUIStore().searchTerm` (Task 4).
- Produces: `StaffFilter`, `STAFF_FILTERS`, `ROLE_LABELS`, `ROLE_BADGE_CLASSNAMES` (`types.ts`) — consumed by `StaffFilters.tsx`, which needs no code changes since it only reads `STAFF_FILTERS`/`StaffFilter` by name.

- [ ] **Step 1: Rewrite `types.ts`**

Replace the full contents of `frontend/src/pages/admin/staff/types.ts` with:

```ts
import type { StaffRole } from '@/lib/api'

export type StaffFilter = 'ALL' | StaffRole

export const STAFF_FILTERS: { value: StaffFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'KITCHEN', label: 'Cocina' },
  { value: 'WAITER', label: 'Comedor' },
  { value: 'ADMIN', label: 'Administración' },
]

export const ROLE_LABELS: Record<StaffRole, string> = {
  KITCHEN: 'Cocina',
  WAITER: 'Comedor',
  ADMIN: 'Administración',
}

export const ROLE_BADGE_CLASSNAMES: Record<StaffRole, string> = {
  KITCHEN: 'bg-orange-100 text-orange-700',
  WAITER: 'bg-blue-100 text-blue-700',
  ADMIN: 'bg-violet-100 text-violet-700',
}
```

- [ ] **Step 2: Rewrite `StaffCard.tsx`**

Replace the full contents of `frontend/src/pages/admin/staff/components/StaffCard.tsx` with:

```tsx
import { MoreHorizontal, Plus } from 'lucide-react'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import type { StaffMemberResponse } from '@/lib/api'
import { ROLE_BADGE_CLASSNAMES, ROLE_LABELS } from '../types'

const getInitials = (name: string) =>
  name
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

interface StaffCardProps {
  member: StaffMemberResponse
  onViewProfile?: (member: StaffMemberResponse) => void
  onOpenActions?: (member: StaffMemberResponse) => void
}

export const StaffCard = ({ member, onViewProfile, onOpenActions }: StaffCardProps) => {
  const metadata = [
    member.shift ? { label: 'Turno', value: member.shift } : null,
    member.contractType ? { label: 'Contrato', value: member.contractType } : null,
    member.location ? { label: 'Ubicación', value: member.location } : null,
    member.efficiencyPercentage != null
      ? { label: 'Eficiencia', value: `${member.efficiencyPercentage}%` }
      : null,
  ].filter((item): item is { label: string; value: string } => item !== null)

  return (
    <Card className="border border-border/40 bg-background py-6 shadow-sm">
      <CardContent className="flex flex-col gap-4">
        <div className="flex items-start justify-between gap-2">
          <div className="relative">
            <Avatar className="h-12 w-12">
              <AvatarFallback>{getInitials(member.name)}</AvatarFallback>
            </Avatar>
            <span
              className={cn(
                'absolute -bottom-0.5 -right-0.5 h-3.5 w-3.5 rounded-full border-2 border-background',
                member.active ? 'bg-emerald-500' : 'bg-zinc-300'
              )}
              title={member.active ? 'Activo' : 'Inactivo'}
            />
          </div>
          <Badge className={cn('border-transparent', ROLE_BADGE_CLASSNAMES[member.role])}>
            {ROLE_LABELS[member.role]}
          </Badge>
        </div>

        <div className="flex flex-col gap-0.5">
          <p className="text-base font-semibold text-foreground">{member.name}</p>
          <p className="text-sm text-muted-foreground">{member.jobTitle || member.email}</p>
        </div>

        {metadata.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {metadata.map((item) => (
              <span
                key={item.label}
                className="rounded-full bg-muted px-3 py-1 text-xs text-muted-foreground"
              >
                {item.label}: <span className="font-medium text-foreground">{item.value}</span>
              </span>
            ))}
          </div>
        )}

        <div className="flex items-center gap-2 pt-1">
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="flex-1"
            onClick={() => onViewProfile?.(member)}
          >
            Perfil
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            onClick={() => onOpenActions?.(member)}
            aria-label="Más opciones"
          >
            <MoreHorizontal className="h-4 w-4" />
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

interface AddStaffCardProps {
  onClick?: () => void
}

export const AddStaffCard = ({ onClick }: AddStaffCardProps) => {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-[220px] flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-border bg-transparent text-muted-foreground transition-colors hover:border-primary/40 hover:text-primary"
    >
      <Plus className="h-6 w-6" />
      <span className="text-sm font-medium">Agregar nuevo rol</span>
    </button>
  )
}
```

- [ ] **Step 3: Update `StaffGrid.tsx`'s import**

In `frontend/src/pages/admin/staff/components/StaffGrid.tsx`, change:

```tsx
import { AddStaffCard, StaffCard } from './StaffCard'
import type { StaffMember } from '../types'

interface StaffGridProps {
  members: StaffMember[]
  onAddRole?: () => void
  onViewProfile?: (member: StaffMember) => void
  onOpenActions?: (member: StaffMember) => void
}
```

to:

```tsx
import { AddStaffCard, StaffCard } from './StaffCard'
import type { StaffMemberResponse } from '@/lib/api'

interface StaffGridProps {
  members: StaffMemberResponse[]
  onAddRole?: () => void
  onViewProfile?: (member: StaffMemberResponse) => void
  onOpenActions?: (member: StaffMemberResponse) => void
}
```

(The rest of the file — the empty state and the `.map` — is unchanged.)

- [ ] **Step 4: Update `StaffKpis.tsx`**

Replace the full contents of `frontend/src/pages/admin/staff/components/StaffKpis.tsx` with:

```tsx
import { Clock, UserCheck, Users } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { StaffMemberResponse } from '@/lib/api'

interface StaffKpisProps {
  members: StaffMemberResponse[]
}

export const StaffKpis = ({ members }: StaffKpisProps) => {
  const totalStaff = members.length
  const activeNow = members.filter((member) => member.active).length
  const pendingHours = members.reduce((sum, member) => sum + member.pendingHours, 0)

  const cards = [
    { label: 'Personal total', value: totalStaff, icon: Users },
    { label: 'Activos ahora', value: activeNow, icon: UserCheck },
    { label: 'Horas pendientes', value: `${pendingHours}h`, icon: Clock },
  ]

  return (
    <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
      {cards.map(({ label, value, icon: Icon }) => (
        <Card key={label} className="border border-border/40 bg-background py-6 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {label}
            </CardTitle>
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10">
              <Icon className="h-4 w-4 text-primary" strokeWidth={2} />
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold tracking-tight tabular-nums text-primary">
              {value}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
```

(Only the import and prop type changed from before — the JSX/logic is identical.)

- [ ] **Step 5: Strip the header down to title/subtitle**

Replace the full contents of `frontend/src/pages/admin/staff/components/StaffHeader.tsx` with:

```tsx
export const StaffHeader = () => {
  return (
    <div className="flex flex-col gap-1">
      <h1 className="text-3xl font-bold tracking-tight text-foreground">
        Gestión de Personal
      </h1>
      <p className="text-sm text-muted-foreground">
        Control administrativo y roles del equipo.
      </p>
    </div>
  )
}
```

- [ ] **Step 6: Rewrite `Staff.tsx`**

Replace the full contents of `frontend/src/pages/admin/staff/Staff.tsx` with:

```tsx
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { staffService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { StaffFilters } from './components/StaffFilters'
import { StaffGrid } from './components/StaffGrid'
import { StaffHeader } from './components/StaffHeader'
import { StaffKpis } from './components/StaffKpis'
import type { StaffFilter } from './types'

export const Staff = () => {
  const [department, setDepartment] = useState<StaffFilter>('ALL')
  const searchTerm = useUIStore((state) => state.searchTerm)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['staff'],
    queryFn: staffService.getAll,
  })

  const staff = data ?? []

  const filteredStaff = useMemo(() => {
    const query = searchTerm.trim().toLowerCase()
    return staff.filter((member) => {
      const matchesDepartment = department === 'ALL' || member.role === department
      const matchesSearch = query === '' || member.name.toLowerCase().includes(query)
      return matchesDepartment && matchesSearch
    })
  }, [staff, searchTerm, department])

  return (
    <div className="flex flex-col gap-8">
      <StaffHeader />
      <StaffFilters active={department} onChange={setDepartment} />
      {isLoading && (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
          Cargando personal...
        </div>
      )}
      {isError && (
        <div className="flex items-center justify-center py-16 text-sm text-destructive">
          Error al cargar el personal.
        </div>
      )}
      {!isLoading && !isError && (
        <>
          <StaffGrid members={filteredStaff} />
          <StaffKpis members={staff} />
        </>
      )}
    </div>
  )
}
```

- [ ] **Step 7: Delete the mock data file**

```bash
rm frontend/src/pages/admin/staff/mock-data.ts
```

- [ ] **Step 8: Verify the build**

Run: `cd frontend && pnpm run build`
Expected: PASS (0 TypeScript errors) — this is the first point since Step 1 where the tree compiles clean again.

Run: `cd frontend && pnpm exec eslint src/pages/admin/staff/types.ts src/pages/admin/staff/components/StaffCard.tsx src/pages/admin/staff/components/StaffGrid.tsx src/pages/admin/staff/components/StaffKpis.tsx src/pages/admin/staff/components/StaffHeader.tsx src/pages/admin/staff/Staff.tsx`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/pages/admin/staff/types.ts frontend/src/pages/admin/staff/components/StaffCard.tsx frontend/src/pages/admin/staff/components/StaffGrid.tsx frontend/src/pages/admin/staff/components/StaffKpis.tsx frontend/src/pages/admin/staff/components/StaffHeader.tsx frontend/src/pages/admin/staff/Staff.tsx
git rm frontend/src/pages/admin/staff/mock-data.ts
git commit -m "feat(frontend): wire staff management page to staffService, remove the mock"
```

- [ ] **Step 10: Manual smoke test (if a backend + Postgres are reachable)**

Run `cd backend && ./mvnw spring-boot:run`, confirm `V6__staff_profile_fields.sql` applied (check `flyway_schema_history` or just that the app boots clean), then `cd frontend && pnpm run dev` and open `/admin/employees` as an ADMIN user: the existing waiter should appear in the grid, the department pills should filter it, and the search box living in `TopNav` (not a second one) should filter by name. If no backend/Postgres is reachable in this environment, note that this step was skipped and only build+lint were verified.

---

## Self-Review Notes

- **Spec coverage:** every "In Scope" bullet from the spec maps to a task — schema (Task 1), endpoints (Tasks 2-3), TopNav dedupe + route rename (Task 4), frontend data wiring (Tasks 5-6). Every "Out of Scope" item (staff creation, edit UI, Cleaning department, real presence, `backend-types.ts` regen) has no corresponding task, as intended.
- **Type consistency:** `StaffMemberResponse` field order (`id, name, email, role, createdAt, active, jobTitle, shift, contractType, location, efficiencyPercentage, pendingHours`) is identical across the Java record (Task 2), every test's constructor call (Tasks 2-3), and the TypeScript interface (Task 5) — checked field-by-field.
- **`updateProfile`'s tenant check** is new behavior `updateRole` doesn't have; documented in the spec as a deliberate improvement scoped to the new endpoint only, not a fix to `updateRole` (out of scope for this plan).
