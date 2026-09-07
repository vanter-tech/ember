# EMB-FEAT-HUB — Ember Hub: waiter-managed seats — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Ember Hub build fully waiter-driven — the waiter opens a table with named, account-less seats and manages them, with no customer-facing flow in the Hub bundle.

**Architecture:** Backend gains name-only `Participant` seats (`userId = null`) seeded at session creation plus three WAITER-only add/rename/remove endpoints; a null-safety sweep of participant matching; one new WS event. Frontend forks by the existing `isHubBuild` signal (`import.meta.env.BASE_URL !== '/'`): the customer subsystem is gated out of the router, the assign-table modal collects seat names instead of rendering a QR, the table-detail view gains seat controls, and the loyalty admin UI is hidden.

**Tech Stack:** Java 17 / Spring Boot 3.5.14, Spring Data JPA (Hibernate JSON columns), JUnit 5 + Mockito, `@WebMvcTest`, `@SpringBootTest @AutoConfigureMockMvc`. React 19 + TS, Vite, react-router-dom 7, TanStack Query 5, Zustand, shadcn/ui, lucide-react, Vitest + Testing Library, i18n (`useTranslation('waiter'|'admin')`).

**Spec:** `docs/superpowers/specs/2026-09-07-hub-waiter-managed-seats-design.md`

## Global Constraints

- **Event bus is Spring `ApplicationEventPublisher` / `@EventListener` only.** Never introduce Kafka.
- **No DB migration.** `Participant` is a JSON column; `userId` is already nullable. Do not add a Flyway script.
- **`GlobalExceptionHandler` mapping:** `IllegalArgumentException` + `IllegalStateException` → 409; `MethodArgumentNotValidException` (from `@Valid` / `@NotBlank` / `@Size`) → 400; `AccessDeniedException` → 403; `ResourceNotFoundException` → 404.
- **Backend test DB:** H2, `ddl-auto=create-drop`, `spring.flyway.enabled=false`. Every `@DataJpaTest` must `@Import(com.vanter.ember.config.TenantIdentifierResolver.class)` — this plan adds none, but integration tests use `@SpringBootTest`.
- **Canonical verification commands** (do not substitute `mvn` or bare `tsc`): backend `cd backend && ./mvnw test`; frontend `cd frontend && pnpm run build` then `pnpm run lint` then `pnpm run test:run`.
- **Commits:** Conventional Commits, lowercase, no `Co-authored-by` / `Signed-off-by` / AI signatures. Scoped `git add <paths>` only — never `git add -A`/`.`. One squashed atomic commit per task during execution; T10 squashes the whole effort into one.
- **Every new Cloud-visible frontend path must be behind `isHubBuild`.** Cloud rendering stays byte-identical.
- **Branch:** `spec/hub-waiter-seats` (already cut from `origin/main`). Depends on PR #97 (guest-join) — if it has merged, rebase onto `main` first so `LoyaltyAccrualListener`'s null-`userId` guard and `ParticipantJoined.guest` are present.

---

