# Guest table-join — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let a walk-in diner join a table with one tap and no account — a `guest` `User` that
behaves like a normal customer for the session but earns no loyalty points and records no visits.

**Architecture:** New public `POST /sessions/join-as-guest` mints a guest `User` (gated on a
valid open table) then delegates to the existing `joinSessionCode` / `joinSession` internals.
A `guest` flag on `User` and on the `ParticipantJoined` event lets the two loyalty listeners
skip guests.

**Tech Stack:** Java 17 / Spring Boot 3.5.14 / Spring Data JPA / Flyway; React 19 / TS / Vite.

**Spec:** `docs/superpowers/specs/2026-09-06-guest-join-design.md`

## Global Constraints

- Cloud build only. The Ember Hub build (separate effort) strips the customer flow entirely.
- Backend build/test: `cd backend && ./mvnw test` (never `mvn`, never bare `tsc`). Frontend:
  `cd frontend && pnpm run build` / `pnpm run lint` / `pnpm run test:run`.
- Any new `@DataJpaTest` must `@Import(com.vanter.ember.config.TenantIdentifierResolver.class)`.
- Prod Flyway is **not** baselined — `V9` runs on deploy. The local dev DB *is* baselined at 15,
  so `V9` is skipped there: run its SQL by hand for local dev (a step in Task 2, not a test).
- Final commit: one squashed atomic commit; report at `ember/reports/NN-guest-join.md`;
  `PROGRESS.md` updated. **No `Co-Authored-By`, no `Claude-Session`, no "Generated with Claude
  Code" line anywhere — not in the commit message, not in the PR body.**
- Scoped staging only (`git add <paths>`), never `git add -A`.

---

## File Structure

**New — backend**
- `backend/src/main/java/com/vanter/ember/identity/service/GuestNameGenerator.java` — random "Animal Adjetivo" display names
- `backend/src/main/java/com/vanter/ember/identity/service/GuestUserService.java` — persist a guest `User`
- `backend/src/main/java/com/vanter/ember/session/dto/JoinAsGuestRequest.java` — request body
- `backend/src/main/resources/db/migration/V9__user_guest_flag.sql`
- `backend/src/test/java/com/vanter/ember/identity/service/GuestNameGeneratorTest.java`
- `backend/src/test/java/com/vanter/ember/identity/service/GuestUserServiceTest.java`
- `backend/src/test/java/com/vanter/ember/session/controller/GuestJoinFlowIntegrationTest.java`

**Modified — backend**
- `backend/src/main/java/com/vanter/ember/identity/model/User.java` — `guest` field
- `backend/src/main/java/com/vanter/ember/session/event/ParticipantJoined.java` — `guest` component
- `backend/src/main/java/com/vanter/ember/session/service/SessionService.java` — pass `guest` when publishing `ParticipantJoined`
- `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListener.java` — skip guests
- `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListener.java` — skip null-user / guest participants
- `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java` — `joinAsGuest` endpoint + `withRescopedToken(Session, String)` overload
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java` — `permitAll` the new endpoint
- `backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java` — add `/sessions/join-as-guest` to the default `paths`
- `backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListenerTest.java`
- `backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListenerTest.java`

**Modified — frontend**
- `frontend/src/lib/api.ts` — `SessionTableService.joinAsGuest`
- `frontend/src/pages/customer/MenuJoin.tsx` — sign-in vs guest choice when unauthenticated
- `frontend/src/pages/customer/components/JoinTableModal.tsx` — "Entrar como invitado" on the code screen
- `frontend/src/locales/es/customer.ts`, `frontend/src/locales/en/customer.ts`
- `frontend/src/pages/customer/MenuJoin.test.tsx`

---

## Task 1: `GuestNameGenerator`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/identity/service/GuestNameGenerator.java`
- Test: `backend/src/test/java/com/vanter/ember/identity/service/GuestNameGeneratorTest.java`

**Interfaces:**
- Produces: `String next()` — a two-word name like `"Puma Veloz"`, always non-blank, ≤ 30 chars.

- [ ] **Step 1: Write the failing test**

