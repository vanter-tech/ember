# Report 72

## Identification
- **Report Number:** 72
- **Current Task ID:** EMB-PC-03
- **Predecessor Task:** EMB-PC-02 (report 71)

## Objective
Add `PlatformJwtService` (keyed to a new, separate `platform.jwt.secret`) and `PlatformOperatorDetailsService` (a `UserDetailsService` over `PlatformOperatorRepository`), the two pieces EMB-PC-04's `/platform/**` security filter chain will need to authenticate platform operators independently of tenant auth.

## Modified Files
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformJwtService.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformOperatorDetailsService.java` (new)
- `backend/src/main/java/com/vanter/ember/identity/service/EmberUserDetailsService.java` (modified — `@Primary`)
- `backend/src/main/resources/application.yml`, `application-dev.properties`, `application-prod.properties` (new `platform.jwt.*` keys)
- `backend/src/test/resources/application.properties` (new `platform.jwt.*` test values)
- `.env.example`, `.env` (new `PLATFORM_JWT_SECRET`/`PLATFORM_JWT_EXPIRATION_MS`)
- `backend/src/test/java/com/vanter/ember/platform/service/PlatformJwtServiceTest.java` (new)
- `backend/src/test/java/com/vanter/ember/platform/service/PlatformOperatorDetailsServiceTest.java` (new)

## What Changed?
- `PlatformJwtService` mirrors `identity/service/JwtService`'s API (`generateToken`/`extractSubject`/`extractClaim`/`isTokenValid`) but reads `@Value("${platform.jwt.secret}")`/`platform.jwt.expiration-ms`, with the same `<32 chars` fail-fast check. It deliberately has no `extractTenantId` — platform tokens never carry a tenant claim and this service never touches `TenantContextHolder`.
- `PlatformOperatorDetailsService` mirrors `EmberUserDetailsService` but loads `PlatformOperator` rows by email; since `PlatformOperator` has no role column, it grants a single fixed `PLATFORM_ADMIN` authority.
- `platform.jwt.secret`/`platform.jwt.expiration-ms` wired the same way as `jwt.secret` (`${PLATFORM_JWT_SECRET}`, no fallback → fails boot if unset) in `application.yml` plus the `dev`/`prod` profile properties; `.env`/`.env.example` got a freshly generated, distinct secret (never reuse `JWT_SECRET` — that would defeat EMB-PC-04's mutual-exclusion-by-signing-key design). `backend/src/test/resources/application.properties` also needed a value, since `PlatformJwtService` is a `@Service` bean eagerly instantiated in every full Spring context, not just contexts that use it directly.
- **Unplanned fix required to keep the app bootable:** adding a second `UserDetailsService` implementation broke every full-context test with `NoUniqueBeanDefinitionException` — `SecurityConfig` and `JwtChannelInterceptor` both autowire `UserDetailsService` by interface type with no qualifier. Tried field-level `@Qualifier("emberUserDetailsService")` on both injection sites first, but confirmed via a full test run that Lombok's `@RequiredArgsConstructor` does not copy `@Qualifier` onto the generated constructor parameter, so it had no effect (verified by an unchanged NoUniqueBeanDefinitionException count/stack). Reverted that and instead marked `EmberUserDetailsService` with `@Primary`, so every existing by-type injection site keeps resolving to it unambiguously with zero call-site changes. Documented on `EmberUserDetailsService`'s Javadoc that EMB-PC-04's platform filter chain must inject `PlatformOperatorDetailsService` by its concrete type (not the `UserDetailsService` interface) to stay unambiguous without depending on this default.

## Why It Changed?
EMB-PC-04 (next task) needs both a way to mint/verify platform-operator tokens and a way to load a `PlatformOperator` by email for authentication — this task builds those two pieces in isolation so EMB-PC-04 can focus solely on wiring the `SecurityFilterChain`/auth filter. Keeping `PlatformJwtService` a straight duplicate of `JwtService` (rather than extracting a shared base class) matches the EMB-PC backlog's explicit design: platform and tenant auth are mutually exclusive by virtue of using *different signing keys*, not a shared code path with a claim check — so the two services should stay independent, not merged.

## Verification
- `./mvnw test`: **539/539 passing** (up from 530; +9 new tests: 6 `PlatformJwtServiceTest`, 2 `PlatformOperatorDetailsServiceTest`, 1 net from the `@Primary` fix requiring no new tests itself), `BUILD SUCCESS`. This full-context run is exactly what caught (and then confirmed the fix for) the `UserDetailsService` ambiguity — worth calling out since it's a case where the mandated full-suite verification step wasn't just a formality.
- No Flyway migration in this task, so no separate Postgres verification was needed.
- `pnpm run build`/`pnpm run lint`: not run — no frontend files touched this task.
