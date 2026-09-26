# Report 600 — REMOVE-DEPENDABOT-AND-SCAN

## 1. Identification
- Report: 600
- Task ID: REMOVE-DEPENDABOT-AND-SCAN
- Predecessor: DEPENDABOT-IGNORE-MINIO (report 599)

## 2. Objective
Remove Dependabot version updates and the dependency security scan from the repo (owner decision: too much noise / too much review overhead).

## 3. Modified Files
- `.github/dependabot.yml` (deleted)
- `.github/workflows/security-scan.yml` (deleted)

## 4. What Changed?
- Deleted the Dependabot config added in reports 595/597/599. No more automatic version-update PRs.
- Deleted `.github/workflows/security-scan.yml` (pnpm audit + OWASP dependency-check, added in report 595): no more automated vulnerability scanning in CI.

## 5. Why It Changed?
The bot opened ~30 PRs in a day, several of them major migrations or breaking bumps (minio 8.6.0 broke `main`'s compile, springdoc 3 targets Boot 4), and the CI has no backend compile/test job to vet them.

Consequences: dependencies are upgraded by hand and nothing in CI reports known-vulnerable libraries any more. The last scan showed the backend still carrying non-applicable, unfixable CVEs (spring-core / spring-security-core 6.x need Boot 4; kotlin-stdlib; postgresql and log4j-api have newer releases). GitHub's own Dependabot alerts / security updates are repo settings, separate from these files, and were not changed. Manual audits remain possible with `pnpm audit` and the OWASP maven plugin.
