# Report 21 — task-2.13: live join capacity + tenant-scoped join paths

## 1. Identification
- **Report number:** 21
- **Task ID:** task-2.13
- **Predecessor task:** task-2.12 (report 20)

## 2. Objective
Make `joinSession` enforce capacity from the live session document instead of a stale QR-JWT claim,
and scope both join paths (`QrTokenService` and the join-code lookup) to the authenticated tenant.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/session/service/QrTokenService.java`
- `backend/src/main/java/com/vanter/ember/session/service/SessionService.java`
- `backend/src/main/java/com/vanter/ember/session/repository/SessionRepository.java`
- `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java`
- `backend/src/test/java/com/vanter/ember/session/service/QrTokenServiceTest.java`
- `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java`
- `backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java`

## 4. What Changed?

### `QrTokenService`
The `maxParticipants` claim is gone; the token now carries a `rid` claim taken from
`TenantContextHolder.requireTenantId()`. `generateQrToken` drops its second parameter, becoming
`generateQrToken(String sessionId)`. `validateQrToken` gained a second gate after the signature/expiry
check: the token's `rid` (read via `JwtService.extractTenantId`) must equal the caller's bound tenant,
otherwise it throws `IllegalArgumentException("QR token does not belong to this restaurant")`. A
token carrying no `rid` at all fails the same check. `extractMaxParticipants` was deleted — it had no
remaining callers.

### `SessionService.joinSession`
Capacity is now read from `session.getMaxParticipants()` after the session is loaded. The new
`assertSessionBelongsToCurrentTenant(session)` runs before the capacity and duplicate-participant
checks: it resolves the session's `DiningTables` and throws `ResourceNotFoundException` (not
`AccessDenied`, so cross-tenant probing cannot distinguish "exists elsewhere" from "does not exist")
when the table's `restaurantId` differs from the bound tenant.

### `SessionService.joinSessionCode` + `SessionRepository`
`findByJoinCodeAndStatus(joinCode, status)` was replaced by
`findByJoinCodeAndStatusAndTableIdIn(joinCode, status, tableIds)`. `joinSessionCode` passes the
current tenant's active table ids via the new `currentTenantTableIds()` helper, so the tenant filter
is part of the query rather than a post-hoc check. The old unscoped method was removed outright so it
cannot be reintroduced by accident.

### `SessionController.getQrToken`
Now calls `qrTokenService.generateQrToken(session.getId())`; the waiter's bound tenant supplies the
`rid` claim.

### Tests
- `QrTokenServiceTest`: rewritten around the tenant claim (7 tests) — binds a tenant in `@BeforeEach`,
  clears in `@AfterEach`, asserts the `rid` claim is embedded, and adds two rejection cases (token
  minted for another tenant; token with no tenant claim). Expiry/tamper cases retained.
- `SessionServiceTest`: existing join tests drop the `extractMaxParticipants` stub and bind a tenant
  through a new `bindTenant()` helper (so the capacity assertions now prove capacity comes from the
  session). Four new tests: capacity is read live after an expansion, a session on another tenant's
  table is rejected without saving, the join-code lookup is scoped to the tenant's table ids, and a
  foreign join code yields 404 without saving.
- `SessionControllerTest`: `generateQrToken` stub updated to the one-argument signature.

## 5. Why It Changed?
Two distinct defects.

**Stale capacity.** QR tokens live 15 minutes and embedded `maxParticipants` at mint time.
`expandCapacity` raises `session.maxParticipants` on the document, but a QR code already printed or
displayed at the table kept the old number, so diners scanning it were rejected with "at full
capacity" even though seats existed — and the number the check trusted came from a token minted
before the change rather than from current state. The session document is the only authority for its
own capacity.

**Cross-tenant joins.** Neither join path was tenant-aware. Join codes are five characters drawn from
a 32-symbol alphabet and were looked up globally with `findByJoinCodeAndStatus`, so a code collision
across restaurants — or straightforward enumeration, ~33.5M combinations with no rate limit on this
endpoint — dropped a diner into another restaurant's live session, where they could read the
collaborative cart and add items. QR tokens were likewise only bound to a session id, so a leaked
token was replayable by any authenticated user of any tenant. Both paths now require the caller's
tenant to own the session.

`Session` still has no `tenantId` field, so ownership is resolved through the session's dining table,
which is tenant-owned. Task-2.17 adds the denormalized field and repository-level scoping; this
change is written so that swapping in that field later replaces the table lookup without altering the
enforcement points.

**Behavioral implications worth tracking:**
- The join-code query filters on *active* tables (reusing
  `findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc`), so a session whose table is deactivated
  mid-service becomes unreachable by code. That matches the intent — deactivated tables should not
  accept new diners — but it is a real behavior change.
- Both join paths now require a bound tenant, so a diner's JWT must carry `rid` (true for every token
  issued since task-2.10). A diner registered under one restaurant cannot join another's session
  without registering there; that is the tenant model as designed, not a regression.

## 6. Verification
- `./mvnw test`: **305 tests, 0 failures, 0 errors, 0 skipped** (299 baseline + 6 new).
- `E2EOrderFlowTest` passes unchanged, exercising the full HTTP path (waiter mints a QR with a real
  JWT, customer joins) with the tenant bound by `jwtAuthFilter`.
