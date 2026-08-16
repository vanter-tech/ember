# Report 73 — EMB-PC-04: Platform `SecurityFilterChain` + `PlatformJwtAuthFilter`

## 1. Identification
- **Report number:** 73
- **Task ID:** EMB-PC-04
- **Predecessor Task:** EMB-PC-03 (report 72)

## 2. Objective
Isolate `/platform/**` behind its own Spring Security filter chain, authenticated by
`PlatformJwtService`-signed tokens only, with no interaction with `TenantContextHolder`. Prove
mutual rejection: a tenant token must not authenticate on `/platform/**`, and a platform token
must not authenticate on a tenant route.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/platform/config/PlatformSecurityConfig.java` (new)
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java` (added `@Order(2)`)
- `backend/src/test/java/com/vanter/ember/config/PlatformAuthIsolationTest.java` (new)

## 4. What Changed?
- `PlatformSecurityConfig` defines a second `SecurityFilterChain` bean, `@Order(1)`, scoped to
  `/platform/**` via `securityMatcher(new AntPathRequestMatcher("/platform/**"))` (the plain
  `String` overload resolves to `MvcRequestMatcher`, which needs a `mvcHandlerMappingIntrospector`
  bean that isn't present in non-web test contexts such as `MinioConfigTest`
  (`spring.main.web-application-type=none`) — `AntPathRequestMatcher` avoids that dependency).
  Stateless session, CSRF disabled, `anyRequest().authenticated()`.
- `platformJwtAuthFilter()` is an inline `OncePerRequestFilter`, structurally mirroring the
  existing tenant filter in `SecurityConfig`: reads the `Bearer` token, validates it against
  `PlatformJwtService.isTokenValid`, resolves the operator via `PlatformOperatorDetailsService`
  injected **by concrete type** (not the `UserDetailsService` interface — `EmberUserDetailsService`
  is `@Primary`, so interface injection here would silently wire the tenant service instead), and
  sets `SecurityContextHolder`. It has no `TenantContextHolder` read/write and no restaurant
  active-status check.
- Added `platformUnauthorizedEntryPoint()` (401, mirroring `SecurityConfig.unauthorizedEntryPoint()`)
  and wired it via `.exceptionHandling(...)` — without it, Spring Security's default
  `Http403ForbiddenEntryPoint` returns 403 for an unauthenticated request on a chain with no
  entry point configured, which does not match the 401 contract the rest of the API uses.
- `SecurityConfig.filterChain` gained `@Order(2)` — Spring Security requires an explicit order once
  more than one `SecurityFilterChain` bean exists; unordered was fine with a single bean, ambiguous
  with two.
- `PlatformAuthIsolationTest` (`@SpringBootTest @AutoConfigureMockMvc`, real non-mocked `JwtService`
  and `PlatformJwtService` beans, unlike other controller tests which `@MockitoBean` `JwtService`)
  asserts: a tenant-signed token against `GET /platform/restaurants` → 401; a platform-signed token
  against `GET /catalog/categories` → 401.

## 5. Why It Changed?
Mutual exclusion between platform (super-admin) and tenant auth must not depend on a claims check
that could be bypassed or miscoded — it comes from the two chains verifying against disjoint
secrets (`platform.jwt.secret` vs `jwt.secret`), so a token signed under one fails signature
verification outright under the other, before either `UserDetailsService` is even consulted. This
is the auth boundary EMB-PC-05 (login controller) and the rest of the `/platform/**` backlog build
on.

## Verification
`cd backend && ./mvnw test` — 542/542 passing (540 pre-existing + 2 new).
