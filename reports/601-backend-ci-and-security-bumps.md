# Report 601 — BACKEND-CI-AND-SECURITY-BUMPS

## 1. Identification
- Report: 601
- Task ID: BACKEND-CI-AND-SECURITY-BUMPS
- Predecessor: REMOVE-DEPENDABOT-AND-SCAN (report 600, PR #175 — not necessarily merged yet)

## 2. Objective
Make CI compile and test the backend, and take the two vulnerable libraries that do have a fix.

## 3. Modified Files
- `.github/workflows/lint.yml`
- `backend/pom.xml`

## 4. What Changed?
- New `test-backend` job in `lint.yml`: `./mvnw -B test` (Java 17, Maven cache) in `backend/`. Tests run on in-memory H2, so no services are needed. Not added to the ruleset's required checks.
- `postgresql.version` 42.7.13 (Boot managed 42.7.11; the channel-binding downgrade advisory covered up to 42.7.11) and `log4j2.version` 2.26.1 (Boot managed 2.24.3) properties in `backend/pom.xml`; `dependency:list` confirms the resolved versions.

## 5. Why It Changed?
Before, nothing compiled or tested the backend on PRs (Checkstyle does not compile), so the Dependabot bump of `minio` to 8.6.0 broke `main` while every check was green (report 598). The two bumps are the only libraries from the last vulnerability scan that had a newer release; the others (`spring-core`/`spring-security-core` 6.x, `kotlin-stdlib`) have no fixed release outside Boot 4.

Verification: backend `./mvnw test` 1599/1599 (surefire reports wiped first), `mvnw` is executable in git (100755), YAML parses; the CI job itself is proven by its first run on the PR.