`GuestNameGeneratorTest.java`:
```java
package com.vanter.ember.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;

class GuestNameGeneratorTest {

    private final GuestNameGenerator generator = new GuestNameGenerator();

    @RepeatedTest(50)
    void next_returnsTwoNonBlankWordsWithinLength() {
        String name = generator.next();
        assertThat(name).isNotBlank();
        assertThat(name.length()).isLessThanOrEqualTo(30);
        assertThat(name.split(" ")).hasSize(2);
        assertThat(name).matches("[A-Za-zÁÉÍÓÚÑáéíóúñ]+ [A-Za-zÁÉÍÓÚÑáéíóúñ]+");
    }
}
```

- [ ] **Step 2: Run it — expect a compile failure (class missing)**

Run: `cd backend && ./mvnw test -Dtest=GuestNameGeneratorTest`

- [ ] **Step 3: Implement**

`GuestNameGenerator.java`:
```java
package com.vanter.ember.identity.service;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** Friendly "Animal Adjetivo" display name for a guest diner. */
@Component
public class GuestNameGenerator {

    private static final String[] ANIMALS = {
            "Puma", "Zorro", "Búho", "Lince", "Halcón", "Nutria", "Tejón", "Colibrí",
            "Jaguar", "Garza", "Erizo", "Alce", "Tucán", "Foca", "Ardilla", "Mapache"
    };

    private static final String[] ADJECTIVES = {
            "Veloz", "Sereno", "Curioso", "Astuto", "Sagaz", "Tranquilo", "Alegre", "Ágil",
            "Noble", "Valiente", "Sutil", "Radiante", "Amable", "Osado", "Gentil", "Vivaz"
    };

    public String next() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return ANIMALS[r.nextInt(ANIMALS.length)] + " " + ADJECTIVES[r.nextInt(ADJECTIVES.length)];
    }
}
```
(Longest combo `"Colibrí Radiante"` = 16 chars — well under 30.)

- [ ] **Step 4: Run the test — expect PASS**

Run: `cd backend && ./mvnw test -Dtest=GuestNameGeneratorTest`

- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/vanter/ember/identity/service/GuestNameGenerator.java \
        backend/src/test/java/com/vanter/ember/identity/service/GuestNameGeneratorTest.java
git commit -m "feat(identity): GuestNameGenerator for guest diner display names"
```

---

## Task 2: `users.guest` column + `User.guest` + `GuestUserService`

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__user_guest_flag.sql`
- Modify: `backend/src/main/java/com/vanter/ember/identity/model/User.java`
- Create: `backend/src/main/java/com/vanter/ember/identity/service/GuestUserService.java`
- Test: `backend/src/test/java/com/vanter/ember/identity/service/GuestUserServiceTest.java`

**Interfaces:**
- Consumes: `GuestNameGenerator.next()` (Task 1), `UserRepository`, `PasswordEncoder`.
- Produces: `User GuestUserService.createGuest(String requestedName)` — persisted `User` with
  `role = CUSTOMER`, `guest = true`, `active = true`, `restaurantId = null`, a unique synthetic
  `email`, an unusable `passwordHash`, and `name` = `requestedName` (trimmed) if non-blank else
  a generated one.

- [ ] **Step 1: Write the failing test**

`GuestUserServiceTest.java`:
```java
package com.vanter.ember.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class GuestUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock GuestNameGenerator nameGenerator;
    @InjectMocks GuestUserService service;

    @Test
    void createGuest_withBlankName_usesGeneratedName_andFlagsGuest() {
        when(nameGenerator.next()).thenReturn("Puma Veloz");
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User guest = service.createGuest("   ");

        assertThat(guest.getName()).isEqualTo("Puma Veloz");
        assertThat(guest.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(guest.getGuest()).isTrue();
        assertThat(guest.getActive()).isTrue();
        assertThat(guest.getRestaurantId()).isNull();
        assertThat(guest.getEmail()).startsWith("guest+").endsWith("@guests.ember.local");
        assertThat(guest.getPasswordHash()).isEqualTo("$2a$hash");
    }

    @Test
    void createGuest_withProvidedName_trimsAndUsesIt() {
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User guest = service.createGuest("  Ana  ");

        assertThat(guest.getName()).isEqualTo("Ana");
    }
}
```

- [ ] **Step 2: Run it — expect compile failure**

Run: `cd backend && ./mvnw test -Dtest=GuestUserServiceTest`

- [ ] **Step 3: Migration + entity + service**

