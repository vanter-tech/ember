# Report 353 — EMB-FEAT-18: `GET /identity/waiters` + `POST /sessions/{id}/transfer`

## 1. Identification
- **Report number:** 353
- **Current Task ID:** EMB-FEAT-18 (Plan C — `docs/superpowers/plans/2026-09-03-waiter-table-detail-actions.md`, Task 7)
- **Predecessor Task:** EMB-FEAT-17 (report 352 — `TableTransferred` event + `SessionService.transferTable`)

## 2. Objective
Expose the HTTP layer for the "Transferir" button: a WAITER/ADMIN endpoint that
lists the tenant's active waiters for the picker, and a WAITER endpoint that hands
the current open table to a chosen waiter by delegating to EMB-FEAT-17's
`SessionService.transferTable`. Backend-only; the frontend modal is EMB-FEAT-21.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/identity/repository/UserRepository.java`
- `backend/src/main/java/com/vanter/ember/identity/dto/WaiterSummary.java` (new)
- `backend/src/main/java/com/vanter/ember/identity/controller/WaiterDirectoryController.java` (new)
- `backend/src/main/java/com/vanter/ember/session/dto/TransferTableRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java`
- `backend/src/test/java/com/vanter/ember/identity/controller/WaiterDirectoryControllerTest.java` (new)
- `backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java`

## 4. What Changed?
- **`UserRepository.java`** — new derived finder
  `findByRestaurantId_IdAndRoleAndActiveTrue(UUID restaurantId, Role role): List<User>`,
  sibling of the existing `findByRestaurantId_IdAndRole` /
  `findByRestaurantId_IdAndRoleNotOrderByNameAsc` (untenanted-by-FK — `User` carries
  no `@TenantId`). `User.active` is a `Boolean`, so `AndActiveTrue` resolves.
- **`WaiterSummary.java`** — new `record(String id, String name, String email)` with a
  `static from(User)` factory. Deliberately narrow so the endpoint never serializes
  `passwordHash` / `pinHash`.
- **`WaiterDirectoryController.java`** — new `@RestController @RequestMapping("/identity")`.
  `GET /identity/waiters` `@PreAuthorize("hasAnyRole('WAITER','ADMIN')")` →
  `List<WaiterSummary>`, resolving the tenant via
  `TenantContextHolder.requireTenantId()` and mapping through `WaiterSummary::from`.
- **`TransferTableRequest.java`** — new `record(@NotBlank String targetWaiterId)`.
- **`SessionController.java`** — new `POST /{id}/transfer`
  `@PreAuthorize("hasRole('WAITER')")`, body `TransferTableRequest` (resolved via the
  existing `session.dto.*` wildcard import), placed before the confirm mapping. Calls
  `sessionService.transferTable(id, authentication.getName(), request.targetWaiterId())`
  then returns `sessionService.getSessionDetails(id)` (`SessionDetailResponseDto`) —
  same shape/return convention as `addWaiterItem` (EMB-FEAT-13). No new controller
  fields/deps; `Authentication` already imported.
- **`WaiterDirectoryControllerTest.java`** — new `@WebMvcTest(WaiterDirectoryController.class)`
  `@Import({SecurityConfig.class, CorsConfig.class})`, `@MockBean` `UserRepository` /
  `JwtService` / `UserDetailsService` / `RestaurantRepository` (mirrors
  `UserAdminControllerTest`), `@AfterEach TenantContextHolder.clear()`. 2 tests: WAITER →
  200 with `$[0].id`/`name`/`email` present and `$[0].passwordHash` absent; KITCHEN → 403.
- **`SessionControllerTest.java`** — `import TransferTableRequest`, 3 new tests under a
  `// --- POST /sessions/{id}/transfer ---` header (reusing `sampleSession()` /
  `sampleSessionDetail(List.of())`): WAITER `w@…` → 200 with `$.id`; WAITER + `{}` body →
  400 (`@NotBlank`); CUSTOMER → 403 (method security). No `.with(csrf())` (CSRF disabled
  in `SecurityConfig`, matching every other test in the file); `objectMapper` for valid
  bodies, raw JSON string for the invalid one.

## 5. Why It Changed?
The transfer picker needs a list of candidate waiters scoped to the current tenant and
filtered to active accounts — a one-line derived finder plus a projection DTO keeps
credential hashes off the wire. The directory lives in a new `identity` controller
rather than being bolted onto `UserAdminController` (ADMIN-only) because a WAITER must
call it. The transfer endpoint is a thin delegator: all rules, events and broadcasts
already live in `SessionService.transferTable` (EMB-FEAT-17), and `GlobalExceptionHandler`
already maps its `IllegalStateException` / `AccessDeniedException` /
`IllegalArgumentException` to 409 / 403 / 400, so no handler or service change was needed.

## 6. Plan Deviations
- Plan Task 7 Step 4's `WaiterDirectoryControllerTest` snippet omits setting the tenant
  context, but the controller calls `TenantContextHolder.requireTenantId()` (throws when
  unset). Added `TenantContextHolder.setTenantId(TENANT_ID)` in the 200 test and an
  `@AfterEach` clear, matching `UserAdminControllerTest`'s `getStaff` convention. The 403
  test needs no tenant (blocked at method security before the controller body).
- Plan snippet builds the test `User` with `.passwordHash("SECRET")` only; added
  `.active(true)` so the (unused-in-this-mock) state is coherent. Assertion contract
  unchanged.
- Test method names follow this repo's existing style
  (`transfer_returnsSessionDetail` / `transfer_badRequestWhenTargetMissing` /
  `transfer_forbiddenForCustomer`) rather than the plan's `transfer_200_…` names, and
  use `username = "waiter@test.com"` to match the file's other WAITER tests.
- Commit named `feat(session): waiters directory and transfer-table endpoint` (plan verbatim).

## 7. Verification
- `./mvnw test -Dtest=WaiterDirectoryControllerTest,SessionControllerTest` → PASS
  (2 + 35; `SessionControllerTest` was 32).
- `./mvnw test` → **988/988** BUILD SUCCESS, 0 failures / 0 errors (983 + 5 new).
