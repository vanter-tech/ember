# Waiter Quick-Login (device-cached chips + PIN) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let anyone who has logged in on a shared floor device re-enter from a tappable profile chip on `/login`, confirming with a 4–6 digit PIN (with password fallback).

**Architecture:** A BCrypt `pin_hash` column on `users` plus a dedicated `POST /auth/login/pin` endpoint that issues the exact same JWT as password login; an in-memory per-email lockout throttles PIN guessing. The chip list is a device-local zustand `persist` store holding only name/email/role/avatar — never a token, password, or PIN. A `QuickLoginModal` drives the PIN entry and falls back to password on `PIN_NOT_SET` / `PIN_LOCKED` / user choice.

**Tech Stack:** Java 17 · Spring Boot 3.5 · Spring Security · Flyway · JPA/Postgres · React 19 · TypeScript · Zustand 5 (`persist`) · TanStack Query 5 · Vitest + React Testing Library · JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-03-waiter-quick-login-design.md`

## Global Constraints

- Backend build/test command is `./mvnw test` (from `backend/`). Never `mvn`.
- Frontend build is `pnpm run build` (`tsc -b && vite build`), lint is `pnpm run lint`, tests are `pnpm run test:run` — all from `frontend/`. Use `pnpm` only.
- Zero TypeScript / ESLint errors is a merge blocker.
- Locale files `frontend/src/locales/{es,en}/*.ts` are kept in parity by a `satisfies typeof es<Ns>` clause — every new key MUST be added to BOTH `es` and `en` in the same task or `tsc -b` fails.
- Commits: Conventional Commits, lowercase, imperative. NO `Co-authored-by:`, `Signed-off-by:`, or AI-attribution trailers of any kind (repo policy, `CLAUDE.md §4`).
- Stage only files the task touched, plus its report — never `git add -A` / `git add .`.
- `@DataJpaTest` classes in this repo MUST be annotated `@Import(TenantIdentifierResolver.class)` because `@TenantId` entities exist project-wide (even when the entity under test has none).
- PIN format is exactly `^\d{4,6}$`. PIN is BCrypt-hashed, `@JsonIgnore`, and never returned by any endpoint.
- New auth error bodies use Spring `ProblemDetail` with a `code` property (matches the existing `CASH_SHIFT_OVERDUE` pattern the frontend already reads via `error.response.data.code`).

---

## File Structure

**Backend (create)**
- `backend/src/main/resources/db/migration/V6__user_pin.sql` — adds `pin_hash`, `pin_updated_at` to `users`.
- `identity/model/dto/PinLoginRequest.java` — `{ email, pin }` request body.
- `identity/model/dto/SetPinRequest.java` — `{ currentPassword, pin }` request body.
- `identity/exception/PinNotSetException.java` — → HTTP 409, `code: PIN_NOT_SET`.
- `identity/exception/PinLockedException.java` — → HTTP 423, `code: PIN_LOCKED`.
- `identity/service/PinAttemptGuard.java` — in-memory per-email failure counter + lockout.
- `identity/controller/AccountController.java` — authenticated `POST` / `DELETE /account/pin`.

**Backend (modify)**
- `identity/model/User.java` — `pinHash` (`@JsonIgnore`), `pinUpdatedAt`.
- `identity/service/AuthService.java` — `loginWithPin`, `setPin`, `clearPin`.
- `identity/controller/AuthController.java` — `POST /auth/login/pin`.
- `config/GlobalExceptionHandler.java` — handlers for the two new exceptions.
- `config/RateLimitProperties.java` — add `/auth/login/pin` to the guarded `paths` default.

**Frontend (create)**
- `src/store/quickAccessStore.ts` (+ `quickAccessStore.test.ts`) — the device-local chip list.
- `src/pages/auth/navigateForRole.ts` (+ `navigateForRole.test.tsx`) — post-login routing, extracted from `Login.tsx`.
- `src/pages/auth/QuickLoginModal.tsx` (+ `QuickLoginModal.test.tsx`) — PIN entry + password fallback.
- `src/pages/auth/SetPinPrompt.tsx` — "create a PIN" dialog, reused by the post-login nudge and the layout header entry.

**Frontend (modify)**
- `src/lib/api.ts` — `authService.loginPin` / `setPin` / `clearPin` + inline request types.
- `src/pages/auth/Login.tsx` — render chips, call `remember()` after a successful password login.
- `src/layouts/WaiterLayout.tsx`, `src/layouts/AdminLayout.tsx` — header entry to open `SetPinPrompt`.
- `src/locales/es/auth.ts`, `src/locales/en/auth.ts` — new keys.

---

## Task 1: DB migration + `User` PIN columns

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__user_pin.sql`
- Modify: `backend/src/main/java/com/vanter/ember/identity/model/User.java`
- Test: `backend/src/test/java/com/vanter/ember/identity/model/UserPinColumnsTest.java` (create)

**Interfaces:**
- Produces: `User.getPinHash(): String` / `setPinHash(String)`, `User.getPinUpdatedAt(): Instant` / `setPinUpdatedAt(Instant)`. Both nullable. `pinHash` is `@JsonIgnore`.

- [ ] **Step 1: Write the migration**

`backend/src/main/resources/db/migration/V6__user_pin.sql`:

```sql
-- Quick-login PIN: BCrypt hash of a 4-6 digit PIN, nullable (most users never set one).
ALTER TABLE users ADD COLUMN pin_hash VARCHAR(60);
ALTER TABLE users ADD COLUMN pin_updated_at TIMESTAMP;
```

- [ ] **Step 2: Add the entity fields**

In `User.java`, after the `pendingHours` field:

```java
    @JsonIgnore
    @Column(name = "pin_hash", length = 60)
    private String pinHash;

    @Column(name = "pin_updated_at")
    private Instant pinUpdatedAt;
```

(`@JsonIgnore` and `Instant` are already imported in this file.)

- [ ] **Step 3: Write the failing test**

`UserPinColumnsTest.java` — a `@DataJpaTest` proving the column round-trips and `ddl-auto=validate` still matches:

```java
package com.vanter.ember.identity.model;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TenantIdentifierResolver.class)
class UserPinColumnsTest {

    @Autowired UserRepository userRepository;

    @Test
    void pinHashAndUpdatedAt_roundTrip() {
        User u = User.builder()
                .name("Waiter One").email("w1@test.com")
                .passwordHash("x").role(Role.WAITER)
                .pinHash("$2a$10$abcdefghijklmnopqrstuv")
                .pinUpdatedAt(Instant.parse("2026-09-03T10:00:00Z"))
                .build();

        User saved = userRepository.saveAndFlush(u);
        userRepository.findById(saved.getId()).ifPresent(found -> {
            assertThat(found.getPinHash()).isEqualTo("$2a$10$abcdefghijklmnopqrstuv");
            assertThat(found.getPinUpdatedAt()).isEqualTo(Instant.parse("2026-09-03T10:00:00Z"));
        });
    }
}
```

- [ ] **Step 4: Run it, expect FAIL then PASS**

Run: `./mvnw test -Dtest=UserPinColumnsTest` (from `backend/`).
First run before Step 2's fields exist: FAIL to compile. After Step 2: PASS.

- [ ] **Step 5: Run the full suite**

Run: `./mvnw test`
Expected: PASS (baseline count + 1). If Flyway complains the dev DB is past baseline, that is expected per PROGRESS.md — `V6` applies on a fresh DB and to CI.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V6__user_pin.sql \
  backend/src/main/java/com/vanter/ember/identity/model/User.java \
  backend/src/test/java/com/vanter/ember/identity/model/UserPinColumnsTest.java
git commit -m "feat(identity): add pin_hash column to users for quick-login"
```

---

## Task 2: `PinAttemptGuard` (in-memory lockout)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/identity/service/PinAttemptGuard.java`
- Test: `backend/src/test/java/com/vanter/ember/identity/service/PinAttemptGuardTest.java`

**Interfaces:**
- Produces:
  - `PinAttemptGuard.assertNotLocked(String email)` — throws `PinLockedException` if the email is currently locked.
  - `PinAttemptGuard.recordFailure(String email)` — increments the window counter; 5th failure within 15 min starts a lockout until the window rolls.
  - `PinAttemptGuard.recordSuccess(String email)` — clears the email's counter.
  - Constructor takes a `Clock` (default bean uses `Clock.systemUTC()`), so tests can advance time.
- Consumes: `PinLockedException` from Task 3 (create it here if Task 3 hasn't run — it is a trivial exception class; the canonical copy lives in Task 3's file list, keep them identical).

- [ ] **Step 1: Create the exception (if not already present)**

`backend/src/main/java/com/vanter/ember/identity/exception/PinLockedException.java`:

```java
package com.vanter.ember.identity.exception;

public class PinLockedException extends RuntimeException {
    public PinLockedException() { super("Too many failed PIN attempts. Use your password."); }
}
```

- [ ] **Step 2: Write the failing test**

`PinAttemptGuardTest.java`:

```java
package com.vanter.ember.identity.service;

import com.vanter.ember.identity.exception.PinLockedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class PinAttemptGuardTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-03T12:00:00Z"));
    private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC); // replaced below

    private PinAttemptGuard newGuard() {
        Clock movable = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return now.get(); }
        };
        return new PinAttemptGuard(movable);
    }

    @Test
    void locksAfterFiveFailuresInWindow() {
        PinAttemptGuard guard = newGuard();
        for (int i = 0; i < 5; i++) guard.recordFailure("w1@test.com");
        assertThatThrownBy(() -> guard.assertNotLocked("w1@test.com"))
                .isInstanceOf(PinLockedException.class);
    }

    @Test
    void successClearsCounter() {
        PinAttemptGuard guard = newGuard();
        for (int i = 0; i < 4; i++) guard.recordFailure("w1@test.com");
        guard.recordSuccess("w1@test.com");
        guard.recordFailure("w1@test.com");
        assertThatCode(() -> guard.assertNotLocked("w1@test.com")).doesNotThrowAnyException();
    }

    @Test
    void lockExpiresAfterWindow() {
        PinAttemptGuard guard = newGuard();
        for (int i = 0; i < 5; i++) guard.recordFailure("w1@test.com");
        now.set(now.get().plus(Duration.ofMinutes(16)));
        assertThatCode(() -> guard.assertNotLocked("w1@test.com")).doesNotThrowAnyException();
    }

    @Test
    void isCaseInsensitiveOnEmail() {
        PinAttemptGuard guard = newGuard();
        for (int i = 0; i < 5; i++) guard.recordFailure("W1@Test.com");
        assertThatThrownBy(() -> guard.assertNotLocked("w1@test.com"))
                .isInstanceOf(PinLockedException.class);
    }
}
```

- [ ] **Step 3: Run it, expect FAIL**

Run: `./mvnw test -Dtest=PinAttemptGuardTest`
Expected: FAIL — `PinAttemptGuard` does not exist.

- [ ] **Step 4: Implement**

`PinAttemptGuard.java`:

```java
package com.vanter.ember.identity.service;

import com.vanter.ember.identity.exception.PinLockedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Best-effort brute-force throttle for {@code POST /auth/login/pin}. In-memory and node-local:
 * a PIN is low-entropy, and the password path (with its own protections) remains the fallback,
 * so a counter that resets on restart is acceptable for this single-node modular monolith.
 */
