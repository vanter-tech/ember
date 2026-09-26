# Report 596 — DEPENDENCY-UPGRADE

## 1. Identification
- Report: 596
- Task ID: DEPENDENCY-UPGRADE
- Predecessor: DEPENDENCY-SCANNING (report 595)

## 2. Objective
Fix the vulnerable dependencies the new CI scan found (backend: 45 CVEs with CVSS >= 7; frontend: 8 high).

## 3. Modified Files
- `backend/pom.xml`
- `backend/src/main/java/com/vanter/ember/config/SortParamGuard.java` (new)
- `backend/src/test/java/com/vanter/ember/config/SortParamGuardTest.java` (new)
- `frontend/package.json`
- `frontend/pnpm-lock.yaml`

## 4. What Changed?
- Spring Boot 3.5.14 -> 3.5.16 (latest 3.5.x): jackson-databind 2.21.4, spring-security-core 6.5.11, spring-data-jpa 3.5.13, postgresql 42.7.11, spring-core 6.2.19.
- `tomcat.version` pinned to 10.1.60 (Boot 3.5.16 still manages 10.1.55; the Tomcat auth-bypass / security-constraint CVEs are fixed from 10.1.58).
- `SortParamGuard`: interceptor answering 400 to any `?sort=` that is not `property[,asc|desc]`. Eight controllers take a raw `Pageable`, so `sort` was client-controlled (Spring Data JPA Sort validation bypass, CVE-2026-47834). 3 unit tests.
- Frontend: axios ^1.20.0, react-router-dom ^7.18.4, vite ^8.3.1 (+ lockfile). `pnpm audit --prod` now clean; remaining findings are dev-only tooling (vitest, js-yaml, brace-expansion, browserslist).

## 5. Why It Changed?
Real advisories, not scanner noise for the frontend; for the backend most CVEs concern features Ember does not use (JMS, XsltView, SAML/WebAuthn/DPoP, X.509, digest auth, polymorphic Jackson typing). Known remaining gap: Spring Framework 6.2 has no patched release (latest 6.2.19, advisories cover up to it); the fix is on 7.x / Boot 4, out of scope. Expect some `spring-core` CVEs to keep showing in the OWASP scan.

Verification: backend `./mvnw test` 1599/1599, checkstyle clean; frontend `pnpm run build` clean, lint 0 errors, `test:run` 245/246 (pre-existing `MenuJoin` failure), `pnpm audit --prod` clean. OWASP rescan pending (CI on the PR).
