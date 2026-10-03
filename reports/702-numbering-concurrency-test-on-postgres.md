# Report 702 — Numbering concurrency test runs on a real Postgres (flaky `test-backend`)

## 1. Identification
- **Report number:** 702
- **Current Task:** FIX-FLAKY-NUMBERING-TEST
- **Predecessor Task:** Report 701 — BACKUP-CRON-ENV

## 2. Objective
Stop `test-backend` from failing at random in CI. Every failure since BILL-NUMBERING (r687, #180) was the same test:
`DocumentNumberServiceIntegrationTest.concurrentIssuers_neverGetTheSameNumber_andLeaveNoGaps` ("Expected size: 40 but was: 36").

## 3. Modified Files
- `backend/pom.xml`
- `backend/src/test/java/com/vanter/ember/numbering/DocumentNumberServiceIntegrationTest.java`
- `PROGRESS.md`
- `reports/702-numbering-concurrency-test-on-postgres.md` — new

## 4. What Changed?
- `pom.xml`: test-scoped `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter` (versions managed by Spring Boot; Testcontainers 1.21.4).
- The test class now starts a `postgres:16` container (`@Container` + `@ServiceConnection`) and overrides the H2 dialect with `PostgreSQLDialect`. `@Testcontainers(disabledWithoutDocker = true)`: a machine without Docker skips the class instead of failing; CI (`ubuntu-latest`) has Docker.
- No production code changed.

## 5. Why It Changed?
- CI history: 0 `test-backend` failures in ~20 runs before #180; 4 failures in ~19 runs after it, all this test, alternating red/green on identical code.
- Reproduced locally with a temporary `@RepeatedTest(80)` copy (deleted): H2 failed ~1 in 80 alone and 8–15 in 80 under load (4 runs in parallel). Duplicates were the first numbers (a thread getting `[1, 4, 2, 5, 10]`), i.e. a waiter re-reading a stale row after the lock.
- Same copy on Postgres 16 (Docker): 0 failures in ~1,000 concurrent repetitions, including the same parallel load. The service is correct on the production database; the test only measured H2.
- Tried and reverted: replacing `SELECT ... FOR UPDATE` + increment with an atomic `UPDATE ... last_number + 1`. H2 still failed (2 of 4 runs), so changing billing code for it was not justified.

## Verification
- `./mvnw test -Dtest=DocumentNumberServiceIntegrationTest`: 5/5 on a Testcontainers Postgres (the logged `duplicate key ... document_counters_pkey` is the expected first-use race the service swallows).
- `./mvnw test`: 1803/1803, BUILD SUCCESS.