@Component
public class PinAttemptGuard {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempt(int count, Instant windowStart) {}

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public PinAttemptGuard(Clock clock) {
        this.clock = clock;
    }

    public void assertNotLocked(String email) {
        Attempt a = attempts.get(key(email));
        if (a != null && a.count() >= MAX_FAILURES && !windowExpired(a)) {
            throw new PinLockedException();
        }
    }

    public void recordFailure(String email) {
        attempts.compute(key(email), (k, a) -> {
            Instant nowInstant = clock.instant();
            if (a == null || windowExpired(a)) {
                return new Attempt(1, nowInstant);
            }
            return new Attempt(a.count() + 1, a.windowStart());
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    private boolean windowExpired(Attempt a) {
        return a.windowStart().plus(WINDOW).isBefore(clock.instant());
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
```

Add a `Clock` bean if the project has none — check `config/` for an existing `@Bean Clock`. If absent, add to an existing `@Configuration` (e.g. `config/AppConfig.java` if present, else create `identity/config/IdentityClockConfig.java`):

```java
@Bean
public Clock clock() { return Clock.systemUTC(); }
```

- [ ] **Step 5: Run it, expect PASS**

Run: `./mvnw test -Dtest=PinAttemptGuardTest` → PASS.

- [ ] **Step 6: Full suite + commit**

Run: `./mvnw test` → PASS.

```bash
git add backend/src/main/java/com/vanter/ember/identity/service/PinAttemptGuard.java \
  backend/src/main/java/com/vanter/ember/identity/exception/PinLockedException.java \
  backend/src/test/java/com/vanter/ember/identity/service/PinAttemptGuardTest.java
# plus the Clock @Bean file if you created/edited one
git commit -m "feat(identity): in-memory pin attempt lockout guard"
```

---

## Task 3: `POST /auth/login/pin` (service + endpoint + error mapping + rate limit)

**Files:**
- Create: `identity/model/dto/PinLoginRequest.java`, `identity/exception/PinNotSetException.java`
- Modify: `identity/service/AuthService.java`, `identity/controller/AuthController.java`, `config/GlobalExceptionHandler.java`, `config/RateLimitProperties.java`
- Test: modify `identity/service/AuthServiceTest.java`, `identity/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `PinAttemptGuard` (Task 2), `User.getPinHash()` (Task 1).
- Produces:
  - `AuthService.loginWithPin(PinLoginRequest): AuthResponse` — same `AuthResponse` shape as `login(...)`.
  - `POST /auth/login/pin` → `200` + `AuthResponse`; `409 {code:PIN_NOT_SET}`; `423 {code:PIN_LOCKED}`; `401` invalid PIN / inactive.

- [ ] **Step 1: Create the request DTO and exception**

`PinLoginRequest.java`:

```java
package com.vanter.ember.identity.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PinLoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4 to 6 digits")
    private String pin;
}
```

`PinNotSetException.java`:

```java
package com.vanter.ember.identity.exception;

public class PinNotSetException extends RuntimeException {
    public PinNotSetException() { super("No PIN is set for this account."); }
}
```

- [ ] **Step 2: Write the failing service tests**

Append to `AuthServiceTest.java` (it uses `@Mock UserRepository/JwtService/PasswordEncoder` + `@InjectMocks AuthService`). Add `@Mock PinAttemptGuard pinAttemptGuard;` to the fields.

```java
    @Test
    void loginWithPin_returnsSameClaimsAsPassword_whenPinMatches() {
        User user = User.builder().id("u1").name("Waiter").email("w@test.com")
                .role(Role.WAITER).passwordHash("pw").pinHash("pinHash").active(true).build();
        when(userRepository.findByEmail("w@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("1234", "pinHash")).thenReturn(true);
        when(jwtService.generateToken(eq("w@test.com"), anyMap())).thenReturn("jwt-123");

        PinLoginRequest req = new PinLoginRequest();
        req.setEmail("w@test.com"); req.setPin("1234");

        AuthResponse res = authService.loginWithPin(req);

        assertThat(res.getToken()).isEqualTo("jwt-123");
        assertThat(res.getRole()).isEqualTo("WAITER");
        assertThat(res.getUserId()).isEqualTo("u1");
        verify(pinAttemptGuard).assertNotLocked("w@test.com");
        verify(pinAttemptGuard).recordSuccess("w@test.com");
    }

    @Test
    void loginWithPin_throwsPinNotSet_whenPinHashNull() {
        User user = User.builder().id("u1").email("w@test.com").name("W")
                .role(Role.WAITER).passwordHash("pw").pinHash(null).active(true).build();
        when(userRepository.findByEmail("w@test.com")).thenReturn(Optional.of(user));

        PinLoginRequest req = new PinLoginRequest();
        req.setEmail("w@test.com"); req.setPin("1234");

        assertThatThrownBy(() -> authService.loginWithPin(req))
                .isInstanceOf(com.vanter.ember.identity.exception.PinNotSetException.class);
    }

    @Test
    void loginWithPin_recordsFailureAndThrows_whenPinWrong() {
        User user = User.builder().id("u1").email("w@test.com").name("W")
                .role(Role.WAITER).passwordHash("pw").pinHash("pinHash").active(true).build();
        when(userRepository.findByEmail("w@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("9999", "pinHash")).thenReturn(false);

        PinLoginRequest req = new PinLoginRequest();
        req.setEmail("w@test.com"); req.setPin("9999");

        assertThatThrownBy(() -> authService.loginWithPin(req))
                .isInstanceOf(BadCredentialsException.class);
        verify(pinAttemptGuard).recordFailure("w@test.com");
    }

    @Test
    void loginWithPin_throws_whenUserInactive() {
        User user = User.builder().id("u1").email("w@test.com").name("W")
                .role(Role.WAITER).passwordHash("pw").pinHash("pinHash").active(false).build();
        when(userRepository.findByEmail("w@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("1234", "pinHash")).thenReturn(true);

        PinLoginRequest req = new PinLoginRequest();
        req.setEmail("w@test.com"); req.setPin("1234");

        assertThatThrownBy(() -> authService.loginWithPin(req)).isInstanceOf(BadCredentialsException.class);
    }
```

- [ ] **Step 3: Run, expect FAIL**

Run: `./mvnw test -Dtest=AuthServiceTest`
Expected: FAIL to compile (`loginWithPin`, `pinAttemptGuard` unknown).

- [ ] **Step 4: Implement `loginWithPin`**

In `AuthService.java` add the field (constructor is Lombok `@RequiredArgsConstructor` — just add the field):

```java
    private final PinAttemptGuard pinAttemptGuard;
```

Add the method (mirrors `login` but PIN-based, and asserts/records via the guard):

```java
    public AuthResponse loginWithPin(PinLoginRequest request) {
        pinAttemptGuard.assertNotLocked(request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.getPinHash() == null) {
            throw new PinNotSetException();
        }

        if (!passwordEncoder.matches(request.getPin(), user.getPinHash())
                || !Boolean.TRUE.equals(user.getActive())) {
            pinAttemptGuard.recordFailure(request.getEmail());
            throw new BadCredentialsException("Invalid credentials");
        }

        pinAttemptGuard.recordSuccess(request.getEmail());
        return buildResponse(user, tenantIdOf(user));
    }
```

Add imports: `com.vanter.ember.identity.exception.PinNotSetException`, `com.vanter.ember.identity.model.dto.PinLoginRequest`, `com.vanter.ember.identity.service.PinAttemptGuard` (same package — no import needed).

- [ ] **Step 5: Add the controller endpoint**

In `AuthController.java`:

```java
    @Operation(summary = "Login with a quick-access PIN")
    @PostMapping("/login/pin")
    public ResponseEntity<AuthResponse> loginWithPin(@Valid @RequestBody PinLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithPin(request));
    }
```

Import `com.vanter.ember.identity.model.dto.PinLoginRequest`.

- [ ] **Step 6: Map the exceptions**

In `GlobalExceptionHandler.java` add, next to `handleBadCredentials`:

```java
    @ExceptionHandler(com.vanter.ember.identity.exception.PinNotSetException.class)
    public ProblemDetail handlePinNotSet(
            com.vanter.ember.identity.exception.PinNotSetException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
        problem.setProperty("code", "PIN_NOT_SET");
        return problem;
    }

    @ExceptionHandler(com.vanter.ember.identity.exception.PinLockedException.class)
    public ProblemDetail handlePinLocked(
            com.vanter.ember.identity.exception.PinLockedException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.LOCKED, ex.getMessage(), request.getRequestURI());
        problem.setProperty("code", "PIN_LOCKED");
        return problem;
    }
```

(`HttpStatus.LOCKED` is 423. `BadCredentialsException` already maps to 401 via the existing handler — no change needed for the wrong-PIN case.)

- [ ] **Step 7: Rate-limit the endpoint**

In `config/RateLimitProperties.java`, change the `paths` default list to include `/auth/login/pin`:

```java
    private List<String> paths = new ArrayList<>(
            List.of("/auth/login", "/auth/login/pin", "/auth/register", "/platform/auth/login",
                    "/hub-activations", "/hub-heartbeat"));
```

- [ ] **Step 8: Write the failing controller tests**

In `AuthControllerTest.java` (a `@WebMvcTest(AuthController.class)` with `@MockBean AuthService`). Add:

```java
    @Test
    void loginWithPin_200_onSuccess() throws Exception {
        when(authService.loginWithPin(any())).thenReturn(
                AuthResponse.builder().token("jwt").userId("u1").name("W").role("WAITER").build());

        mockMvc.perform(post("/auth/login/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"w@test.com\",\"pin\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.role").value("WAITER"));
    }

    @Test
    void loginWithPin_409_withCode_whenPinNotSet() throws Exception {
        when(authService.loginWithPin(any()))
                .thenThrow(new com.vanter.ember.identity.exception.PinNotSetException());

        mockMvc.perform(post("/auth/login/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"w@test.com\",\"pin\":\"1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIN_NOT_SET"));
    }

    @Test
    void loginWithPin_423_withCode_whenLocked() throws Exception {
        when(authService.loginWithPin(any()))
                .thenThrow(new com.vanter.ember.identity.exception.PinLockedException());

        mockMvc.perform(post("/auth/login/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"w@test.com\",\"pin\":\"1234\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"));
    }

    @Test
    void loginWithPin_400_whenPinMalformed() throws Exception {
        mockMvc.perform(post("/auth/login/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"w@test.com\",\"pin\":\"12\"}"))
                .andExpect(status().isBadRequest());
    }
```

If `AuthControllerTest` does not already import `GlobalExceptionHandler`, add `@Import(GlobalExceptionHandler.class)` to the test class so the `code` body is produced under `@WebMvcTest` (check how the existing `handleBadCredentials` test in this file is set up and match it).

- [ ] **Step 9: Run both test classes, expect PASS**

Run: `./mvnw test -Dtest=AuthServiceTest,AuthControllerTest,PinAttemptGuardTest` → PASS.

- [ ] **Step 10: Full suite + commit**

Run: `./mvnw test` → PASS.

```bash
git add backend/src/main/java/com/vanter/ember/identity/model/dto/PinLoginRequest.java \
  backend/src/main/java/com/vanter/ember/identity/exception/PinNotSetException.java \
  backend/src/main/java/com/vanter/ember/identity/service/AuthService.java \
  backend/src/main/java/com/vanter/ember/identity/controller/AuthController.java \
  backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java \
  backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java \
  backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java \
  backend/src/test/java/com/vanter/ember/identity/controller/AuthControllerTest.java
git commit -m "feat(identity): pin login endpoint with lockout and error codes"
```

---

## Task 4: `POST` / `DELETE /account/pin` (authenticated PIN management)

**Files:**
- Create: `identity/model/dto/SetPinRequest.java`, `identity/controller/AccountController.java`
- Modify: `identity/service/AuthService.java`
- Test: `backend/src/test/java/com/vanter/ember/identity/controller/AccountControllerTest.java` (create), modify `AuthServiceTest.java`

**Interfaces:**
- Produces:
  - `AuthService.setPin(String email, SetPinRequest): void` — verifies `currentPassword`, then stores `bcrypt(pin)` + `pinUpdatedAt = Instant.now()`.
  - `AuthService.clearPin(String email): void` — nulls `pinHash` and `pinUpdatedAt`.
  - `POST /account/pin` (auth required) → `204`; `401` bad current password; `400` bad PIN format.
  - `DELETE /account/pin` (auth required) → `204`.
- Note: `/account/**` falls under `anyRequest().authenticated()` (it is NOT under the `permitAll` `/auth/**` matcher), so no `@PreAuthorize` is required for the authn gate — the filter chain enforces it. The spec named these `/auth/pin`; they live under `/account/pin` so the security filter chain, not method security, enforces authentication.

- [ ] **Step 1: Create `SetPinRequest`**

```java
package com.vanter.ember.identity.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SetPinRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4 to 6 digits")
    private String pin;
}
```

- [ ] **Step 2: Write failing service tests**

Append to `AuthServiceTest.java`:

```java
    @Test
    void setPin_storesBcryptHashAndTimestamp_whenCurrentPasswordMatches() {
        User user = User.builder().id("u1").email("w@test.com").name("W")
                .role(Role.WAITER).passwordHash("pwHash").active(true).build();
        when(userRepository.findByEmail("w@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("realpw", "pwHash")).thenReturn(true);
        when(passwordEncoder.encode("1234")).thenReturn("pinHash");

        SetPinRequest req = new SetPinRequest();
        req.setCurrentPassword("realpw"); req.setPin("1234");

        authService.setPin("w@test.com", req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPinHash()).isEqualTo("pinHash");
        assertThat(captor.getValue().getPinUpdatedAt()).isNotNull();
    }

    @Test
    void setPin_throws_whenCurrentPasswordWrong() {
        User user = User.builder().id("u1").email("w@test.com").name("W")
                .role(Role.WAITER).passwordHash("pwHash").active(true).build();
        when(userRepository.findByEmail("w@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "pwHash")).thenReturn(false);

        SetPinRequest req = new SetPinRequest();
        req.setCurrentPassword("bad"); req.setPin("1234");

        assertThatThrownBy(() -> authService.setPin("w@test.com", req))
                .isInstanceOf(BadCredentialsException.class);
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void clearPin_nullsBothColumns() {
        User user = User.builder().id("u1").email("w@test.com").name("W")
                .role(Role.WAITER).passwordHash("pwHash").pinHash("pinHash")
                .pinUpdatedAt(java.time.Instant.now()).active(true).build();
        when(userRepository.findByEmail("w@test.com")).thenReturn(Optional.of(user));

        authService.clearPin("w@test.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPinHash()).isNull();
        assertThat(captor.getValue().getPinUpdatedAt()).isNull();
    }
```

Add import `com.vanter.ember.identity.model.dto.SetPinRequest`.

- [ ] **Step 3: Run, expect FAIL**

Run: `./mvnw test -Dtest=AuthServiceTest` → FAIL to compile.

- [ ] **Step 4: Implement in `AuthService`**

```java
    public void setPin(String email, SetPinRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        user.setPinHash(passwordEncoder.encode(request.getPin()));
        user.setPinUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    public void clearPin(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        user.setPinHash(null);
        user.setPinUpdatedAt(null);
        userRepository.save(user);
    }
```

Add imports `java.time.Instant`, `com.vanter.ember.identity.model.dto.SetPinRequest`.

- [ ] **Step 5: Create `AccountController`**

```java
package com.vanter.ember.identity.controller;

import com.vanter.ember.identity.model.dto.SetPinRequest;
import com.vanter.ember.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "Self-service credential management")
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AuthService authService;

    @Operation(summary = "Set or replace the caller's quick-access PIN")
    @PostMapping("/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPin(@Valid @RequestBody SetPinRequest request, Authentication authentication) {
        authService.setPin(authentication.getName(), request);
    }

    @Operation(summary = "Remove the caller's quick-access PIN")
    @DeleteMapping("/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearPin(Authentication authentication) {
        authService.clearPin(authentication.getName());
    }
}
```

- [ ] **Step 6: Write failing controller tests**

`AccountControllerTest.java` — model it on `AuthControllerTest` (`@WebMvcTest(AccountController.class)`, `@MockBean AuthService`, `@Import(GlobalExceptionHandler.class)` if that is the local convention). Because `/account/**` is `authenticated()`, use `@WithMockUser(username = "w@test.com")` for the happy paths and assert `401` (or the project's unauthenticated status — check a sibling authenticated-controller `@WebMvcTest`) with no user.

```java
    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "w@test.com")
    void setPin_204_onSuccess() throws Exception {
        mockMvc.perform(post("/account/pin").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"pw\",\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());
        verify(authService).setPin(eq("w@test.com"), any());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "w@test.com")
    void setPin_400_whenPinMalformed() throws Exception {
        mockMvc.perform(post("/account/pin").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"pw\",\"pin\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "w@test.com")
    void setPin_401_whenCurrentPasswordWrong() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.security.authentication.BadCredentialsException("x"))
                .when(authService).setPin(eq("w@test.com"), any());
        mockMvc.perform(post("/account/pin").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"bad\",\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "w@test.com")
    void clearPin_204() throws Exception {
        mockMvc.perform(delete("/account/pin").with(csrf())).andExpect(status().isNoContent());
        verify(authService).clearPin("w@test.com");
    }
```

If the project's `@WebMvcTest`s disable CSRF globally (check `AuthControllerTest` — the existing POST tests will show whether `.with(csrf())` is used), drop `.with(csrf())` to match.

- [ ] **Step 7: Run, expect PASS**

Run: `./mvnw test -Dtest=AccountControllerTest,AuthServiceTest` → PASS.

- [ ] **Step 8: Full suite + commit**

Run: `./mvnw test` → PASS.

```bash
git add backend/src/main/java/com/vanter/ember/identity/model/dto/SetPinRequest.java \
  backend/src/main/java/com/vanter/ember/identity/controller/AccountController.java \
  backend/src/main/java/com/vanter/ember/identity/service/AuthService.java \
  backend/src/test/java/com/vanter/ember/identity/controller/AccountControllerTest.java \
  backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java
git commit -m "feat(identity): self-service pin set and clear endpoints"
```

---

## Task 5: Frontend `authService` methods + inline request types

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Test: none (thin wrappers; exercised by Task 9's modal test with a mocked `api`)

**Interfaces:**
- Produces:
  - `authService.loginPin(body: { email: string; pin: string }): Promise<LoginResponse>`
  - `authService.setPin(body: { currentPassword: string; pin: string }): Promise<void>`
  - `authService.clearPin(): Promise<void>`

- [ ] **Step 1: Add the methods**

In `api.ts`, extend the existing `authService` object (after `register`):

```ts
  loginPin: async (body: { email: string; pin: string }): Promise<LoginResponse> => {
    const { data } = await api.post<LoginResponse>('/auth/login/pin', body)
    return data
  },

  setPin: async (body: { currentPassword: string; pin: string }): Promise<void> => {
    await api.post<void>('/account/pin', body)
  },

  clearPin: async (): Promise<void> => {
    await api.delete<void>('/account/pin')
  },
```

- [ ] **Step 2: Type-check**

Run: `cd frontend && pnpm run build`
Expected: PASS (0 TS errors).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(frontend): auth api methods for pin login and pin management"
```

---

## Task 6: `quickAccessStore` (device-local chip list)

**Files:**
- Create: `frontend/src/store/quickAccessStore.ts`, `frontend/src/store/quickAccessStore.test.ts`

**Interfaces:**
- Produces:
  - `type QuickAccessProfile = { email: string; name: string; role: string; initials: string; colorSeed: number; lastUsedAt: number }`
  - `useQuickAccessStore` with state `{ profiles: QuickAccessProfile[]; pinDismissed: string[] }` and actions `remember(p: Omit<QuickAccessProfile,'lastUsedAt'|'initials'|'colorSeed'>)`, `forget(email)`, `clear()`, `dismissPinPrompt(email)`.
  - `remember` derives `initials` + `colorSeed` from `name`/`email`, upserts by lowercased `email`, sets `lastUsedAt = Date.now()`, caps the list at 6 (evicting the smallest `lastUsedAt`).

- [ ] **Step 1: Write the failing test**

`quickAccessStore.test.ts` (mirror `authStore.test.ts` style — `beforeEach` resets state):

```ts
import { describe, test, expect, beforeEach } from 'vitest'
import { useQuickAccessStore } from '@/store/quickAccessStore'

const reset = () => useQuickAccessStore.setState({ profiles: [], pinDismissed: [] })

describe('quickAccessStore', () => {
  beforeEach(reset)

  test('remember adds a profile with derived initials and lastUsedAt', () => {
    useQuickAccessStore.getState().remember({ email: 'juan.perez@x.com', name: 'Juan Perez', role: 'WAITER' })
    const [p] = useQuickAccessStore.getState().profiles
    expect(p.email).toBe('juan.perez@x.com')
    expect(p.initials).toBe('JP')
    expect(p.lastUsedAt).toBeGreaterThan(0)
  })

  test('remember upserts by email (case-insensitive), not duplicate', () => {
    const s = useQuickAccessStore.getState()
    s.remember({ email: 'a@x.com', name: 'A', role: 'WAITER' })
    s.remember({ email: 'A@X.com', name: 'A Updated', role: 'ADMIN' })
    const list = useQuickAccessStore.getState().profiles
    expect(list).toHaveLength(1)
    expect(list[0].name).toBe('A Updated')
    expect(list[0].role).toBe('ADMIN')
  })

  test('caps at 6, evicting the oldest lastUsedAt', () => {
    const s = useQuickAccessStore.getState()
    for (let i = 0; i < 7; i++) {
      s.remember({ email: `u${i}@x.com`, name: `U ${i}`, role: 'WAITER' })
      // force distinct timestamps
      useQuickAccessStore.setState({
        profiles: useQuickAccessStore.getState().profiles.map((p) =>
          p.email === `u${i}@x.com` ? { ...p, lastUsedAt: 1000 + i } : p),
      })
    }
    const list = useQuickAccessStore.getState().profiles
    expect(list).toHaveLength(6)
    expect(list.find((p) => p.email === 'u0@x.com')).toBeUndefined()
  })

  test('forget removes by email; clear empties', () => {
    const s = useQuickAccessStore.getState()
    s.remember({ email: 'a@x.com', name: 'A', role: 'WAITER' })
    s.remember({ email: 'b@x.com', name: 'B', role: 'WAITER' })
    s.forget('a@x.com')
    expect(useQuickAccessStore.getState().profiles.map((p) => p.email)).toEqual(['b@x.com'])
    s.clear()
    expect(useQuickAccessStore.getState().profiles).toHaveLength(0)
  })

  test('dismissPinPrompt records the email once', () => {
    const s = useQuickAccessStore.getState()
    s.dismissPinPrompt('a@x.com')
    s.dismissPinPrompt('a@x.com')
    expect(useQuickAccessStore.getState().pinDismissed).toEqual(['a@x.com'])
  })
})
```

- [ ] **Step 2: Run, expect FAIL**

Run: `cd frontend && pnpm run test:run quickAccessStore`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement**

`quickAccessStore.ts`:

```ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface QuickAccessProfile {
  email: string
  name: string
  role: string
  initials: string
  colorSeed: number
  lastUsedAt: number
}

interface QuickAccessState {
  profiles: QuickAccessProfile[]
  pinDismissed: string[]
  remember: (p: { email: string; name: string; role: string }) => void
  forget: (email: string) => void
  clear: () => void
  dismissPinPrompt: (email: string) => void
}

const MAX_PROFILES = 6

const initials = (name: string) =>
  name.trim().split(/\s+/).slice(0, 2).map((w) => w[0]?.toUpperCase() ?? '').join('') || '?'

const colorSeed = (email: string) => {
  let h = 0
  for (let i = 0; i < email.length; i++) h = (h * 31 + email.charCodeAt(i)) >>> 0
  return h % 360
}

export const useQuickAccessStore = create<QuickAccessState>()(
  persist(
    (set) => ({
      profiles: [],
      pinDismissed: [],

      remember: ({ email, name, role }) =>
        set((state) => {
          const key = email.trim().toLowerCase()
          const next: QuickAccessProfile = {
            email, name, role,
            initials: initials(name),
            colorSeed: colorSeed(key),
            lastUsedAt: Date.now(),
          }
          const without = state.profiles.filter((p) => p.email.trim().toLowerCase() !== key)
          const merged = [next, ...without]
            .sort((a, b) => b.lastUsedAt - a.lastUsedAt)
            .slice(0, MAX_PROFILES)
          return { profiles: merged }
        }),

      forget: (email) =>
        set((state) => ({
          profiles: state.profiles.filter(
            (p) => p.email.trim().toLowerCase() !== email.trim().toLowerCase()),
        })),

      clear: () => set({ profiles: [] }),

      dismissPinPrompt: (email) =>
        set((state) =>
          state.pinDismissed.includes(email)
            ? state
            : { pinDismissed: [...state.pinDismissed, email] }),
    }),
    { name: 'ember-quick-access' }
  )
)
```

- [ ] **Step 4: Run, expect PASS**

Run: `cd frontend && pnpm run test:run quickAccessStore` → PASS.

- [ ] **Step 5: Build + commit**

Run: `cd frontend && pnpm run build && pnpm run lint` → PASS.

```bash
git add frontend/src/store/quickAccessStore.ts frontend/src/store/quickAccessStore.test.ts
git commit -m "feat(frontend): device-local quick-access profile store"
```

---

## Task 7: Extract `navigateForRole` from `Login.tsx`

**Files:**
- Create: `frontend/src/pages/auth/navigateForRole.ts`, `frontend/src/pages/auth/navigateForRole.test.tsx`
- Modify: `frontend/src/pages/auth/Login.tsx`

**Interfaces:**
- Produces: `navigateForRole(response: LoginResponse, navigate: NavigateFunction, deps: { tAuth: (k: string, o?: Record<string, unknown>) => string }): Promise<void>` — the exact behavior of `Login.tsx`'s current `switch (response.role)` block, including the CUSTOMER resume-session branch and its toasts.
- Consumes: `useAuthStore`, `useSessionStore`, `SessionTableService` (already imported by `Login.tsx`).

- [ ] **Step 1: Move the logic**

Create `navigateForRole.ts` with the body currently inside `Login.tsx`'s `onSubmit` from `switch (response.role) {` through the end of that switch. Signature:

```ts
import type { NavigateFunction } from 'react-router-dom'
import type { LoginResponse } from '@/lib/api'
import { SessionTableService } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { useSessionStore } from '@/store/sessionStore'
import toast from 'react-hot-toast'

type TAuth = (key: string, opts?: Record<string, unknown>) => string

export async function navigateForRole(
  response: LoginResponse,
  navigate: NavigateFunction,
  { tAuth }: { tAuth: TAuth },
): Promise<void> {
  const { setAuth } = useAuthStore.getState()
  switch (response.role) {
    case 'ADMIN':
      navigate('/admin', { replace: true })
      break
    case 'CUSTOMER': {
      const openSessionId = useSessionStore.getState().id
      if (openSessionId) {
        try {
          const resumed = await SessionTableService.resumeSession(openSessionId)
          if (resumed.token) setAuth({ token: resumed.token })
          if (resumed.session) useSessionStore.getState().setSession(resumed.session)
          toast.success(tAuth('sessionResumedToast'))
          navigate('/customer/menu', { replace: true })
          break
        } catch {
          useSessionStore.getState().clearSession()
        }
      }
      navigate('/customer/home', { replace: true })
      break
    }
    case 'WAITER':
      navigate('/waiter', { replace: true })
      break
    case 'KITCHEN':
      navigate('/kitchen', { replace: true })
      break
    default:
      break
  }
}
```

Confirm against the current `Login.tsx` body and copy any detail that differs (e.g. exact toast keys) verbatim.

- [ ] **Step 2: Rewire `Login.tsx`**

In `Login.tsx` `onSubmit`, replace the `switch` block with:

```ts
      await navigateForRole(response, navigate, { tAuth })
```

Keep `setAuth(response)` and the success toast that precede it. Remove now-unused imports only if genuinely unused (`useSessionStore`/`SessionTableService` may still be referenced — check before deleting).

- [ ] **Step 3: Write the test**

`navigateForRole.test.tsx`:

```tsx
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { navigateForRole } from '@/pages/auth/navigateForRole'
import { useSessionStore } from '@/store/sessionStore'

const tAuth = (k: string) => k

describe('navigateForRole', () => {
  beforeEach(() => { useSessionStore.setState({ id: undefined } as never) })

  test('ADMIN → /admin', async () => {
    const navigate = vi.fn()
    await navigateForRole({ role: 'ADMIN' } as never, navigate, { tAuth })
    expect(navigate).toHaveBeenCalledWith('/admin', { replace: true })
  })

  test('WAITER → /waiter', async () => {
    const navigate = vi.fn()
    await navigateForRole({ role: 'WAITER' } as never, navigate, { tAuth })
    expect(navigate).toHaveBeenCalledWith('/waiter', { replace: true })
  })

  test('KITCHEN → /kitchen', async () => {
    const navigate = vi.fn()
    await navigateForRole({ role: 'KITCHEN' } as never, navigate, { tAuth })
    expect(navigate).toHaveBeenCalledWith('/kitchen', { replace: true })
  })

  test('CUSTOMER with no open session → /customer/home', async () => {
    const navigate = vi.fn()
    await navigateForRole({ role: 'CUSTOMER' } as never, navigate, { tAuth })
    expect(navigate).toHaveBeenCalledWith('/customer/home', { replace: true })
  })
})
```

- [ ] **Step 4: Run tests + build**

Run: `cd frontend && pnpm run test:run navigateForRole && pnpm run build && pnpm run lint`
Expected: PASS. Existing `Login` tests (if any) still green.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/auth/navigateForRole.ts frontend/src/pages/auth/navigateForRole.test.tsx frontend/src/pages/auth/Login.tsx
git commit -m "refactor(frontend): extract navigateForRole from Login"
```

---

## Task 8: `Login.tsx` — render quick-access chips + remember on login

**Files:**
- Modify: `frontend/src/pages/auth/Login.tsx`, `frontend/src/locales/es/auth.ts`, `frontend/src/locales/en/auth.ts`
- Test: `frontend/src/pages/auth/Login.quickaccess.test.tsx` (create)

**Interfaces:**
- Consumes: `useQuickAccessStore` (Task 6), `QuickLoginModal` (Task 9 — import lazily/at top; if Task 9 not yet done, stub a component that renders `null` and replace in Task 9). Prefer ordering Task 9 before this if executing sequentially; if not, the stub is: `const QuickLoginModal = (_: { profile: QuickAccessProfile; onClose: () => void }) => null`.
- Produces: chips UI on `/login`; `remember(...)` called after a successful password login.

- [ ] **Step 1: Add i18n keys**

Add to BOTH `locales/es/auth.ts` and `locales/en/auth.ts` (ES values shown; supply natural EN equivalents):

```ts
  quickStartTitle: 'Inicio rápido',          // EN: 'Quick start'
  useAnotherAccount: 'Usar otra cuenta',      // EN: 'Use another account'
  editChips: 'Editar',                        // EN: 'Edit'
  doneEditingChips: 'Listo',                  // EN: 'Done'
  removeChipAria: 'Quitar {name}',            // EN: 'Remove {name}'
```

- [ ] **Step 2: Write the failing test**

`Login.quickaccess.test.tsx`:

```tsx
import { describe, test, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Login } from '@/pages/auth/Login'
import { useQuickAccessStore } from '@/store/quickAccessStore'

vi.mock('@/pages/auth/QuickLoginModal', () => ({ QuickLoginModal: () => null }))

const renderLogin = () => render(<MemoryRouter><Login /></MemoryRouter>)

describe('Login quick-access chips', () => {
  beforeEach(() => useQuickAccessStore.setState({ profiles: [], pinDismissed: [] }))

  test('no chips section when store empty; form is visible', () => {
    renderLogin()
    expect(screen.queryByText('quickStartTitle')).not.toBeInTheDocument()
    expect(screen.getByPlaceholderText('emailPlaceholder')).toBeVisible()
  })

  test('renders a chip per stored profile and hides the form by default', () => {
    useQuickAccessStore.setState({
      profiles: [{ email: 'juan@x.com', name: 'Juan Perez', role: 'WAITER', initials: 'JP', colorSeed: 10, lastUsedAt: 1 }],
      pinDismissed: [],
    })
    renderLogin()
    expect(screen.getByText('Juan Perez')).toBeVisible()
    expect(screen.queryByPlaceholderText('emailPlaceholder')).not.toBeVisible()
    expect(screen.getByText('useAnotherAccount')).toBeVisible()
  })
})
```

(`useTranslation` in tests returns the key as its own value — confirm against an existing `*.test.tsx` that renders a translated component and match its setup, e.g. any needed i18n provider wrapper.)

- [ ] **Step 3: Run, expect FAIL**

Run: `cd frontend && pnpm run test:run Login.quickaccess` → FAIL.

- [ ] **Step 4: Implement in `Login.tsx`**

- Import `useQuickAccessStore` and `QuickLoginModal`.
- `const { profiles, forget, remember } = useQuickAccessStore()`.
- `const [showForm, setShowForm] = useState(false)`, `const [editing, setEditing] = useState(false)`, `const [activeChip, setActiveChip] = useState<QuickAccessProfile | null>(null)`.
- `const chipsVisible = profiles.length > 0 && !showForm`.
- After `setAuth(response)` in `onSubmit`, add:
  `remember({ email: data.email, name: response.name ?? data.email, role: response.role ?? '' })`.
- In JSX, above `<Form>`: when `profiles.length > 0`, render the chips block:

```tsx
{profiles.length > 0 && (
  <div className="mb-6">
    <div className="flex items-center justify-between mb-2">
      <span className="text-sm font-semibold text-zinc-600">{tAuth('quickStartTitle')}</span>
      <button type="button" className="text-xs text-zinc-500 hover:underline"
        onClick={() => setEditing((e) => !e)}>
        {editing ? tAuth('doneEditingChips') : tAuth('editChips')}
      </button>
    </div>
    <div className="grid grid-cols-2 gap-2">
      {profiles.map((p) => (
        <div key={p.email} className="relative">
          <button type="button"
            onClick={() => setActiveChip(p)}
            className="w-full flex items-center gap-2 rounded-2xl border p-2 hover:bg-zinc-50">
            <span className="flex h-9 w-9 items-center justify-center rounded-full text-white text-sm font-bold"
              style={{ backgroundColor: `hsl(${p.colorSeed} 55% 45%)` }}>{p.initials}</span>
            <span className="flex flex-col text-left">
              <span className="text-sm font-medium text-zinc-800 truncate">{p.name}</span>
              <span className="text-[10px] uppercase tracking-wide text-zinc-400">{p.role}</span>
            </span>
          </button>
          {editing && (
            <button type="button" aria-label={tAuth('removeChipAria', { name: p.name })}
              onClick={() => forget(p.email)}
              className="absolute -right-1 -top-1 h-5 w-5 rounded-full bg-zinc-700 text-white text-xs">×</button>
          )}
        </div>
      ))}
    </div>
    <button type="button" className="mt-3 text-sm text-zinc-600 hover:underline"
      onClick={() => setShowForm(true)}>{tAuth('useAnotherAccount')}</button>
  </div>
)}
```

- Wrap the existing `<Form>...</Form>` so it renders only when `!(profiles.length > 0) || showForm` (use a conditional / `hidden` attribute; tests assert `not.toBeVisible()` so `hidden` is fine).
- Render the modal: `{activeChip && <QuickLoginModal profile={activeChip} onClose={() => setActiveChip(null)} />}`.

- [ ] **Step 5: Run test + build + lint**

Run: `cd frontend && pnpm run test:run Login && pnpm run build && pnpm run lint` → PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/auth/Login.tsx frontend/src/pages/auth/Login.quickaccess.test.tsx \
  frontend/src/locales/es/auth.ts frontend/src/locales/en/auth.ts
git commit -m "feat(frontend): quick-access chips on the login screen"
```

---

## Task 9: `QuickLoginModal` (PIN entry + password fallback)

**Files:**
- Create: `frontend/src/pages/auth/QuickLoginModal.tsx`, `frontend/src/pages/auth/QuickLoginModal.test.tsx`
- Modify: `frontend/src/locales/es/auth.ts`, `frontend/src/locales/en/auth.ts`

**Interfaces:**
- Consumes: `authService.loginPin` / `authService.login` (Task 5), `navigateForRole` (Task 7), `useQuickAccessStore.remember` (Task 6), `useAuthStore.setAuth`.
- Props: `{ profile: QuickAccessProfile; onClose: () => void }`.

- [ ] **Step 1: Add i18n keys (BOTH locales)**

```ts
  quickLoginPinLabel: 'PIN',
  quickLoginPinPlaceholder: 'PIN de 4 a 6 dígitos',   // EN: '4 to 6 digit PIN'
  quickLoginPinIncorrect: 'PIN incorrecto',            // EN: 'Incorrect PIN'
  quickLoginPreferPassword: 'Prefiero mi contraseña',  // EN: 'Use my password instead'
  quickLoginPinNotSetHint: 'No tienes un PIN configurado. Ingresa tu contraseña.',
  quickLoginPinLockedHint: 'Demasiados intentos. Ingresa tu contraseña.',
  quickLoginSubmit: 'Entrar',                          // EN: 'Sign in'
```

- [ ] **Step 2: Write the failing test**

`QuickLoginModal.test.tsx`:

```tsx
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QuickLoginModal } from '@/pages/auth/QuickLoginModal'
import { authService } from '@/lib/api'

vi.mock('@/lib/api', async (orig) => {
  const actual = await orig<typeof import('@/lib/api')>()
  return { ...actual, authService: { login: vi.fn(), loginPin: vi.fn() } }
})

const profile = { email: 'juan@x.com', name: 'Juan Perez', role: 'WAITER', initials: 'JP', colorSeed: 1, lastUsedAt: 1 }
const renderModal = () =>
  render(<MemoryRouter><QuickLoginModal profile={profile} onClose={vi.fn()} /></MemoryRouter>)

describe('QuickLoginModal', () => {
  beforeEach(() => vi.clearAllMocks())

  test('submits the PIN to loginPin', async () => {
    ;(authService.loginPin as vi.Mock).mockResolvedValue({ token: 't', role: 'WAITER', name: 'Juan Perez' })
    renderModal()
    fireEvent.change(screen.getByLabelText('quickLoginPinLabel'), { target: { value: '1234' } })
    fireEvent.click(screen.getByText('quickLoginSubmit'))
    await waitFor(() =>
      expect(authService.loginPin).toHaveBeenCalledWith({ email: 'juan@x.com', pin: '1234' }))
  })

  test('swaps to password on 409 PIN_NOT_SET', async () => {
    ;(authService.loginPin as vi.Mock).mockRejectedValue({
      isAxiosError: true, response: { status: 409, data: { code: 'PIN_NOT_SET' } },
    })
    renderModal()
    fireEvent.change(screen.getByLabelText('quickLoginPinLabel'), { target: { value: '1234' } })
    fireEvent.click(screen.getByText('quickLoginSubmit'))
    await waitFor(() =>
      expect(screen.getByText('quickLoginPinNotSetHint')).toBeVisible())
    expect(screen.getByLabelText('passwordPlaceholder')).toBeVisible()
  })

  test('"prefer password" link swaps input to password', () => {
    renderModal()
    fireEvent.click(screen.getByText('quickLoginPreferPassword'))
    expect(screen.getByLabelText('passwordPlaceholder')).toBeVisible()
  })
})
```

Match `axios.isAxiosError` handling to the rest of the codebase (`import axios from 'axios'`, `axios.isAxiosError(err)`). The mock rejections carry `isAxiosError: true` so `axios.isAxiosError` returns true.

- [ ] **Step 3: Run, expect FAIL**

Run: `cd frontend && pnpm run test:run QuickLoginModal` → FAIL.

- [ ] **Step 4: Implement**

`QuickLoginModal.tsx` — use the shared `Dialog` primitive (as `ChargeTableModal` does), a single controlled input that is either PIN or password based on `mode` state:

```tsx
import { useState } from 'react'
import axios from 'axios'
import toast from 'react-hot-toast'
import { useNavigate } from 'react-router-dom'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { authService } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { useQuickAccessStore, type QuickAccessProfile } from '@/store/quickAccessStore'
import { navigateForRole } from '@/pages/auth/navigateForRole'
import { useTranslation } from '@/lib/i18n'

export const QuickLoginModal = ({ profile, onClose }: { profile: QuickAccessProfile; onClose: () => void }) => {
  const { t: tAuth } = useTranslation('auth')
  const navigate = useNavigate()
  const { setAuth } = useAuthStore()
  const { remember } = useQuickAccessStore()
  const [mode, setMode] = useState<'pin' | 'password'>('pin')
  const [value, setValue] = useState('')
  const [hint, setHint] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    setBusy(true); setError(null)
    try {
      const res = mode === 'pin'
        ? await authService.loginPin({ email: profile.email, pin: value })
        : await authService.login({ email: profile.email, password: value })
      setAuth(res)
      remember({ email: profile.email, name: res.name ?? profile.name, role: res.role ?? profile.role })
      toast.success(tAuth('loginSuccessToast'))
      onClose()
      await navigateForRole(res, navigate, { tAuth })
    } catch (err) {
      const status = axios.isAxiosError(err) ? err.response?.status : undefined
      const code = axios.isAxiosError(err) ? (err.response?.data as { code?: string })?.code : undefined
      if (mode === 'pin' && (code === 'PIN_NOT_SET' || status === 409)) {
        setMode('password'); setValue(''); setHint(tAuth('quickLoginPinNotSetHint'))
      } else if (mode === 'pin' && (code === 'PIN_LOCKED' || status === 423)) {
        setMode('password'); setValue(''); setHint(tAuth('quickLoginPinLockedHint'))
      } else if (mode === 'pin') {
        setError(tAuth('quickLoginPinIncorrect'))
      } else {
        setError(tAuth('unauthorizedToast'))
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-sm rounded-3xl p-6">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-full text-white font-bold"
              style={{ backgroundColor: `hsl(${profile.colorSeed} 55% 45%)` }}>{profile.initials}</span>
            <span className="flex flex-col">
              <span className="text-base font-semibold">{profile.name}</span>
              <span className="text-xs text-zinc-400">{profile.email}</span>
            </span>
          </DialogTitle>
        </DialogHeader>
        {hint && <p className="text-sm text-amber-600">{hint}</p>}
        <form onSubmit={(e) => { e.preventDefault(); void submit() }} className="flex flex-col gap-3">
          <label htmlFor="quicklogin-field" className="text-sm font-medium">
            {mode === 'pin' ? tAuth('quickLoginPinLabel') : tAuth('passwordPlaceholder')}
          </label>
          <Input id="quicklogin-field"
            aria-label={mode === 'pin' ? tAuth('quickLoginPinLabel') : tAuth('passwordPlaceholder')}
            type={mode === 'pin' ? 'text' : 'password'}
            inputMode={mode === 'pin' ? 'numeric' : undefined}
            maxLength={mode === 'pin' ? 6 : undefined}
            autoFocus value={value}
            onChange={(e) => setValue(mode === 'pin' ? e.target.value.replace(/\D/g, '') : e.target.value)}
            placeholder={mode === 'pin' ? tAuth('quickLoginPinPlaceholder') : tAuth('passwordPlaceholder')} />
          {error && <p className="text-sm text-red-600">{error}</p>}
          <Button type="submit" disabled={busy || value.length < 4}>
            {tAuth('quickLoginSubmit')}
          </Button>
          {mode === 'pin' && (
            <button type="button" className="text-xs text-zinc-500 hover:underline"
              onClick={() => { setMode('password'); setValue(''); setHint(null); setError(null) }}>
              {tAuth('quickLoginPreferPassword')}
            </button>
          )}
        </form>
      </DialogContent>
    </Dialog>
  )
}
```

If Task 8 shipped a `null` stub import, this file replaces it (same path/name).

- [ ] **Step 5: Run test + build + lint**

Run: `cd frontend && pnpm run test:run QuickLoginModal && pnpm run build && pnpm run lint` → PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/auth/QuickLoginModal.tsx frontend/src/pages/auth/QuickLoginModal.test.tsx \
  frontend/src/locales/es/auth.ts frontend/src/locales/en/auth.ts
git commit -m "feat(frontend): quick-login pin modal with password fallback"
```

---

## Task 10: `SetPinPrompt` — post-login nudge + layout header entry

**Files:**
- Create: `frontend/src/pages/auth/SetPinPrompt.tsx`
- Modify: `frontend/src/pages/auth/QuickLoginModal.tsx` (show nudge after a password-path success), `frontend/src/layouts/WaiterLayout.tsx`, `frontend/src/layouts/AdminLayout.tsx`, `frontend/src/locales/es/auth.ts`, `frontend/src/locales/en/auth.ts`
- Test: `frontend/src/pages/auth/SetPinPrompt.test.tsx` (create)

**Interfaces:**
- Consumes: `authService.setPin` (Task 5), `useQuickAccessStore.dismissPinPrompt` / `pinDismissed` (Task 6).
- Props: `{ email: string; defaultPassword?: string; onDone: () => void }`.

- [ ] **Step 1: Add i18n keys (BOTH locales)**

```ts
  setPinCtaTitle: 'Crea un PIN para entrar más rápido',   // EN: 'Create a PIN for faster sign-in'
  setPinCurrentPassword: 'Contraseña actual',
  setPinNewPin: 'Nuevo PIN (4-6 dígitos)',
  setPinConfirm: 'Confirmar PIN',
  setPinMismatch: 'Los PIN no coinciden',
  setPinSavedToast: 'PIN guardado',
  setPinNotNow: 'Ahora no',
  setPinSave: 'Guardar PIN',
  setPinMenuItem: 'Configurar PIN de acceso rápido',       // header menu label
```

- [ ] **Step 2: Write the failing test**

`SetPinPrompt.test.tsx`:

```tsx
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { SetPinPrompt } from '@/pages/auth/SetPinPrompt'
import { authService } from '@/lib/api'

vi.mock('@/lib/api', async (orig) => {
  const actual = await orig<typeof import('@/lib/api')>()
  return { ...actual, authService: { setPin: vi.fn() } }
})

describe('SetPinPrompt', () => {
  beforeEach(() => vi.clearAllMocks())

  test('rejects mismatched PINs without calling the API', async () => {
    render(<SetPinPrompt email="a@x.com" onDone={vi.fn()} />)
    fireEvent.change(screen.getByLabelText('setPinCurrentPassword'), { target: { value: 'pw' } })
    fireEvent.change(screen.getByLabelText('setPinNewPin'), { target: { value: '1234' } })
    fireEvent.change(screen.getByLabelText('setPinConfirm'), { target: { value: '5678' } })
    fireEvent.click(screen.getByText('setPinSave'))
    expect(await screen.findByText('setPinMismatch')).toBeVisible()
    expect(authService.setPin).not.toHaveBeenCalled()
  })

  test('submits when PINs match', async () => {
    ;(authService.setPin as vi.Mock).mockResolvedValue(undefined)
    const onDone = vi.fn()
    render(<SetPinPrompt email="a@x.com" onDone={onDone} />)
    fireEvent.change(screen.getByLabelText('setPinCurrentPassword'), { target: { value: 'pw' } })
    fireEvent.change(screen.getByLabelText('setPinNewPin'), { target: { value: '1234' } })
    fireEvent.change(screen.getByLabelText('setPinConfirm'), { target: { value: '1234' } })
    fireEvent.click(screen.getByText('setPinSave'))
    await waitFor(() =>
      expect(authService.setPin).toHaveBeenCalledWith({ currentPassword: 'pw', pin: '1234' }))
    await waitFor(() => expect(onDone).toHaveBeenCalled())
  })
})
```

- [ ] **Step 3: Run, expect FAIL**

Run: `cd frontend && pnpm run test:run SetPinPrompt` → FAIL.

- [ ] **Step 4: Implement `SetPinPrompt.tsx`**

A `Dialog` with three fields (`currentPassword`, `pin`, `confirmPin`), each with a matching `aria-label`. Client-side: both PINs `^\d{4,6}$` and equal; on submit call `authService.setPin({ currentPassword, pin })`, `toast.success(tAuth('setPinSavedToast'))`, `onDone()`. "Ahora no" button calls `onDone()`. `defaultPassword` pre-fills `currentPassword` when provided.

- [ ] **Step 5: Wire the nudge into `QuickLoginModal`**

After a successful **password-path** login in `QuickLoginModal.submit` (i.e. `mode === 'password'`), instead of closing immediately, check `useQuickAccessStore.getState().pinDismissed.includes(profile.email)`. If not dismissed, set a local `showSetPin` state that renders `<SetPinPrompt email={profile.email} defaultPassword={value} onDone={() => { useQuickAccessStore.getState().dismissPinPrompt(profile.email); onClose() }} />` instead of navigating first. Navigation (`navigateForRole`) then runs from `onDone`. Keep the PIN-path behavior unchanged (straight to navigate).

- [ ] **Step 6: Add the header menu entry**

In `WaiterLayout.tsx` and `AdminLayout.tsx` headers, add a small button/menu item labelled `tAuth('setPinMenuItem')` that opens `<SetPinPrompt email={useAuthStore.getState().name ? /* email */ : ''} onDone={close} />`. NOTE: `authStore` holds `name`, not `email`. If the current user's email is not in `authStore`, source it from the most-recent `quickAccessStore` profile whose `role` matches, or add `email` to what `setAuth` persists (out of scope — prefer the quickAccess lookup, and if neither is available, hide the menu item). Keep this addition minimal and consistent with each layout's existing header controls.

- [ ] **Step 7: Run tests + build + lint**

Run: `cd frontend && pnpm run test:run SetPinPrompt QuickLoginModal && pnpm run build && pnpm run lint` → PASS.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/auth/SetPinPrompt.tsx frontend/src/pages/auth/SetPinPrompt.test.tsx \
  frontend/src/pages/auth/QuickLoginModal.tsx \
  frontend/src/layouts/WaiterLayout.tsx frontend/src/layouts/AdminLayout.tsx \
  frontend/src/locales/es/auth.ts frontend/src/locales/en/auth.ts
git commit -m "feat(frontend): set-pin prompt after password login and in layout headers"
```

---

## Task 11: Report + PROGRESS.md + full verification

**Files:**
- Create: `reports/NNN-feat-waiter-quick-login.md` (next free number — check `ls reports/ | sort -V | tail -1`)
- Modify: `PROGRESS.md`

- [ ] **Step 1: Backend + frontend full verification**

Run: `cd backend && ./mvnw test` → PASS (record the count).
Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run` → PASS (record counts).

- [ ] **Step 2: Write the report**

Follow `CLAUDE.md §4` structure: Identification (report number, task `B`, predecessor report 335), Objective, Modified Files (every path), What Changed, Why It Changed, Verification (the recorded command outputs).

- [ ] **Step 3: Update `PROGRESS.md`**

- Flip `- [ ] B: waiter quick-login ...` → `- [x] B: ... (report NNN)` in the "Waiter quick-login + table-detail actions" section.
- Add a "Last Completed Task (report NNN, ...)" bullet at the top of Current Execution State summarizing the endpoints and the device store.
- Keep the file under its size budget — overwrite an obsolete older bullet if needed.

- [ ] **Step 4: Commit**

```bash
git add reports/NNN-feat-waiter-quick-login.md PROGRESS.md
git commit -m "docs: report and progress for waiter quick-login"
```

---

## Self-Review Notes (author)

- **Spec coverage:** §4.1 → Task 1. §4.2 `login/pin` → Task 3; `POST/DELETE pin` → Task 4 (moved to `/account/pin`, noted). §4.3 guard → Task 2. §4.4 error contract → Task 3 (handlers) + Task 4. §5.1 store → Task 6. §5.2 `navigateForRole` → Task 7. §5.3 `Login.tsx` → Task 8. §5.4 modal → Task 9. §5.5 `SetPinPrompt` → Task 10. §5.6 api → Task 5. §8 i18n → distributed across Tasks 8–10 (keys added with their consumers to satisfy `satisfies` parity). §9 security: rate-limit → Task 3 Step 7; BCrypt/`@JsonIgnore` → Task 1; current-password gate → Task 4.
- **Deviation:** spec says `POST /auth/pin`; plan uses `/account/pin` so the security filter chain (not `@PreAuthorize`) enforces authn, since `/auth/**` is `permitAll`. Frontend `authService.setPin`/`clearPin` target `/account/pin` accordingly.
- **Type consistency:** `QuickAccessProfile` shape identical in Tasks 6/8/9/10. `navigateForRole(response, navigate, { tAuth })` signature identical in Tasks 7/9. `authService.loginPin({ email, pin })` identical in Tasks 5/9.
</content>