### Task 1: Seed name-only seats at session creation

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/session/dto/CreateSessionRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/service/SessionService.java` (`createSession`, ~127-155)
- Modify: `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java` (`createSession`, ~50-56)
- Test: `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java`
- Test: `backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java`

**Interfaces:**
- Consumes: `CreateSessionRequest(UUID tableId, int maxParticipants)`, `Participant.builder().userId(...).name(...).build()`, `SessionService.createSession(UUID, String, int)`.
- Produces: `CreateSessionRequest(UUID tableId, int maxParticipants, List<String> seatNames)` (nullable third component); `SessionService.createSession(UUID tableId, String waiterId, int maxParticipants, List<String> seatNames)` — seeds `maxParticipants` `Participant{userId:null}` rows when `seatNames` is non-empty, seeds none when null/empty; throws `IllegalArgumentException` when `seatNames.size() > maxParticipants` or provided names collide.

- [ ] **Step 1: Write the failing test** — add to `SessionServiceTest` (uses Mockito; `sessionRepository.save` is stubbed to return its argument):

```java
@Test
void createSession_noSeatNames_seedsNoParticipants() {
    when(diningTableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
    when(sessionRepository.findByTenantIdAndTableIdAndStatus(any(), any(), any())).thenReturn(List.of());
    when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

    Session s = service.createSession(TABLE_ID, "waiter@x.com", 4, null);

    assertThat(s.getParticipants()).isEmpty();
}

@Test
void createSession_partialSeatNames_fillsRestWithAsientoN() {
    when(diningTableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
    when(sessionRepository.findByTenantIdAndTableIdAndStatus(any(), any(), any())).thenReturn(List.of());
    when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

    Session s = service.createSession(TABLE_ID, "waiter@x.com", 4, List.of("Ana", "Beto"));

    assertThat(s.getParticipants()).extracting(Participant::getName)
            .containsExactly("Ana", "Beto", "Asiento 3", "Asiento 4");
    assertThat(s.getParticipants()).allSatisfy(p -> assertThat(p.getUserId()).isNull());
}

@Test
void createSession_moreNamesThanCapacity_throws() {
    when(diningTableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
    when(sessionRepository.findByTenantIdAndTableIdAndStatus(any(), any(), any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.createSession(TABLE_ID, "w@x.com", 2, List.of("A", "B", "C")))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void createSession_duplicateProvidedNames_throws() {
    when(diningTableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
    when(sessionRepository.findByTenantIdAndTableIdAndStatus(any(), any(), any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.createSession(TABLE_ID, "w@x.com", 3, List.of("Ana", "Ana")))
            .isInstanceOf(IllegalArgumentException.class);
}
```

Check the existing `SessionServiceTest` for the real field names (`service`, `TABLE_ID`, `table`, mock names). If the class stubs a `TenantContextHolder` tenant, keep that setup. Match the existing `@BeforeEach`.

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && ./mvnw test -Dtest=SessionServiceTest`
Expected: the 4 new tests fail to compile (`createSession` has 3 args).

- [ ] **Step 3: Update `CreateSessionRequest`**

```java
package com.vanter.ember.session.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateSessionRequest(
        @NotNull UUID tableId,
        @Min(1) int maxParticipants,
        List<@Size(max = 50) String> seatNames) {
}
```

Keep whatever annotations the current file has on `tableId` / `maxParticipants`; only add the `seatNames` component.

- [ ] **Step 4: Implement seeding in `SessionService.createSession`**

Change the signature and add seeding before `eventPublisher.publishEvent(new SessionOpened(...))`. Insert a private helper:

```java
public Session createSession(UUID tableId, String waiterId, int maxParticipants, List<String> seatNames) {
    UUID tenantId = TenantContextHolder.requireTenantId();

    var table = diningTableRepository.findById(tableId)
            .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + tableId));

    var OpenSession = sessionRepository.findByTenantIdAndTableIdAndStatus(
            tenantId, tableId, SessionStatus.OPEN);
    if (!OpenSession.isEmpty()) {
        throw new IllegalStateException("Table " + table.getTableNumber() + " is already occupied");
    }

    Session session = sessionRepository.save(Session.builder()
            .tenantId(tenantId)
            .tableId(tableId)
            .waiterId(waiterId)
            .status(SessionStatus.OPEN)
            .maxParticipants(maxParticipants)
            .createdAt(LocalDateTime.now())
            .joinCode(generateJoinCode())
            .build());

    seedSeats(session, maxParticipants, seatNames);
    if (!session.getParticipants().isEmpty()) {
        session = sessionRepository.save(session);
    }

    eventPublisher.publishEvent(
            new SessionOpened(tenantId, session.getId(), tableId, table.getTableNumber()));
    return session;
}

/**
 * Hub tables are opened with a fixed set of account-less "seats" ({@link Participant#getUserId()}
 * null). Blank/missing slots up to {@code capacity} are auto-named "Asiento N"; provided names
 * must be unique. Cloud passes {@code seatNames == null} and no seats are seeded — customers add
 * themselves on join.
 */
private void seedSeats(Session session, int capacity, List<String> seatNames) {
    if (seatNames == null || seatNames.isEmpty()) {
        return;
    }
    if (seatNames.size() > capacity) {
        throw new IllegalArgumentException(
                "More seat names (" + seatNames.size() + ") than seats (" + capacity + ")");
    }
    List<String> provided = seatNames.stream()
            .map(n -> n == null ? "" : n.trim())
            .toList();
    List<String> nonBlank = provided.stream().filter(n -> !n.isBlank()).toList();
    if (nonBlank.size() != nonBlank.stream().distinct().count()) {
        throw new IllegalArgumentException("Duplicate seat names");
    }
    java.util.Set<String> taken = new java.util.HashSet<>(nonBlank);
    for (int i = 0; i < capacity; i++) {
        String name = i < provided.size() && !provided.get(i).isBlank()
                ? provided.get(i)
                : nextAutoSeatName(taken);
        taken.add(name);
        session.getParticipants().add(Participant.builder().userId(null).name(name).build());
    }
}

private String nextAutoSeatName(java.util.Set<String> taken) {
    int n = 1;
    while (taken.contains("Asiento " + n)) {
        n++;
    }
    return "Asiento " + n;
}
```

- [ ] **Step 5: Wire the controller**

`SessionController.createSession`:

```java
Session savedSession = sessionService.createSession(
        request.tableId(), authentication.getName(), request.maxParticipants(), request.seatNames());
```

- [ ] **Step 6: Fix other `createSession` call sites**

Run: `cd backend && grep -rn "\.createSession(" src/main src/test`
Any call with 3 args (tests, other services) → add `, null` (or `, List.of(...)` in a test that wants seats). Compile: `./mvnw -q compile test-compile`.

- [ ] **Step 7: Add `SessionControllerTest` coverage**

In the existing `@WebMvcTest(SessionController.class)` class, add:

```java
@Test
void createSession_passesSeatNamesThrough() throws Exception {
    when(sessionService.createSession(any(), any(), anyInt(), any()))
            .thenReturn(Session.builder().id("s1").joinCode("ABCDE").build());

    mockMvc.perform(post("/sessions").with(user("w@x.com").roles("WAITER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"tableId":"%s","maxParticipants":3,"seatNames":["Ana","Beto"]}
                """.formatted(UUID.randomUUID())))
            .andExpect(status().isCreated());

    verify(sessionService).createSession(any(), eq("w@x.com"), eq(3), eq(List.of("Ana", "Beto")));
}
```

Match the class's existing mock-mvc auth helper (`.with(user(...))` / `@WithMockUser` — copy whichever the file already uses).

- [ ] **Step 8: Run tests**

Run: `cd backend && ./mvnw test -Dtest=SessionServiceTest,SessionControllerTest`
Expected: PASS.

- [ ] **Step 9: Full suite + commit**

Run: `cd backend && ./mvnw test`
Expected: PASS (baseline was 1167/1167 pre-effort; count grows).

```bash
git add backend/src/main/java/com/vanter/ember/session/dto/CreateSessionRequest.java \
        backend/src/main/java/com/vanter/ember/session/service/SessionService.java \
        backend/src/main/java/com/vanter/ember/session/controller/SessionController.java \
        backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java \
        backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java
git commit -m "feat(hub): seed name-only seats at session creation"
```

---

### Task 2: Null-safety sweep of participant matching + loyalty join guard

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/session/service/SessionService.java` (lines ~173, 211, 285, 611, 645, 692)
- Modify: `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java` (~line 69)
- Modify: `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListener.java`
- Test: `backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListenerTest.java`
- Test: `backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java` (or an existing session integration test)

**Interfaces:**
- Consumes: `Participant.getUserId()` (now nullable), `ParticipantJoined.userId()`, `ParticipantJoined.guest()`.
- Produces: no signature changes — every `p.getUserId().equals(x)` on a participant stream becomes null-safe; `LoyaltyAccountJoinListener` returns early when `event.userId() == null`.

- [ ] **Step 1: Write the failing test** — `LoyaltyAccountJoinListenerTest`:

```java
@Test
void handleParticipantJoined_nameOnlySeat_doesNotCreateAccount() {
    listener.handleParticipantJoined(new ParticipantJoined(
            UUID.randomUUID(), "sess-1", null, "Asiento 1", false));

    verifyNoInteractions(loyaltyAccountService);
}
```

(If the existing guest test already does `verifyNoInteractions`, mirror it.)

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && ./mvnw test -Dtest=LoyaltyAccountJoinListenerTest`
Expected: FAIL — `NullPointerException` or an unwanted `findOrCreate(tenantId, null)` call.

- [ ] **Step 3: Guard `LoyaltyAccountJoinListener`**

```java
@EventListener
public void handleParticipantJoined(ParticipantJoined event) {
    if (event.guest()) { return; }
    if (event.userId() == null) { return; }
    loyaltyAccountService.findOrCreate(event.tenantId(), event.userId());
}
```

- [ ] **Step 4: Null-safe the participant matches**

In `SessionService.java`, replace each `p.getUserId().equals(<expr>)` where the participant may be name-only with `java.util.Objects.equals(p.getUserId(), <expr>)` (add `import java.util.Objects;`). Sites: `joinSession` (`alreadyJoined`), `joinSessionCode` (`alreadyJoin`), `addItem` (participant lookup), `isParticipant`, `leaveSession` (`leaver` filter), `resumeSession` (`isParticipant` filter). Example:

```java
// joinSession
boolean alreadyJoined = session.getParticipants().stream()
        .anyMatch(p -> Objects.equals(p.getUserId(), user.getId()));
```

In `SessionController.getSession`:

```java
boolean isParticipant = session.participants().stream()
        .anyMatch(p -> java.util.Objects.equals(p.userId(), requesterId));
```

- [ ] **Step 5: Add the no-NPE regression test**

Prefer an existing `@SpringBootTest` session test; otherwise add to `SessionControllerTest` a slice test that stubs `sessionService.getSessionDetails` to return a DTO whose participant list contains a `userId == null` entry and asserts `GET /sessions/{id}` as a WAITER returns 200. If a real integration test class exists (`E2E*`, `Session*IntegrationTest`), add there instead — create a session with `seatNames`, then `GET /sessions/{id}` as the waiter, expect 200.

- [ ] **Step 6: Run tests**

Run: `cd backend && ./mvnw test -Dtest=LoyaltyAccountJoinListenerTest,SessionServiceTest,SessionControllerTest`
Expected: PASS.

- [ ] **Step 7: Full suite + commit**

Run: `cd backend && ./mvnw test`

```bash
git add backend/src/main/java/com/vanter/ember/session/service/SessionService.java \
        backend/src/main/java/com/vanter/ember/session/controller/SessionController.java \
        backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListener.java \
        backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListenerTest.java \
        backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java
git commit -m "fix(session): null-safe participant matching for name-only seats"
```

---

### Task 3: `ParticipantRenamed` event + WebSocket wiring

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/session/event/ParticipantRenamed.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/listener/SessionWebSocketListener.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/listener/WaiterWebSocketListener.java`
- Test: `backend/src/test/java/com/vanter/ember/session/listener/SessionWebSocketListenerTest.java` (create if absent) or extend an existing listener test

**Interfaces:**
- Produces: `ParticipantRenamed(String type, UUID tenantId, String sessionId, String oldName, String newName)` with a 4-arg convenience constructor defaulting `type = "PARTICIPANT_RENAMED"`. Broadcast to `/topic/session/{sessionId}` and `/topic/waiter/{tenantId}`. Consumed by Task 5 (publishes it) and Task 8 (frontend handles it).

- [ ] **Step 1: Write the failing test**

If a `WaiterWebSocketListenerTest` / `SessionWebSocketListenerTest` exists, add:

```java
@Test
void onParticipantRenamed_broadcastsToWaiterTopic() {
    UUID tenant = UUID.randomUUID();
    var event = new ParticipantRenamed(tenant, "s1", "Asiento 1", "Ana");

    listener.onParticipantRenamed(event);

    verify(messagingTemplate).convertAndSend("/topic/waiter/" + tenant, (Object) event);
}
```

Match the existing `verify(messagingTemplate).convertAndSend(...)` argument style in the file (cast or not).

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && ./mvnw test -Dtest=WaiterWebSocketListenerTest`
Expected: FAIL to compile (`ParticipantRenamed` missing).

- [ ] **Step 3: Create the event**

```java
package com.vanter.ember.session.event;

import java.util.UUID;

public record ParticipantRenamed(
        String type, UUID tenantId, String sessionId, String oldName, String newName) {
    public ParticipantRenamed(UUID tenantId, String sessionId, String oldName, String newName) {
        this("PARTICIPANT_RENAMED", tenantId, sessionId, oldName, newName);
    }
}
```

- [ ] **Step 4: Wire both listeners**

`SessionWebSocketListener`:

```java
@EventListener
public void onParticipantRenamed(ParticipantRenamed event) {
    messagingTemplate.convertAndSend("/topic/session/" + event.sessionId(), event);
}
```

`WaiterWebSocketListener`:

```java
@EventListener
public void onParticipantRenamed(ParticipantRenamed event) {
    messagingTemplate.convertAndSend("/topic/waiter/" + event.tenantId(), event);
}
```

- [ ] **Step 5: Run tests**

Run: `cd backend && ./mvnw test -Dtest=*WebSocketListenerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/session/event/ParticipantRenamed.java \
        backend/src/main/java/com/vanter/ember/session/listener/SessionWebSocketListener.java \
        backend/src/main/java/com/vanter/ember/session/listener/WaiterWebSocketListener.java \
        backend/src/test/java/com/vanter/ember/session/listener/
git commit -m "feat(session): ParticipantRenamed event broadcast on session + waiter topics"
```

---

### Task 4: `POST /sessions/{id}/participants` — add a seat

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/session/dto/AddSeatRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/service/SessionService.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java`
- Test: `SessionServiceTest`, `SessionControllerTest`

**Interfaces:**
- Consumes: `SessionService.findById`, `Participant`, `nextAutoSeatName` (Task 1), `ParticipantJoined` 5-arg ctor, `sessionService.getSessionDetails`.
- Produces: `SessionService.addSeat(String sessionId, String requestingWaiter, String name) -> Session` — appends one `Participant{userId:null}`; blank name → next free `"Asiento N"`; non-blank must be unique else `IllegalArgumentException`; bumps `maxParticipants` if exceeded; publishes `ParticipantJoined(tenantId, id, null, name, false)`. Endpoint `POST /sessions/{id}/participants` (WAITER, assigned-waiter, OPEN) returning `SessionDetailResponseDto`.

- [ ] **Step 1: Write the failing test** — `SessionServiceTest`:

```java
@Test
void addSeat_blankName_picksLowestFreeAsientoN() {
    Session session = openSessionWithSeats("Ana", "Asiento 2");   // helper: builds Session, OPEN, waiterId WAITER_EMAIL
    when(sessionRepository.findByIdAndTenantId(eq("s1"), any())).thenReturn(Optional.of(session));
    when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Session out = service.addSeat("s1", WAITER_EMAIL, "  ");

    assertThat(out.getParticipants()).extracting(Participant::getName)
            .containsExactly("Ana", "Asiento 2", "Asiento 1");
    verify(eventPublisher).publishEvent(isA(ParticipantJoined.class));
}

@Test
void addSeat_duplicateName_throws() {
    Session session = openSessionWithSeats("Ana");
    when(sessionRepository.findByIdAndTenantId(eq("s1"), any())).thenReturn(Optional.of(session));

    assertThatThrownBy(() -> service.addSeat("s1", WAITER_EMAIL, "Ana"))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void addSeat_notAssignedWaiter_throws() {
    Session session = openSessionWithSeats("Ana");
    when(sessionRepository.findByIdAndTenantId(eq("s1"), any())).thenReturn(Optional.of(session));

    assertThatThrownBy(() -> service.addSeat("s1", "other@x.com", "Beto"))
            .isInstanceOf(AccessDeniedException.class);
}
```

Add the `openSessionWithSeats(String... names)` helper to the test class if not present (builds `Session.builder().id("s1").tenantId(TENANT).waiterId(WAITER_EMAIL).status(SessionStatus.OPEN).maxParticipants(Math.max(4, names.length)).participants(new ArrayList<>(...)).build()`).

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && ./mvnw test -Dtest=SessionServiceTest`
Expected: FAIL — `addSeat` undefined.

- [ ] **Step 3: Implement `SessionService.addSeat`**

```java
public Session addSeat(String sessionId, String requestingWaiter, String name) {
    Session session = findById(sessionId);
    requireAssignedWaiter(session, requestingWaiter);
    requireOpen(session);

    java.util.Set<String> taken = session.getParticipants().stream()
            .map(Participant::getName).collect(java.util.stream.Collectors.toSet());
    String seatName = name == null || name.isBlank()
            ? nextAutoSeatName(taken)
            : name.trim();
    if (taken.contains(seatName)) {
        throw new IllegalArgumentException("Seat name already in use: " + seatName);
    }

    session.getParticipants().add(Participant.builder().userId(null).name(seatName).build());
    if (session.getParticipants().size() > session.getMaxParticipants()) {
        session.setMaxParticipants(session.getParticipants().size());
    }
    Session saved = sessionRepository.save(session);
    eventPublisher.publishEvent(
            new ParticipantJoined(saved.getTenantId(), saved.getId(), null, seatName, false));
    return saved;
}

private void requireAssignedWaiter(Session session, String waiterEmail) {
    if (!session.getWaiterId().equals(waiterEmail)) {
        throw new AccessDeniedException("Only the assigned waiter can manage this table's seats");
    }
}

private void requireOpen(Session session) {
    if (session.getStatus() != SessionStatus.OPEN) {
        throw new IllegalStateException("Session is not open: " + session.getId());
    }
}
```

(If a similar `requireOpen` / waiter check already exists privately, reuse it instead of adding a duplicate.)

- [ ] **Step 4: Create `AddSeatRequest`**

```java
package com.vanter.ember.session.dto;

import jakarta.validation.constraints.Size;

public record AddSeatRequest(@Size(max = 50) String name) {
}
```

- [ ] **Step 5: Add the endpoint**

`SessionController`:

```java
@Operation(summary = "Add a name-only seat to the table (WAITER)")
@PostMapping("/{id}/participants")
@PreAuthorize("hasRole('WAITER')")
public SessionDetailResponseDto addSeat(@PathVariable String id,
                                        @Valid @RequestBody AddSeatRequest request,
                                        Authentication authentication) {
    sessionService.addSeat(id, authentication.getName(), request.name());
    return sessionService.getSessionDetails(id);
}
```

- [ ] **Step 6: `SessionControllerTest`**

```java
@Test
void addSeat_requiresWaiterRole() throws Exception {
    mockMvc.perform(post("/sessions/s1/participants").with(user("c@x.com").roles("CUSTOMER"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\"}"))
            .andExpect(status().isForbidden());
}

@Test
void addSeat_ok() throws Exception {
    when(sessionService.getSessionDetails("s1")).thenReturn(sampleDetail());
    mockMvc.perform(post("/sessions/s1/participants").with(user("w@x.com").roles("WAITER"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\"}"))
            .andExpect(status().isOk());
    verify(sessionService).addSeat("s1", "w@x.com", "Ana");
}
```

Reuse the file's existing `sampleDetail()` / DTO builder helper, or inline a minimal `SessionDetailResponseDto`.

- [ ] **Step 7: Run + commit**

Run: `cd backend && ./mvnw test -Dtest=SessionServiceTest,SessionControllerTest` then `./mvnw test`

```bash
git add backend/src/main/java/com/vanter/ember/session/dto/AddSeatRequest.java \
        backend/src/main/java/com/vanter/ember/session/service/SessionService.java \
        backend/src/main/java/com/vanter/ember/session/controller/SessionController.java \
        backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java \
        backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java
git commit -m "feat(hub): POST /sessions/{id}/participants to add a name-only seat"
```

---

### Task 5: Rename (`PATCH`) and remove (`DELETE`) a seat

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/session/dto/RenameSeatRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/service/SessionService.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java`
- Test: `SessionServiceTest`, `SessionControllerTest`
- Test: `backend/src/test/java/com/vanter/ember/session/HubSeatFlowIntegrationTest.java` (new)

**Interfaces:**
- Consumes: `billRepository.findBySessionIdAndStatusNot(id, BillStatus.VOIDED)`, `OrderItem.setParticipantName`, `SessionActivity`, `ParticipantLeft`, `DeleteItem`, `SessionClosed`, `ParticipantRenamed` (Task 3), `ParticipantLeftListener` (existing).
- Produces:
  - `SessionService.renameSeat(String sessionId, String requestingWaiter, String from, String to) -> Session` — requires seat `from` exists, `to` non-blank/unique, session OPEN, **no non-voided bill** (else `IllegalStateException`); rewrites `OrderItem.participantName` and `SessionActivity.participantName` `from`→`to`; publishes `ParticipantRenamed`.
  - `SessionService.removeSeat(String sessionId, String requestingWaiter, String name) -> Session` — the named participant must exist and be `userId == null` (else `AccessDeniedException`); discards its DRAFT items (`DeleteItem` each), removes the seat; if no participants and no billable items → `SessionClosed`; else publishes `ParticipantLeft(tenantId, id, null, name)`.
  - Endpoints `PATCH /sessions/{id}/participants` and `DELETE /sessions/{id}/participants/{name}` (WAITER), both returning `SessionDetailResponseDto`.

- [ ] **Step 1: Write the failing tests** — `SessionServiceTest`:

```java
@Test
void renameSeat_rewritesItemAndActivityNames() {
    Session session = openSessionWithSeats("Ana", "Beto");
    session.getItems().add(OrderItem.builder().id("i1").name("Pizza")
            .participantName("Ana").status(OrderItemStatus.PENDING).build());
    session.getActivityLog().add(SessionActivity.builder()
            .type(SessionActivity.Type.ITEM_SENT).itemName("Pizza").participantName("Ana")
            .timestamp(LocalDateTime.now()).build());
    when(sessionRepository.findByIdAndTenantId(eq("s1"), any())).thenReturn(Optional.of(session));
    when(billRepository.findBySessionIdAndStatusNot("s1", BillStatus.VOIDED)).thenReturn(Optional.empty());
    when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.renameSeat("s1", WAITER_EMAIL, "Ana", "Ana G.");

    assertThat(session.getItems().get(0).getParticipantName()).isEqualTo("Ana G.");
    assertThat(session.getActivityLog().get(0).getParticipantName()).isEqualTo("Ana G.");
    verify(eventPublisher).publishEvent(isA(ParticipantRenamed.class));
}

@Test
void renameSeat_billExists_throws() {
    Session session = openSessionWithSeats("Ana");
    when(sessionRepository.findByIdAndTenantId(eq("s1"), any())).thenReturn(Optional.of(session));
    when(billRepository.findBySessionIdAndStatusNot("s1", BillStatus.VOIDED))
            .thenReturn(Optional.of(mock(com.vanter.ember.billing.model.Bill.class)));

    assertThatThrownBy(() -> service.renameSeat("s1", WAITER_EMAIL, "Ana", "Ana G."))
            .isInstanceOf(IllegalStateException.class);
}

@Test
void removeSeat_accountBackedSeat_throws() {
    Session session = openSessionWithSeats("Ana");
    session.getParticipants().get(0).setUserId("user-123");
    when(sessionRepository.findByIdAndTenantId(eq("s1"), any())).thenReturn(Optional.of(session));

    assertThatThrownBy(() -> service.removeSeat("s1", WAITER_EMAIL, "Ana"))
            .isInstanceOf(AccessDeniedException.class);
}

@Test
void removeSeat_dropsDraftsKeepsSentPublishesParticipantLeft() {
    Session session = openSessionWithSeats("Ana", "Beto");
    session.getItems().add(OrderItem.builder().id("d1").name("Agua")
            .participantName("Ana").status(OrderItemStatus.DRAFT).build());
    session.getItems().add(OrderItem.builder().id("p1").name("Pizza")
            .participantName("Ana").status(OrderItemStatus.PENDING).build());
    when(sessionRepository.findByIdAndTenantId(eq("s1"), any())).thenReturn(Optional.of(session));
    when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.removeSeat("s1", WAITER_EMAIL, "Ana");

    assertThat(session.getParticipants()).extracting(Participant::getName).containsExactly("Beto");
    assertThat(session.getItems()).extracting(OrderItem::getId).containsExactly("p1");
    verify(eventPublisher).publishEvent(isA(ParticipantLeft.class));
}
```

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && ./mvnw test -Dtest=SessionServiceTest`
Expected: FAIL — `renameSeat` / `removeSeat` undefined.

- [ ] **Step 3: Implement `renameSeat` + `removeSeat`**

```java
public Session renameSeat(String sessionId, String requestingWaiter, String from, String to) {
    Session session = findById(sessionId);
    requireAssignedWaiter(session, requestingWaiter);
    requireOpen(session);

    String target = to == null ? "" : to.trim();
    if (target.isBlank()) {
        throw new IllegalArgumentException("New seat name is blank");
    }
    Participant seat = session.getParticipants().stream()
            .filter(p -> p.getName().equals(from)).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + from));
    boolean clashes = session.getParticipants().stream()
            .anyMatch(p -> p != seat && p.getName().equals(target));
    if (clashes) {
        throw new IllegalArgumentException("Seat name already in use: " + target);
    }
    if (billRepository.findBySessionIdAndStatusNot(sessionId, BillStatus.VOIDED).isPresent()) {
        throw new IllegalStateException("Recalculate the bill before renaming seats");
    }

    seat.setName(target);
    session.getItems().stream()
            .filter(i -> from.equals(i.getParticipantName()))
            .forEach(i -> i.setParticipantName(target));
    session.getActivityLog().stream()
            .filter(a -> from.equals(a.getParticipantName()))
            .forEach(a -> a.setParticipantName(target));

    Session saved = sessionRepository.save(session);
    eventPublisher.publishEvent(
            new ParticipantRenamed(saved.getTenantId(), saved.getId(), from, target));
    return saved;
}

public Session removeSeat(String sessionId, String requestingWaiter, String name) {
    Session session = findById(sessionId);
    requireAssignedWaiter(session, requestingWaiter);
    requireOpen(session);

    Participant seat = session.getParticipants().stream()
            .filter(p -> p.getName().equals(name)).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + name));
    if (seat.getUserId() != null) {
        throw new AccessDeniedException("This seat is bound to a real account and cannot be removed here");
    }

    List<OrderItem> discardedDrafts = session.getItems().stream()
            .filter(i -> name.equals(i.getParticipantName()) && i.getStatus() == OrderItemStatus.DRAFT)
            .toList();
    session.getItems().removeAll(discardedDrafts);
    session.getParticipants().removeIf(p -> p.getName().equals(name));

    boolean hasBillableItems = session.getItems().stream()
            .anyMatch(i -> i.getStatus() != OrderItemStatus.DRAFT);
    if (session.getParticipants().isEmpty() && !hasBillableItems) {
        session.setStatus(SessionStatus.CLOSED);
        Session closed = sessionRepository.save(session);
        eventPublisher.publishEvent(new SessionClosed(
                closed.getTenantId(), closed.getId(), closed.getTableId(), closed.getStatus()));
        return closed;
    }

    Session saved = sessionRepository.save(session);
    discardedDrafts.forEach(d -> eventPublisher.publishEvent(new DeleteItem(saved.getId(), d.getId())));
    eventPublisher.publishEvent(new ParticipantLeft(saved.getTenantId(), saved.getId(), null, name));
    return saved;
}
```

- [ ] **Step 4: `RenameSeatRequest`**

```java
package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameSeatRequest(
        @NotBlank String from,
        @NotBlank @Size(max = 50) String to) {
}
```

- [ ] **Step 5: Endpoints**

```java
@Operation(summary = "Rename a seat (WAITER) — blocked once a bill exists")
@PatchMapping("/{id}/participants")
@PreAuthorize("hasRole('WAITER')")
public SessionDetailResponseDto renameSeat(@PathVariable String id,
                                           @Valid @RequestBody RenameSeatRequest request,
                                           Authentication authentication) {
    sessionService.renameSeat(id, authentication.getName(), request.from(), request.to());
    return sessionService.getSessionDetails(id);
}

@Operation(summary = "Remove a name-only seat (WAITER)")
@DeleteMapping("/{id}/participants/{name}")
@PreAuthorize("hasRole('WAITER')")
public SessionDetailResponseDto removeSeat(@PathVariable String id,
                                           @PathVariable String name,
                                           Authentication authentication) {
    sessionService.removeSeat(id, authentication.getName(), name);
    return sessionService.getSessionDetails(id);
}
```

- [ ] **Step 6: Controller slice tests** — role-gating (403 for CUSTOMER), `@Valid` blank `to` → 400, `IllegalState` (bill exists, stubbed) → 409. Mirror Task 4's style.

- [ ] **Step 7: Integration test** — `HubSeatFlowIntegrationTest` (`@SpringBootTest @AutoConfigureMockMvc @TestPropertySource(properties = "ember.ratelimit.enabled=false")`). Seed a Restaurant + WAITER user + one `DiningTables`. Authenticate as the waiter (copy the token/`@WithMockUser` approach from `GuestJoinFlowIntegrationTest` or the nearest existing session integration test).

```
1. POST /sessions {tableId, maxParticipants:3, seatNames:["Ana","Beto"]}  -> 201, capture id
2. GET  /sessions/{id}  -> 200, participants = [Ana, Beto, Asiento 3], every userId null
3. POST /sessions/{id}/waiter-items  (menuItemId for a seeded available item, participantName "Ana")
   POST /sessions/{id}/waiter-items  (participantName "Beto")
4. PATCH /sessions/{id}/participants {from:"Ana", to:"Ana G."} -> 200
   GET /sessions/{id} -> the PENDING item formerly "Ana" now participantName "Ana G."
5. POST /billing/sessions/{id}/bill {splitMethod:"BY_CONSUMPTION"} -> splits keyed "Ana G." and "Beto"
6. PATCH /sessions/{id}/participants {from:"Beto", to:"B."} -> 409 (bill now exists)
7. DELETE /sessions/{id}/participants/Beto -> 200; GET the bill -> Beto's (UNPAID) share
   redistributed onto "Ana G." (total unchanged)
```

Use `mockMvc` + `objectMapper`. For step 5's split assertion, read the bill via the existing waiter bill endpoint (`GET /billing/sessions/{id}` or whatever `billingService.getBillState` maps to) and assert on `splits[].participantName`.

- [ ] **Step 8: Run + commit**

Run: `cd backend && ./mvnw test -Dtest=SessionServiceTest,SessionControllerTest,HubSeatFlowIntegrationTest` then `./mvnw test`

```bash
git add backend/src/main/java/com/vanter/ember/session/dto/RenameSeatRequest.java \
        backend/src/main/java/com/vanter/ember/session/service/SessionService.java \
        backend/src/main/java/com/vanter/ember/session/controller/SessionController.java \
        backend/src/test/java/com/vanter/ember/session/
git commit -m "feat(hub): rename and remove name-only seats"
```

---

### Task 6: Frontend — strip the customer subsystem from the Hub build

**Files:**
- Create: `frontend/src/lib/isHubBuild.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/pages/auth/Login.tsx` (use the shared module)
- Test: `frontend/src/App.hubBuild.test.tsx` (new)

**Interfaces:**
- Produces: `export const isHubBuild: boolean` from `@/lib/isHubBuild`. When `true`, `App.tsx` registers neither `/customer/*` nor `/menu/join`; customer page components are `lazy()`.

- [ ] **Step 1: Write the failing test** — `App.hubBuild.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { vi, describe, it, expect, afterEach } from 'vitest'

const renderAt = async (path: string) => {
  window.history.pushState({}, '', path)
  const { default: App } = await import('./App')
  render(<App />)
}

afterEach(() => { vi.unstubAllEnvs(); vi.resetModules() })

describe('Hub build routing', () => {
  it('does not mount /menu/join when isHubBuild', async () => {
    vi.stubEnv('BASE_URL', '/app/')
    await renderAt('/app/menu/join?token=abc')
    expect(await screen.findByText(/no encontrada|not found/i)).toBeInTheDocument()
  })

  it('mounts /menu/join on the cloud build', async () => {
    vi.stubEnv('BASE_URL', '/')
    await renderAt('/menu/join?token=abc')
    // MenuJoin renders its invalid-link card or the choice screen — either way, not NotFound
    expect(screen.queryByText(/página no encontrada|page not found/i)).not.toBeInTheDocument()
  })
})
```

Adjust the `NotFound` copy match to the real `NotFound` component text. If `App` reads env at module scope, `vi.resetModules()` + dynamic `import('./App')` per case (as above) is required.

- [ ] **Step 2: Run to verify failure**

Run: `cd frontend && pnpm run test:run App.hubBuild`
Expected: FAIL — `/menu/join` still renders on the Hub build.

- [ ] **Step 3: Create the shared module**

```ts
// frontend/src/lib/isHubBuild.ts
// True only in the Hub-bundled SPA (`vite build --base=/app/`, see ember-hub/build-frontend.ps1).
// Cloud/dev builds have BASE_URL "/". Same signal App.tsx uses for the router basename.
export const isHubBuild = import.meta.env.BASE_URL !== '/'
```

- [ ] **Step 4: Rework `App.tsx`**

- Add `import { isHubBuild } from '@/lib/isHubBuild'`.
- Replace the direct customer imports with lazy ones:

```tsx
const CustomerLayout = lazy(() => import('./layouts/CustomerLayout').then(m => ({ default: m.CustomerLayout })))
const Home = lazy(() => import('./pages/customer/Home').then(m => ({ default: m.Home })))
const Menu = lazy(() => import('./pages/customer/Menu').then(m => ({ default: m.Menu })))
const ComandaView = lazy(() => import('./pages/customer/ComandaView').then(m => ({ default: m.ComandaView })))
const Bill = lazy(() => import('./pages/customer/Bill').then(m => ({ default: m.Bill })))
const MenuJoin = lazy(() => import('./pages/customer/MenuJoin').then(m => ({ default: m.MenuJoin })))
```

- Wrap the customer routes and the `/menu/join` route:

```tsx
{!isHubBuild && (
  <Route path="/menu/join" element={<Suspense fallback={null}><MenuJoin /></Suspense>} />
)}
{!isHubBuild && (
  <Route element={<ProtectedRoute allowedRoles={['CUSTOMER']} />}>
    <Route path="/customer" element={<Suspense fallback={null}><CustomerLayout /></Suspense>}>
      <Route index element={<Navigate to="home" replace />} />
      <Route path="home" element={<Suspense fallback={null}><Home /></Suspense>} />
      <Route path="menu" element={<Suspense fallback={null}><Menu /></Suspense>} />
      <Route path="menu/:id/comanda" element={<Suspense fallback={null}><ComandaView /></Suspense>} />
      <Route path="menu/:id/bill" element={<Suspense fallback={null}><Bill /></Suspense>} />
    </Route>
  </Route>
)}
```

JSX note: `{cond && (<Route/>)}` as a direct child of `<Routes>` is supported by react-router 7 (it flattens fragments/false). If the build complains, wrap the two blocks in a single `{!isHubBuild && (<><Route .../><Route ...>...</Route></>)}`.

- `RoleRedirect`: add near the top `if (isHubBuild && role === 'CUSTOMER') return <Navigate to="/login" replace />`.

- [ ] **Step 5: Point `Login.tsx` at the shared module**

Delete its local `const isHubBuild = import.meta.env.BASE_URL !== '/'` and its explanatory comment; add `import { isHubBuild } from '@/lib/isHubBuild'`. No behaviour change.

- [ ] **Step 6: Run build + lint + tests**

Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run`
Expected: build clean, lint clean (pre-existing warnings only), all tests pass including `App.hubBuild`.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/isHubBuild.ts frontend/src/App.tsx frontend/src/pages/auth/Login.tsx frontend/src/App.hubBuild.test.tsx
git commit -m "feat(hub): exclude the customer subsystem from the Hub build"
```

---

### Task 7: Frontend — assign-table modal Hub variant

**Files:**
- Modify: `frontend/src/pages/waiter/components/ParticipantsQrModal.tsx`
- Modify: `frontend/src/lib/api.ts` (`SessionTableService.createSession`)
- Modify: `frontend/src/locales/es/waiter.ts`, `frontend/src/locales/en/waiter.ts` (only the keys this task needs; the rest land in T10)
- Test: `frontend/src/pages/waiter/components/ParticipantsQrModal.test.tsx` (new)

**Interfaces:**
- Consumes: `isHubBuild` (T6), `SessionTableService.createSession`, `useUIStore`, `useNavigate`.
- Produces: `SessionTableService.createSession(tableId: string, maxParticipants: number, seatNames?: string[])`. On the Hub build the modal renders `maxParticipants` name inputs (no QR), calls `createSession` with the trimmed names, and navigates to `/waiter/tables/${sessionId}`.

- [ ] **Step 1: Write the failing test** — `ParticipantsQrModal.test.tsx`:

```tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { vi, describe, it, expect, beforeEach } from 'vitest'

const navigate = vi.fn()
vi.mock('react-router-dom', async (orig) => ({ ...(await orig()), useNavigate: () => navigate }))
vi.mock('@/lib/isHubBuild', () => ({ isHubBuild: true }))
const createSession = vi.fn().mockResolvedValue({ sessionId: 's1', joinCode: 'ABCDE' })
const getQrToken = vi.fn()
vi.mock('@/lib/api', () => ({ SessionTableService: { createSession: (...a: unknown[]) => createSession(...a), getQrToken } }))
vi.mock('@/store/uiStore', () => ({
  useUIStore: () => ({ activeModal: 'PARTICIPANTS_QR', modalPayload: { tableId: 't1' }, closeModal: vi.fn() }),
}))

beforeEach(() => { createSession.mockClear(); getQrToken.mockClear(); navigate.mockClear() })

describe('ParticipantQrModal (Hub build)', () => {
  it('collects seat names and creates the session without a QR', async () => {
    const { ParticipantQrModal } = await import('./ParticipantsQrModal')
    render(<ParticipantQrModal />)

    // default count is 1 -> one seat input
    fireEvent.click(screen.getByRole('button', { name: '' })) // + button; refine selector to the plus control
    const inputs = screen.getAllByPlaceholderText(/Asiento \d/)
    fireEvent.change(inputs[0], { target: { value: 'Ana' } })

    fireEvent.click(screen.getByRole('button', { name: /abrir mesa|open table/i }))

    await waitFor(() => expect(createSession).toHaveBeenCalledWith('t1', 2, ['Ana', '']))
    expect(getQrToken).not.toHaveBeenCalled()
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/waiter/tables/s1'))
    expect(screen.queryByRole('img', { name: /qr/i })).not.toBeInTheDocument()
  })
})
```

Refine the +/- button selectors against the real markup (they render lucide `<Plus/>`/`<Minus/>` with no accessible name — consider adding `aria-label={t('addSeatLabel')}` / a `data-testid` while editing, which also improves a11y). Add a second `describe` with `vi.mock('@/lib/isHubBuild', () => ({ isHubBuild: false }))` asserting the QR still renders after `createSession` + `getQrToken`.

- [ ] **Step 2: Run to verify failure**

Run: `cd frontend && pnpm run test:run ParticipantsQrModal`
Expected: FAIL — no seat inputs, `createSession` called with 2 args.

- [ ] **Step 3: Extend `SessionTableService.createSession`**

```ts
createSession: async (
  tableId: string,
  maxParticipants: number,
  seatNames?: string[],
): Promise<CreateSession> => {
  const { data } = await api.post<CreateSession>('/sessions', {
    tableId,
    maxParticipants,
    ...(seatNames ? { seatNames } : {}),
  })
  return data
},
```

- [ ] **Step 4: Branch the modal**

- `import { isHubBuild } from '@/lib/isHubBuild'`, `import { useNavigate } from 'react-router-dom'`.
- Add state: `const [seatNames, setSeatNames] = useState<string[]>([''])`. Keep it length-synced to `participants` — when the +/- buttons change `participants`, `setSeatNames(prev => Array.from({ length: next }, (_, i) => prev[i] ?? ''))`.
- Hub render branch (replace the dashed QR box + join-code block):

```tsx
{isHubBuild ? (
  <div className="flex flex-col gap-2 my-4">
    <p className="text-zinc-500 text-sm">{t('seatNamesLabel')}</p>
    {seatNames.map((val, i) => (
      <input
        key={i}
        value={val}
        maxLength={50}
        placeholder={t('seatNamePlaceholder', { n: i + 1 })}
        onChange={(e) => setSeatNames((prev) => prev.map((v, j) => (j === i ? e.target.value : v)))}
        className="w-full rounded-2xl border-2 border-zinc-200 px-4 py-2 outline-none focus:border-[#8B0000]"
      />
    ))}
  </div>
) : (
  /* existing QR box + join code JSX */
)}
```

- Hub submit:

```tsx
const mutation = useMutation({
  mutationFn: async ({ tableId, maxParticipants }: { tableId: string; maxParticipants: number }) => {
    if (isHubBuild) {
      const s = await SessionTableService.createSession(
        tableId, maxParticipants, seatNames.map((n) => n.trim()),
      )
      return { sessionId: s.sessionId as string }
    }
    const newSession = await SessionTableService.createSession(tableId, maxParticipants)
    const realId = newSession.sessionId
    if (!realId) throw new Error('El servidor no devolvio el ID de la session')
    const qrData = await SessionTableService.getQrToken(realId)
    return { qrToken: qrData.qrToken, joinCode: newSession.joinCode }
  },
  onSuccess: (data) => {
    queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
    toast.success(t('tableOpenedToast'))
    if (isHubBuild) {
      handleClose()
      navigate(`/waiter/tables/${(data as { sessionId: string }).sessionId}`)
      return
    }
    setQrToken((data as { qrToken: string }).qrToken)
    setJoinCode((data as { joinCode?: string }).joinCode ?? '')
  },
})
```

- The submit button label: `isHubBuild ? t('openTableButton') : t('openTableGenerateQrButton')` — reuse `openTableButton` if it exists in the `waiter` bundle, else add it in T10 and use a literal `t('assignTableLabel')` placeholder for now. Keep the current key if unsure.

- [ ] **Step 5: Add the two i18n keys used here**

`es/waiter.ts`: `seatNamesLabel: 'Nombres de los asientos (opcional)'`, `seatNamePlaceholder: 'Asiento {{n}}'`.
`en/waiter.ts`: `seatNamesLabel: 'Seat names (optional)'`, `seatNamePlaceholder: 'Seat {{n}}'`.
(`en/waiter.ts` `satisfies typeof esWaiter` — add to both or the build breaks.)

- [ ] **Step 6: Run build + lint + tests**

Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run ParticipantsQrModal`
Then full: `pnpm run test:run`
Expected: all green; the existing Cloud-path modal test (if any) still passes.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/waiter/components/ParticipantsQrModal.tsx frontend/src/pages/waiter/components/ParticipantsQrModal.test.tsx frontend/src/lib/api.ts frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts
git commit -m "feat(hub): assign-table modal collects seat names instead of a QR"
```

---

### Task 8: Frontend — seat management on the table-detail view

**Files:**
- Create: `frontend/src/pages/waiter/components/SeatFormModal.tsx`
- Modify: `frontend/src/pages/waiter/TableInformation.tsx`
- Modify: `frontend/src/lib/api.ts` (`SessionTableService.addSeat/renameSeat/removeSeat`)
- Modify: `frontend/src/store/websocket.ts` (handle `PARTICIPANT_RENAMED`)
- Modify: `frontend/src/locales/es/waiter.ts`, `frontend/src/locales/en/waiter.ts`
- Test: `frontend/src/pages/waiter/components/SeatFormModal.test.tsx` (new)
- Test: `frontend/src/pages/waiter/TableInformation.seats.test.tsx` (new)

**Interfaces:**
- Consumes: `isHubBuild`, `useUIStore().openModal`, existing `subscribeToWaiterSession`, `SessionDetailResponseDto` (`infoSession`).
- Produces: `SessionTableService.addSeat(sessionId, name?)`, `renameSeat(sessionId, from, to)`, `removeSeat(sessionId, name)` — all resolve to `infoSession`. Modal id `'SEAT_FORM'` with payload `{ sessionId, mode: 'add' | 'rename', from?: string }`; delete confirm reuses `openModal('DELETE_SEAT', { sessionId, name })`.

- [ ] **Step 1: Write the failing tests**

`SeatFormModal.test.tsx`:

```tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { vi, describe, it, expect } from 'vitest'

const addSeat = vi.fn().mockResolvedValue({})
const renameSeat = vi.fn().mockResolvedValue({})
vi.mock('@/lib/api', () => ({ SessionTableService: { addSeat, renameSeat } }))
const closeModal = vi.fn()
let payload: Record<string, unknown> = { sessionId: 's1', mode: 'add' }
vi.mock('@/store/uiStore', () => ({
  useUIStore: () => ({ activeModal: 'SEAT_FORM', modalPayload: payload, closeModal }),
}))

describe('SeatFormModal', () => {
  it('add mode posts addSeat', async () => {
    payload = { sessionId: 's1', mode: 'add' }
    const { SeatFormModal } = await import('./SeatFormModal')
    render(<SeatFormModal />)
    fireEvent.change(screen.getByLabelText(/nombre del asiento|seat name/i), { target: { value: 'Ana' } })
    fireEvent.click(screen.getByRole('button', { name: /agregar|add/i }))
    await waitFor(() => expect(addSeat).toHaveBeenCalledWith('s1', 'Ana'))
  })

  it('rename mode posts renameSeat with the original name', async () => {
    payload = { sessionId: 's1', mode: 'rename', from: 'Asiento 2' }
    vi.resetModules()
    const { SeatFormModal } = await import('./SeatFormModal')
    render(<SeatFormModal />)
    fireEvent.change(screen.getByLabelText(/nombre del asiento|seat name/i), { target: { value: 'Beto' } })
    fireEvent.click(screen.getByRole('button', { name: /guardar|save|renombrar|rename/i }))
    await waitFor(() => expect(renameSeat).toHaveBeenCalledWith('s1', 'Asiento 2', 'Beto'))
  })
})
```

`TableInformation.seats.test.tsx`: mock `@/lib/isHubBuild` → `{ isHubBuild: true }`, mock the queries so `sessionData.participants = [{ userId: null, name: 'Ana' }]` and `status: 'OPEN'`; assert a "Agregar asiento" button and, per row, a rename and a remove control are present. Add a second case with `isHubBuild: false` asserting none of those controls render.

- [ ] **Step 2: Run to verify failure**

Run: `cd frontend && pnpm run test:run SeatFormModal TableInformation.seats`
Expected: FAIL — `SeatFormModal` missing, no seat controls.

- [ ] **Step 3: API methods** — in `SessionTableService`:

```ts
addSeat: async (sessionId: string, name?: string): Promise<infoSession> => {
  const { data } = await api.post<infoSession>(`/sessions/${sessionId}/participants`, { name })
  return data
},
renameSeat: async (sessionId: string, from: string, to: string): Promise<infoSession> => {
  const { data } = await api.patch<infoSession>(`/sessions/${sessionId}/participants`, { from, to })
  return data
},
removeSeat: async (sessionId: string, name: string): Promise<infoSession> => {
  const { data } = await api.delete<infoSession>(`/sessions/${sessionId}/participants/${encodeURIComponent(name)}`)
  return data
},
```

- [ ] **Step 4: `SeatFormModal.tsx`** — model it on `TransferTableModal.tsx` (same `useUIStore` + `useMutation` + `Dialog` shape). One labelled `<input>` (label `t('seatNameInputLabel')`), prefilled with `from` in rename mode. Submit → `mode === 'add' ? addSeat(sessionId, value.trim() || undefined) : renameSeat(sessionId, from, value.trim())`. `onSuccess`: `queryClient.invalidateQueries({ queryKey: ['sessionDetails', sessionId] })` + `['bill', sessionId]`, `toast.success(t(mode === 'add' ? 'seatAddedToast' : 'seatRenamedToast'))`, `closeModal()`. `onError`: if `axios.isAxiosError(e) && e.response?.status === 409 && mode === 'rename'` → `toast.error(t('renameBlockedBillExistsToast'))`, else `toast.error(t('seatErrorToast'))`.

- [ ] **Step 5: `TableInformation.tsx` edits**

- `import { isHubBuild } from '@/lib/isHubBuild'`, `import { SeatFormModal } from './components/SeatFormModal'`, add `Pencil` to the lucide import.
- Participants card `<CardHeader>`: when `isHubBuild`, add a right-aligned button:

```tsx
{isHubBuild && (
  <Button variant="ghost" className="text-sm" disabled={actionsDisabled}
    onClick={() => openModal('SEAT_FORM', { sessionId: id, mode: 'add' })}>
    <Plus className="w-4 h-4 mr-1" /> {t('addSeatLabel')}
  </Button>
)}
```

- Participant row: change `key={participant.userId}` → `key={participant.name}`. When `isHubBuild`, append two icon buttons:

```tsx
{isHubBuild && (
  <div className="ml-auto flex gap-1">
    <Button variant="ghost" size="icon" title={t('renameSeatTitle')} disabled={actionsDisabled}
      onClick={() => openModal('SEAT_FORM', { sessionId: id, mode: 'rename', from: participant.name })}>
      <Pencil className="w-4 h-4" />
    </Button>
    <Button variant="ghost" size="icon" title={t('removeSeatTitle')} disabled={actionsDisabled}
      onClick={() => openModal('DELETE_SEAT', { sessionId: id, name: participant.name })}>
      <Trash2 className="w-4 h-4" />
    </Button>
  </div>
)}
```

  (Adjust the row flex container so the buttons sit at the end.)
- Mount `<SeatFormModal/>` next to the other modals at the bottom of the JSX.
- **Remove-seat confirm:** add a minimal `DELETE_SEAT` branch. Simplest: a tiny inline `<Dialog>` in `SeatFormModal.tsx` keyed on `activeModal === 'DELETE_SEAT'` (title `t('removeSeatTitle')`, body `t('removeSeatConfirm', { name })`, confirm → `removeSeat(sessionId, name)` → same invalidation + `t('seatRemovedToast')`). Keep it in the same file so one component owns all seat mutations.

- [ ] **Step 6: WebSocket handler** — in `frontend/src/store/websocket.ts`, find where `PARTICIPANT_JOINED` / `PARTICIPANT_LEFT` message `type`s are handled for the waiter session subscription and add `PARTICIPANT_RENAMED` to the same branch (invalidate `['sessionDetails', sessionId]`). If the handler already invalidates on any unrecognised session message, just confirm and add a comment.

- [ ] **Step 7: i18n** — add every remaining key from the spec §6.6 table to `es/waiter.ts` and `en/waiter.ts` (`addSeatLabel`, `renameSeatTitle`, `removeSeatTitle`, `removeSeatConfirm`, `seatNameInputLabel`, `seatAddedToast`, `seatRenamedToast`, `seatRemovedToast`, `seatErrorToast`, `renameBlockedBillExistsToast`).

- [ ] **Step 8: Run build + lint + tests**

Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run`
Expected: all green; existing `TableInformation.*.test.tsx` still pass (they run Cloud-mode, `isHubBuild` false).

- [ ] **Step 9: Commit**

```bash
git add frontend/src/pages/waiter/components/SeatFormModal.tsx frontend/src/pages/waiter/components/SeatFormModal.test.tsx frontend/src/pages/waiter/TableInformation.tsx frontend/src/pages/waiter/TableInformation.seats.test.tsx frontend/src/lib/api.ts frontend/src/store/websocket.ts frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts
git commit -m "feat(hub): seat rename/add/remove controls on the table detail view"
```

---

### Task 9: Frontend — hide the loyalty admin UI in the Hub build

**Files:**
- Modify: `frontend/src/components/SettingsBar.tsx`
- Modify: `frontend/src/pages/admin/Settings.tsx`
- Modify: `frontend/src/components/GlobalSearchResults.tsx`
- Test: `frontend/src/components/SettingsBar.hubBuild.test.tsx` (new)

**Interfaces:**
- Consumes: `isHubBuild`.
- Produces: when `isHubBuild`, `SETTINGS_NAV` has no `FIDELIZACION` group; `Settings.renderContent` returns `null` for `FIDELIZACION` / `LOYALTY_REWARDS`; global search yields no loyalty-settings entries.

- [ ] **Step 1: Write the failing test** — `SettingsBar.hubBuild.test.tsx`: mock `@/lib/isHubBuild` → `true`, render `<SettingsBar collapsed={false} onToggleCollapsed={() => {}} />` inside the needed store/i18n providers (copy provider setup from any existing admin component test), assert `screen.queryByText(/fidelizaci|loyalty/i)` is null. Second case `isHubBuild: false` → the group label is present.

- [ ] **Step 2: Run to verify failure**

Run: `cd frontend && pnpm run test:run SettingsBar.hubBuild`
Expected: FAIL — the group renders regardless.

- [ ] **Step 3: `SettingsBar.tsx`**

```tsx
import { isHubBuild } from '@/lib/isHubBuild'

const SETTINGS_NAV: NavNode[] = [
  { kind: 'leaf', type: 'BRANDING' },
  { kind: 'leaf', type: 'MENU' },
  { kind: 'group', group: 'BILLING', labelKey: 'billingLabel', Icon: Receipt, members: ['BILLING', 'PAYMENT_GATEWAY', 'TICKET'] },
  { kind: 'group', group: 'HARDWARE', labelKey: 'hardwareLabel', Icon: Printer, members: ['HARDWARE', 'PRINTING'] },
  { kind: 'leaf', type: 'SPACE' },
  { kind: 'leaf', type: 'HORARIO' },
  ...(isHubBuild ? [] : [{ kind: 'group', group: 'FIDELIZACION', labelKey: 'loyaltyLabel', Icon: Gift, members: ['FIDELIZACION', 'LOYALTY_REWARDS'] } as NavNode]),
]
```

- [ ] **Step 4: `Settings.tsx`**

```tsx
import { isHubBuild } from '@/lib/isHubBuild'
// ...
case 'FIDELIZACION':
    return isHubBuild ? null : <LoyaltySettings />;
case 'LOYALTY_REWARDS':
    return isHubBuild ? null : <LoyaltyRewardsSettings />;
```

- [ ] **Step 5: `GlobalSearchResults.tsx`** — locate the static list of searchable settings sections; filter out the loyalty entries when `isHubBuild` (one `.filter(entry => !isHubBuild || !['FIDELIZACION', 'LOYALTY_REWARDS'].includes(entry.key))`, adjust to the real shape).

- [ ] **Step 6: Run build + lint + tests**

Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run`
Expected: green.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/SettingsBar.tsx frontend/src/pages/admin/Settings.tsx frontend/src/components/GlobalSearchResults.tsx frontend/src/components/SettingsBar.hubBuild.test.tsx
git commit -m "feat(hub): hide the loyalty admin UI in the Hub build"
```

---

### Task 10: Report, PROGRESS.md, squash, PR

**Files:**
- Create: `reports/NN-emb-feat-hub-waiter-managed-seats.md` (NN = next free sequence number at commit time)
- Modify: `PROGRESS.md`

- [ ] **Step 1: Verify both suites from clean**

Run: `cd backend && ./mvnw test`
Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run`
Both must be green. Note the final backend test count and frontend test count.

- [ ] **Step 2: Write the report** per CLAUDE.md §4 — sections: Identification (report NN, Task `EMB-FEAT-HUB`, predecessor = report 397 guest-join), Objective, Modified Files (every path), What Changed? (backend: seat seeding, 3 endpoints, null sweep, `ParticipantRenamed`, loyalty guard; frontend: `isHubBuild` module, router fork, assign-table Hub variant, seat controls, loyalty UI hidden), Why It Changed? (Hub is LAN-only, customers can't reach the server; waiter runs the table). List the spec/plan paths. State "no DB migration".

- [ ] **Step 3: Update `PROGRESS.md`** — move `EMB-FEAT-HUB` from "Next up" to done; set Last Completed Task; flip the T1–T10 checkboxes; refresh System Health with the new counts; keep it under 180 lines.

- [ ] **Step 4: Squash the effort into one commit**

```bash
git reset --soft $(git merge-base HEAD origin/main)
git add docs/superpowers/specs/2026-09-07-hub-waiter-managed-seats-design.md \
        docs/superpowers/plans/2026-09-07-hub-waiter-managed-seats.md \
        backend/src/main/java/com/vanter/ember/session/ \
        backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListener.java \
        backend/src/test/java/com/vanter/ember/session/ \
        backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListenerTest.java \
        frontend/src/lib/isHubBuild.ts frontend/src/App.tsx frontend/src/App.hubBuild.test.tsx \
        frontend/src/pages/auth/Login.tsx \
        frontend/src/pages/waiter/ frontend/src/lib/api.ts frontend/src/store/websocket.ts \
        frontend/src/components/SettingsBar.tsx frontend/src/components/SettingsBar.hubBuild.test.tsx \
        frontend/src/pages/admin/Settings.tsx frontend/src/components/GlobalSearchResults.tsx \
        frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts \
        reports/NN-emb-feat-hub-waiter-managed-seats.md PROGRESS.md
git status   # confirm nothing stray (no .env, no keys)
git commit -m "feat(hub): waiter-managed seats (no customer flow in the Hub build)"
```

- [ ] **Step 5: Push + PR**

```bash
git push -u origin spec/hub-waiter-seats
gh pr create --base main --head spec/hub-waiter-seats \
  --title "feat(hub): waiter-managed seats (EMB-FEAT-HUB)" \
  --body "<summary of the spec: backend name-only seats + 3 WAITER endpoints + null sweep + ParticipantRenamed + loyalty guard; frontend Hub-build fork removes the customer subsystem, assign-table collects seat names, seat controls on the table detail, loyalty admin UI hidden. No DB migration. Spec + plan in docs/superpowers/. Depends on #97 for the LoyaltyAccrualListener null-userId guard.>"
```

No Claude attribution in the commit or the PR body.

- [ ] **Step 6: Reset reminder** — tell the user the effort is done, PR number, both test counts, and to run `/clear`.

## Self-Review

**Spec coverage:** §5.2 seed seats → T1. §5.3 add/rename/remove → T4/T5. §5.4 `ParticipantRenamed` → T3. §5.5 null sweep → T2. §5.6 loyalty guard → T2. §6.1 strip customer subsystem → T6. §6.2 assign-table Hub variant → T7. §6.3 seat management → T8. §6.4 add-item modal (no change) → covered by note in T8. §6.5 hide loyalty UI → T9. §6.6 i18n → split across T7 (2 keys) + T8 (rest). §6.7 FE tests → T6–T9. §7 rollout (no migration) → Global Constraints + T10 report. All spec sections map to a task.

**Placeholder scan:** every code step has real code or a concrete file+shape to follow (`TransferTableModal.tsx` as the model for `SeatFormModal`, `GuestJoinFlowIntegrationTest` as the model for the integration test). Selectors in a couple of FE tests are flagged "refine against real markup" — acceptable, the assertion targets are concrete.

**Type consistency:** `SessionService.createSession` is 4-arg from T1 on; `addSeat(sessionId, requestingWaiter, name)`, `renameSeat(sessionId, requestingWaiter, from, to)`, `removeSeat(sessionId, requestingWaiter, name)` consistent between T4/T5 definitions and their controller callers. `ParticipantRenamed(tenantId, sessionId, oldName, newName)` 4-arg ctor used identically in T3 (definition) and T5 (publish). Frontend `SessionTableService.addSeat/renameSeat/removeSeat` return `infoSession` in T8 definition and consumers. `isHubBuild` is a named export from `@/lib/isHubBuild` in T6 and imported the same way in T7/T8/T9.
