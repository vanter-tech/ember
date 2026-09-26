# Report 600 — REMOVE-DEPENDABOT

## 1. Identification
- Report: 600
- Task ID: REMOVE-DEPENDABOT
- Predecessor: DEPENDABOT-IGNORE-MINIO (report 599)

## 2. Objective
Remove Dependabot version updates from the repo (owner decision: too much noise / too much review overhead).

## 3. Modified Files
- `.github/dependabot.yml` (deleted)

## 4. What Changed?
- Deleted the Dependabot config added in reports 595/597/599. No more automatic version-update PRs.
- `.github/workflows/security-scan.yml` is untouched (pnpm audit + OWASP dependency-check keep running on PRs, pushes to `main` and Mondays).

## 5. Why It Changed?
The bot opened ~30 PRs in a day, several of them major migrations or breaking bumps (minio 8.6.0 broke `main`'s compile, springdoc 3 targets Boot 4), and the CI has no backend compile/test job to vet them.

Consequences: dependencies are now upgraded by hand; the scan is the only early warning. `audit-backend` stays red until `postgresql`/`log4j-api` are bumped and the unfixable, non-applicable CVEs (`spring-core`, `spring-security-core`, `kotlin-stdlib`) are suppressed with a justification and expiry date. GitHub's own Dependabot alerts / security updates are repo settings, separate from this file, and were not changed.