`V9__user_guest_flag.sql`:
```sql
-- Walk-in diners can join a table without an account (see the guest-join spec). A guest is a
-- real users row flagged here; it never earns loyalty points or visits.
ALTER TABLE users ADD COLUMN guest boolean NOT NULL DEFAULT false;
```

`User.java` — add next to `active`:
```java
    /** True for a throwaway walk-in identity created by POST /sessions/join-as-guest. Guests
     *  behave like a CUSTOMER for the session but never get a loyalty account or visits. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean guest = false;
```

`GuestUserService.java`:
```java
package com.vanter.ember.identity.service;

import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GuestNameGenerator nameGenerator;

    /**
     * Create and persist a throwaway CUSTOMER identity for a walk-in diner. The email is
     * synthetic and unique; the password is a random value nobody knows, so POST /auth/login can
     * never authenticate a guest — they are resumed only via their stored JWT.
     */
    @Transactional
    public User createGuest(String requestedName) {
        String name = requestedName != null && !requestedName.isBlank()
                ? requestedName.trim()
                : nameGenerator.next();

        User guest = User.builder()
                .name(name)
                .email("guest+" + UUID.randomUUID() + "@guests.ember.local")
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.CUSTOMER)
                .guest(true)
                .build();

        return userRepository.save(guest);
    }
}
```

- [ ] **Step 4: Run the test — expect PASS**

Run: `cd backend && ./mvnw test -Dtest=GuestUserServiceTest`

- [ ] **Step 5: Local dev DB — add the column by hand (baseline skips V9)**

Run against the local dev Postgres:
```sql
ALTER TABLE users ADD COLUMN guest boolean NOT NULL DEFAULT false;
```
Then `cd backend && ./mvnw test` — the full suite must still be green (`ddl-auto=validate` /
`@DataJpaTest` fresh-schema paths see the entity field; the running dev DB now matches).

- [ ] **Step 6: Commit**
```bash
git add backend/src/main/resources/db/migration/V9__user_guest_flag.sql \
        backend/src/main/java/com/vanter/ember/identity/model/User.java \
        backend/src/main/java/com/vanter/ember/identity/service/GuestUserService.java \
        backend/src/test/java/com/vanter/ember/identity/service/GuestUserServiceTest.java
git commit -m "feat(identity): users.guest column + GuestUserService"
```

---

## Task 3: `ParticipantJoined.guest` + loyalty guards

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/session/event/ParticipantJoined.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/service/SessionService.java`
- Modify: `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListener.java`
- Modify: `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListener.java`
- Test: `backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListenerTest.java`
- Test: `backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListenerTest.java`

**Interfaces:**
- Consumes: `User.getGuest()` (Task 2).
- Produces: `ParticipantJoined` carries `boolean guest`; loyalty is never accrued for a guest or
  for a participant with a null `userId`.

- [ ] **Step 1: Write the failing tests**

In `LoyaltyAccountJoinListenerTest.java` add:
```java
    @Test
    void handleParticipantJoined_guest_doesNotCreateAccount() {
        listener.handleParticipantJoined(
                new ParticipantJoined("PARTICIPANT_JOINED", tenantId, "sess-1", "user-1", "Puma Veloz", true));
        verifyNoInteractions(loyaltyAccountService);
    }
```
(Add `import static org.mockito.Mockito.verifyNoInteractions;` if absent.)

In `LoyaltyAccrualListenerTest.java` add a test: a bill whose only participant is a guest
(`billSplitRepository.findByBillId` returns one split; `session.getParticipants()` has a
matching participant whose `userId` resolves to a `User` with `guest == true` via a stubbed
`UserRepository`) → `loyaltyAccountService.credit` is never called. Mirror the existing
`handlePaymentCompleted_multiParticipant_...` test's setup; add a `@Mock UserRepository
userRepository` to the test and stub `findById`.

- [ ] **Step 2: Run them — expect FAIL (guest still credited) / compile error on the 6-arg ctor**

Run: `cd backend && ./mvnw test -Dtest=LoyaltyAccountJoinListenerTest,LoyaltyAccrualListenerTest`

- [ ] **Step 3: Implement**

`ParticipantJoined.java` — make the canonical record carry `guest`, keep the old 4-arg
convenience ctor (defaults `guest = false`, so `SessionWebSocketListenerTest` /
`WaiterWebSocketListenerTest` keep compiling) and add a 5-arg one:
```java
package com.vanter.ember.session.event;

