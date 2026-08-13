# Report 28 — task-3.2: Remove unused `spring-kafka` dependency

## 1. Identification
- **Report Number:** 28
- **Task ID:** task-3.2
- **Predecessor Task:** task-3.1 (report 27 — externalize credentials to `.env`)

## 2. Objective
Remove the unused `spring-kafka` / `spring-kafka-test` dependencies from the backend build, aligning the actual classpath with the architectural rule in `CLAUDE.md` §1: all internal events flow through Spring `ApplicationEventPublisher`, and Kafka must not be used or configured.

## 3. Modified Files
- `backend/pom.xml`
- `backend/src/test/resources/application.properties`

## 4. What Changed?
1. **`backend/pom.xml`** — deleted two dependency blocks:
   - `org.springframework.kafka:spring-kafka` (compile scope, version managed by the Spring Boot BOM).
   - `org.springframework.kafka:spring-kafka-test` (test scope).

   No `<properties>` entry, `<dependencyManagement>` pin, or plugin configuration referenced Kafka, so nothing else in the POM required adjustment.

2. **`backend/src/test/resources/application.properties`** — removed the now-invalid autoconfiguration exclusion:

   ```properties
   # Disable Kafka autoconfiguration (not needed for tests)
   spring.autoconfigure.exclude=\
     org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
   ```

   This deletion is **mandatory, not cosmetic**. Spring Boot validates every entry in `spring.autoconfigure.exclude` at context startup and throws when a listed class is absent from the classpath. Removing the jar while keeping the exclusion would have failed the `ApplicationContext` bootstrap of every `@SpringBootTest` in the suite.

   The property held only that one value, so the whole block (and its comment) was removed rather than emptied.

### Verification
- Grep over `backend/src/**` confirmed **zero** Java sources import `org.springframework.kafka` or reference a `KafkaTemplate`/`@KafkaListener`; no `application.yml`, profile properties file, or compose manifest configured a broker.
- `cd backend && ./mvnw test` → **BUILD SUCCESS**, `Tests run: 367, Failures: 0, Errors: 0, Skipped: 0` (unchanged from the task-3.1 baseline).

## 5. Why It Changed?
- **Architectural truthfulness.** The repository name (`Java_SpringBoot_Kafka_Courses`) and the leftover dependency both implied an event-streaming backbone that does not exist. Ember is a modular monolith whose cross-module communication is 100% synchronous in-process publishing. A dependency that contradicts the architecture misleads every future reader and invites someone to "just add a `@KafkaListener`", silently introducing an async boundary the codebase makes no consistency guarantees across.
- **Attack surface and supply chain.** `spring-kafka` transitively pulls in the Kafka clients library and its serialization stack. Shipping deserialization machinery that no code path exercises is unnecessary CVE exposure and unnecessary patch-triage work for a multi-tenant product where the security posture is under active hardening (milestone 2).
- **Build weight and startup cost.** Dropping the jars trims the fat artifact and removes the autoconfiguration class Spring had to load and then explicitly exclude in every test context — the exclusion property existed purely to neutralize a dependency nobody wanted.
- **Sequencing.** Placed in milestone 3 (hardening/cleanup) after tenant isolation landed, so the dependency removal is verified against the full 367-test suite rather than a shifting baseline.
