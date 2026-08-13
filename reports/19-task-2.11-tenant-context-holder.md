# Report 19 — task-2.11: `TenantContextHolder` from the JWT `rid` claim

## 1. Identification
- **Report number:** 19
- **Task ID:** task-2.11
- **Predecessor task:** task-2.10 (report 18)

## 2. Objective
Introduce a request-scoped `TenantContextHolder` populated from the authenticated JWT `rid` claim,
wire it into both the HTTP filter chain (`jwtAuthFilter`) and the STOMP inbound channel
(`JwtChannelInterceptor`), and retire `SettingsController`'s ad hoc `getRestaurantIdFromAuth()`
tenant resolution.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/TenantContextHolder.java` (new)
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- `backend/src/main/java/com/vanter/ember/config/JwtChannelInterceptor.java`
- `backend/src/main/java/com/vanter/ember/identity/service/JwtService.java`
- `backend/src/main/java/com/vanter/ember/settings/controller/SettingsController.java`
- `backend/src/test/java/com/vanter/ember/config/TenantContextHolderTest.java` (new)
- `backend/src/test/java/com/vanter/ember/config/JwtChannelInterceptorTest.java`

## 4. What Changed?

### `TenantContextHolder` (new)
Final, non-instantiable holder over a plain `ThreadLocal<UUID>` exposing `setTenantId`,
`getTenantId` (nullable), `requireTenantId` (throws `IllegalStateException` when unbound) and
`clear()` (backed by `ThreadLocal.remove()` so pooled request threads do not retain the value).

### `JwtService.extractTenantId(String token)`
New accessor reading the `rid` claim as a `String` and parsing it to `UUID`. Returns `null` when the
claim is absent/blank (QR session tokens issued by `QrTokenService` carry only `maxParticipants`) or
unparsable, so a missing tenant degrades to "no tenant bound" rather than an exception inside the
filter chain.

### `SecurityConfig.jwtAuthFilter`
After the `SecurityContextHolder` authentication is established, the filter binds
`jwtService.extractTenantId(token)` into `TenantContextHolder` and wraps `chain.doFilter(...)` in a
`try/finally` that always clears it. The two pre-existing early-return branches (no `Bearer` header,
invalid token) are untouched and never bind a tenant.

### `JwtChannelInterceptor`
- On `CONNECT`: extracts `rid`, stores it under the new `TENANT_SESSION_ATTRIBUTE` key in the STOMP
  session attributes, and binds it to `TenantContextHolder`.
- On every non-`CONNECT` frame: rehydrates the tenant from those session attributes
  (`bindTenantFromSession`), since each inbound message is handled on a pooled channel thread that
  does not inherit the CONNECT thread's `ThreadLocal`.
- New `afterSendCompletion` override clears the holder once the message has been dispatched.
- Null-safe throughout: a `null` accessor short-circuits, and absent session attributes are ignored.

### `SettingsController`
Removed `getRestaurantIdFromAuth()` and the `UserRepository` dependency; both endpoints now call
`TenantContextHolder.requireTenantId()`. The now-unused `Authentication` parameters and the
`org.springframework.security.core.Authentication` import were dropped.

### Tests
- `TenantContextHolderTest` (4 tests): set/get round-trip, `clear()` semantics, `requireTenantId()`
  failure mode, and thread confinement (a second thread must not observe the bound tenant).
- `JwtChannelInterceptorTest` (+3 tests): CONNECT binds the tenant and persists it to session
  attributes, a SUBSCRIBE frame rehydrates it from those attributes, and `afterSendCompletion`
  clears it. Existing helpers were widened to accept an optional session-attribute map, and an
  `@AfterEach` clears the holder so tests stay isolated.

## 5. Why It Changed?
`SettingsController.getRestaurantIdFromAuth()` was actively wrong, not merely ad hoc: it looked the
user up by email and returned `User.getId()` — the **user** id — as the restaurant id. Every settings
read/write therefore addressed a tenant partition keyed by a user id, silently creating default
`RestaurantSettings` rows per user instead of per restaurant. Now that task-2.10 issues a real `rid`
claim at registration and login, the tenant is available directly from the verified token and no
longer needs to be re-derived (incorrectly) per controller.

Centralizing resolution in `TenantContextHolder` also gives the remaining multi-tenancy backlog a
single authoritative source of the current tenant: task-2.12 (`DashboardController`'s client-supplied
`restaurantId` IDOR), task-2.14's Hibernate `CurrentTenantIdentifierResolver`, and task-2.17's Mongo
scoping all read from this holder rather than re-deriving the tenant per call site.

`ThreadLocal.remove()` in a `finally` block (HTTP) and in `afterSendCompletion` (STOMP) prevents a
tenant id from leaking across requests on a reused container thread — the failure mode here is
cross-tenant data exposure, so the cleanup path is mandatory rather than hygienic.

## 6. Verification
- `./mvnw test`: **295 tests, 0 failures, 0 errors, 0 skipped** (288 baseline + 7 new).