import java.util.UUID;

public record ParticipantJoined(
        String type, UUID tenantId, String sessionId, String userId, String userName, boolean guest) {

    public ParticipantJoined(UUID tenantId, String sessionId, String userId, String userName) {
        this("PARTICIPANT_JOINED", tenantId, sessionId, userId, userName, false);
    }

    public ParticipantJoined(UUID tenantId, String sessionId, String userId, String userName, boolean guest) {
        this("PARTICIPANT_JOINED", tenantId, sessionId, userId, userName, guest);
    }
}
```

`SessionService.java` — both publish sites (`joinSession` ~line 185, `joinSessionCode` ~line 220)
already hold the resolved `user`; pass the flag:
```java
        eventPublisher.publishEvent(new ParticipantJoined(
                saved.getTenantId(), saved.getId(), user.getId(), userName,
                Boolean.TRUE.equals(user.getGuest())));
```
(and the equivalent with `user.getName()` in `joinSessionCode`.)

`LoyaltyAccountJoinListener.java`:
```java
    @EventListener
    public void handleParticipantJoined(ParticipantJoined event) {
        if (event.guest()) {
            return;
        }
        loyaltyAccountService.findOrCreate(event.tenantId(), event.userId());
    }
```

`LoyaltyAccrualListener.java` — inject `UserRepository`, skip null / guest:
```java
    private final UserRepository userRepository;
    ...
        for (BillSplit split : billSplitRepository.findByBillId(event.billId())) {
            session.getParticipants().stream()
                    .filter(p -> p.getName().equals(split.getParticipantName()))
                    .findFirst()
                    .filter(p -> p.getUserId() != null && !isGuest(p.getUserId()))
                    .ifPresent(p -> accrue(tenantId, p.getUserId(), split, settings, event.billId()));
        }
    }

    private boolean isGuest(String userId) {
        return userRepository.findById(userId)
                .map(u -> Boolean.TRUE.equals(u.getGuest()))
                .orElse(false);
    }
```

- [ ] **Step 4: Fix compile fallout, run targeted tests**

Only `SessionService` and the four listed test files reference `ParticipantJoined`; the 4-arg
ctor covers the three WS listener tests unchanged. Run:
`cd backend && ./mvnw test -Dtest=LoyaltyAccountJoinListenerTest,LoyaltyAccrualListenerTest,SessionWebSocketListenerTest,WaiterWebSocketListenerTest`

- [ ] **Step 5: Full suite**

Run: `cd backend && ./mvnw test` — expect green.

- [ ] **Step 6: Commit**
```bash
git add backend/src/main/java/com/vanter/ember/session/event/ParticipantJoined.java \
        backend/src/main/java/com/vanter/ember/session/service/SessionService.java \
        backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListener.java \
        backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListener.java \
        backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListenerTest.java \
        backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListenerTest.java
git commit -m "feat(loyalty): skip guests and account-less participants for accrual"
```

---

## Task 4: `POST /sessions/join-as-guest`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/session/dto/JoinAsGuestRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java`
- Test: `backend/src/test/java/com/vanter/ember/session/controller/GuestJoinFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `GuestUserService.createGuest` (Task 2); `SessionService.joinSessionCode` /
  `joinSession`; `AuthService.issueTenantScopedToken(String email, UUID restaurantId)`.
- Produces: `POST /sessions/join-as-guest` → `JoinSessionResponse { Session session, String token }`.

- [ ] **Step 1: Write the failing integration test**

`GuestJoinFlowIntegrationTest.java` — `@SpringBootTest @AutoConfigureMockMvc`, `ember.ratelimit.enabled=false`:
```java
package com.vanter.ember.session.controller;

// imports: SpringBootTest, AutoConfigureMockMvc, MockMvc, TestPropertySource, ObjectMapper,
// UserRepository, DiningTableRepository / a helper that opens a session + returns its joinCode,
// static MockMvcRequestBuilders / MockMvcResultMatchers.

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ember.ratelimit.enabled=false")
class GuestJoinFlowIntegrationTest {

    // @Autowired MockMvc mockMvc; ObjectMapper objectMapper; UserRepository userRepository;
    // plus whatever seeds a tenant + an OPEN session with a known joinCode (reuse the pattern
    // from the existing session integration tests, e.g. E2EOrderFlowTest / SessionControllerTest
    // helpers).

    @Test
    void joinAsGuest_byCode_createsGuestUser_andJoins() throws Exception {
        String joinCode = openSessionAndGetCode();      // helper
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/sessions/join-as-guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"" + joinCode + "\",\"name\":\"Ana\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.session.participants[?(@.name=='Ana')]").exists());

        assertThat(userRepository.count()).isEqualTo(usersBefore + 1);
        assertThat(userRepository.findAll())
                .anySatisfy(u -> { assertThat(u.getGuest()).isTrue(); assertThat(u.getName()).isEqualTo("Ana"); });
    }

    @Test
    void joinAsGuest_blankName_getsGeneratedName() throws Exception {
        String joinCode = openSessionAndGetCode();
        mockMvc.perform(post("/sessions/join-as-guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"" + joinCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.participants[0].name").isNotEmpty());
    }

    @Test
    void joinAsGuest_badCode_returns4xx_andCreatesNoUser() throws Exception {
        long usersBefore = userRepository.count();
        mockMvc.perform(post("/sessions/join-as-guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"00000\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @Test
    void joinAsGuest_neitherIdentifier_returns400() throws Exception {
        mockMvc.perform(post("/sessions/join-as-guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
```
> If seeding a QR token in an integration test is cheap (reuse `QrTokenService`), add a
> `joinAsGuest_byQrToken_joins` case; otherwise the code path + the `SessionServiceTest`
> coverage of `joinSession` is sufficient and the QR branch is one `if`.

- [ ] **Step 2: Run it — expect FAIL (404 — endpoint missing)**

Run: `cd backend && ./mvnw test -Dtest=GuestJoinFlowIntegrationTest`

- [ ] **Step 3: Implement**

`JoinAsGuestRequest.java`:
```java
package com.vanter.ember.session.dto;

import jakarta.validation.constraints.Size;

public record JoinAsGuestRequest(String joinCode, String qrToken, @Size(max = 50) String name) {

    public boolean hasExactlyOneIdentifier() {
        boolean code = joinCode != null && !joinCode.isBlank();
        boolean qr = qrToken != null && !qrToken.isBlank();
        return code ^ qr;
    }
}
```

`SessionController.java` — add (uses `GuestUserService guestUserService` as a new
constructor-injected field):
```java
    @Operation(summary = "Join a table with no account — creates a throwaway guest identity")
    @PostMapping("/join-as-guest")
    public JoinSessionResponse joinAsGuest(@Valid @RequestBody JoinAsGuestRequest request) {
        if (!request.hasExactlyOneIdentifier()) {
            throw new IllegalArgumentException("Provide exactly one of joinCode or qrToken");
        }
        User guest = guestUserService.createGuest(request.name());
        Session session = request.qrToken() != null && !request.qrToken().isBlank()
                ? sessionService.joinSession(request.qrToken(), guest.getEmail(), guest.getName())
                : sessionService.joinSessionCode(request.joinCode(), guest.getEmail());
        return withRescopedToken(session, guest.getEmail());
    }
```
Add the overload + make the existing method delegate:
```java
    private JoinSessionResponse withRescopedToken(Session session, Authentication authentication) {
        return withRescopedToken(session, authentication.getName());
    }

    private JoinSessionResponse withRescopedToken(Session session, String email) {
        String token = authService.issueTenantScopedToken(email, session.getTenantId()).getToken();
        return new JoinSessionResponse(session, token);
    }
```
Make the whole method transactional so a failed join rolls the guest `User` back — add
`@org.springframework.transaction.annotation.Transactional` to `joinAsGuest` (or the class if
that matches the codebase style; check first). `IllegalArgumentException` → 400 via
`GlobalExceptionHandler`; a bad code / expired QR / full table throw their existing statuses.

`SecurityConfig.java` — add beside the other `/sessions` public entries:
```java
                        .requestMatchers(HttpMethod.POST, "/sessions/join-as-guest").permitAll()
```

`RateLimitProperties.java` — add `"/sessions/join-as-guest"` to the default `paths` list, with a
one-line comment that it mints a `users` row per call so it needs the same throttle as
`/sessions/join`.

- [ ] **Step 4: Run the integration test — expect PASS**

Run: `cd backend && ./mvnw test -Dtest=GuestJoinFlowIntegrationTest`

- [ ] **Step 5: Full suite**

Run: `cd backend && ./mvnw test` — expect green (note the new baseline count).

- [ ] **Step 6: Commit**
```bash
git add backend/src/main/java/com/vanter/ember/session/dto/JoinAsGuestRequest.java \
        backend/src/main/java/com/vanter/ember/session/controller/SessionController.java \
        backend/src/main/java/com/vanter/ember/config/SecurityConfig.java \
        backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java \
        backend/src/test/java/com/vanter/ember/session/controller/GuestJoinFlowIntegrationTest.java
git commit -m "feat(session): POST /sessions/join-as-guest"
```

---

## Task 5: Frontend — guest CTA on `/menu/join` and the join modal

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/pages/customer/MenuJoin.tsx`
- Modify: `frontend/src/pages/customer/components/JoinTableModal.tsx`
- Modify: `frontend/src/locales/es/customer.ts`, `frontend/src/locales/en/customer.ts`
- Test: `frontend/src/pages/customer/MenuJoin.test.tsx`

**Interfaces:**
- Consumes: `POST /sessions/join-as-guest` (Task 4).
- Produces: `SessionTableService.joinAsGuest({ joinCode?, qrToken?, name? }) => Promise<joinSessionResponse>`.

- [ ] **Step 1: Write the failing test**

In `MenuJoin.test.tsx` add:
```jsx
  test('unauthenticated: offers guest entry that joins via the QR token', async () => {
    const spy = vi
      .spyOn(SessionTableService, 'joinAsGuest')
      .mockResolvedValue({ session: { id: 'sess-42' }, token: 'scoped' } as never)

    renderAt(`/menu/join?token=${QR_TOKEN}`)          // authStore is unauthenticated in beforeEach
    await userEvent.click(screen.getByRole('button', { name: /invitado/i }))
    await userEvent.click(screen.getByRole('button', { name: /^Entrar$/ }))

    await waitFor(() => expect(spy).toHaveBeenCalledWith({ qrToken: QR_TOKEN, name: undefined }))
    expect(screen.getByText('MENU PAGE')).toBeInTheDocument()
  })
```
Adjust the existing "redirects to login when not authenticated" test: it should now assert the
**"Iniciar sesión"** button is present (the automatic `<Navigate to="/login">` is replaced by a
choice screen), and that clicking it lands on `LOGIN PAGE`.

- [ ] **Step 2: Run it — expect FAIL**

Run: `cd frontend && pnpm exec vitest run src/pages/customer/MenuJoin.test.tsx`

- [ ] **Step 3: Implement**

`lib/api.ts` — in `SessionTableService`:
```ts
  joinAsGuest: async (payload: {
    joinCode?: string
    qrToken?: string
    name?: string
  }): Promise<joinSessionResponse> => {
    const { data } = await api.post<joinSessionResponse>('/sessions/join-as-guest', payload)
    return data
  },
```

`MenuJoin.tsx` — replace the `if (!token || role !== 'CUSTOMER') return <Navigate to="/login" replace />`
block with a small choice UI:
- "Iniciar sesión" → keeps today's behaviour: `<Navigate to="/login" replace />` (the token is
  already parked in `sessionStorage` by the existing effect).
- "Entrar como invitado" → shows an optional name `<Input placeholder={t('qrJoinGuestNamePlaceholder')}>`
  + an "Entrar" button → `SessionTableService.joinAsGuest({ qrToken, name: name.trim() || undefined })`
  → on success `setAuth({ token })` + `setSession(session)` + `sessionStorage.removeItem(PENDING_QR_TOKEN_KEY)`
  + `navigate('/customer/menu', { replace: true })`; on 404/409 the same toasts as the
  authenticated path, then `navigate('/customer', { replace: true })`.
Keep the authenticated branch (name prompt → `joinSessionViaQr`) exactly as it is.

`JoinTableModal.tsx` — on the `opciones === 'CODE'` screen, below the confirm button, a
`variant="link"` button "Entrar como invitado" that swaps the panel to a tiny form: optional
name field + "Entrar" → `SessionTableService.joinAsGuest({ joinCode, name: name.trim() || undefined })`
→ reuse the existing `onSuccess` logic (`setAuth`, `navigate('/customer/menu')`, `setSession`,
`closeModal`). Reuse the existing `onError` (404 → `joinCodeInvalidToast`, 409 →
`joinBlockedOtherTableToast`).

Locales (`customer` namespace, es then en):
```
qrJoinSignInCta: 'Iniciar sesión' / 'Sign in'
qrJoinGuestCta: 'Entrar como invitado' / 'Continue as guest'
qrJoinGuestNameLabel: 'Tu nombre (opcional)' / 'Your name (optional)'
qrJoinGuestNamePlaceholder: 'Ej. Ana' / 'e.g. Ana'
qrJoinGuestSubmit: 'Entrar' / 'Enter'
```

- [ ] **Step 4: Run the test — expect PASS**

Run: `cd frontend && pnpm exec vitest run src/pages/customer/MenuJoin.test.tsx`

- [ ] **Step 5: Build + lint + full frontend suite**

Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run` — all green
(0 lint errors; the 16 pre-existing warnings are fine).

- [ ] **Step 6: Commit**
```bash
git add frontend/src/lib/api.ts frontend/src/pages/customer/MenuJoin.tsx \
        frontend/src/pages/customer/components/JoinTableModal.tsx \
        frontend/src/locales/es/customer.ts frontend/src/locales/en/customer.ts \
        frontend/src/pages/customer/MenuJoin.test.tsx
git commit -m "feat(customer): 'Entrar como invitado' on the QR/code join surfaces"
```

---

## Task 6: Report, PROGRESS, squash, PR

- [ ] **Step 1: Verify from a clean state**

Run `cd backend && ./mvnw test` and `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run`.
Record the counts.

- [ ] **Step 2: Report**

Create `ember/reports/NN-guest-join.md` (NN = next sequential number at commit time) with the
CLAUDE.md §4 structure: identification / objective / modified files / what changed / why.

- [ ] **Step 3: PROGRESS.md**

Update `Current Execution State` (last completed = this report; system health = the recorded
counts) and add a Task Queue entry:
`[x] Guest table-join (Cloud) — POST /sessions/join-as-guest, users.guest, loyalty guards, "Entrar como invitado" UI. Spec 2026-09-06-guest-join-design.md, plan 2026-09-06-guest-join.md.`
Note the **`V9` prod step**: it applies automatically on the next tagged backend release; no
manual ALTER needed on prod (prod Flyway is not baselined).

- [ ] **Step 4: Squash to one atomic commit**

```bash
git reset --soft $(git merge-base HEAD origin/main)
git add backend/... frontend/... docs/superpowers/... reports/NN-guest-join.md PROGRESS.md
git commit -m "feat: guest table-join (no account required)

POST /sessions/join-as-guest mints a throwaway guest User (role CUSTOMER,
guest flag, synthetic email, unusable password), gated on a valid open
table, then reuses the existing joinSessionCode / joinSession internals.
Guests behave like a normal customer for the session but never get a
loyalty account or visits (ParticipantJoined.guest + guards in
LoyaltyAccountJoinListener and LoyaltyAccrualListener). Random editable
display name. V9 adds users.guest. Frontend: 'Entrar como invitado' on
the code screen and /menu/join.

Backend <N/N>, frontend build/lint clean, test:run <N/N>."
```
(The design/plan docs were already committed on `spec/guest-join`; if implementing on that same
branch they're part of history already — only include them in the squash if starting fresh.)

- [ ] **Step 5: Push + PR**

```bash
git push -u origin <branch>
gh pr create --base main --title "feat: guest table-join (no account required)" --body "<summary — NO Claude attribution / session URL>"
```

---

## Self-Review

- **Spec coverage:** §4.1 endpoint → Task 4. §4.2 column → Task 2. §4.3 loyalty suppression →
  Task 3. §4.4 name generator → Task 1. §4.5 frontend → Task 5. §5 error table → Task 4 tests.
  §6 testing → the per-task tests + Task 6 verification. All covered.
- **Placeholder scan:** every code step has real code; the one soft spot is the integration
  test's `openSessionAndGetCode()` helper — the plan points the implementer at the existing
  session integration tests to copy the seeding pattern rather than inventing one.
- **Type consistency:** `JoinAsGuestRequest.hasExactlyOneIdentifier()`, `GuestUserService.createGuest(String)`,
  `ParticipantJoined(…, boolean guest)`, `withRescopedToken(Session, String)`,
  `SessionTableService.joinAsGuest({joinCode?,qrToken?,name?})` — names used consistently across
  the tasks that produce and consume them.
