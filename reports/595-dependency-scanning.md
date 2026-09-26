# Report 595 — DEPENDENCY-SCANNING

## 1. Identification
- Report: 595
- Task ID: DEPENDENCY-SCANNING
- Predecessor: WAITER-ADD-ITEM-MOBILE (report 594)

## 2. Objective
Get automatic warning about dependencies with known vulnerabilities (the CI only ran Checkstyle/ESLint).

## 3. Modified Files
- `.github/dependabot.yml` (new)
- `.github/workflows/security-scan.yml` (new)
- `.github/workflows/lint.yml` (rust-cache in `build-print-agent`)

## 4. What Changed?
- Dependabot, weekly, minor/patch grouped per ecosystem: maven (`backend`, `printing-agent`), npm (`frontend`, `landing`, `ember-hub/ui`, `printing-agent/ui`), cargo (both `src-tauri`), github-actions.
- Workflow `Dependency Security Scan` (PR, push to `main`, Monday cron): `audit-frontend` runs `pnpm audit --prod --audit-level high`; `audit-backend` runs OWASP dependency-check through the Maven plugin CLI (no `pom.xml` change), failing on CVSS >= 7, NVD data cached, HTML report uploaded.
- Neither job is a required check of the "EMBER - PROTECTION" ruleset, so findings show red but do not block merges.

## 5. Why It Changed?
Backend handles money and tenant data; a vulnerable library (JWT, deserialization, Spring) is a risk even if our code is right. Cheapest gap in the API-security checklist review.

Owner action: add repo secret `NVD_API_KEY` (free at nvd.nist.gov) or the first OWASP run may be very slow / rate-limited.

Verification: both YAML files parse; the workflow itself is validated by its first run on the PR. No app code or tests touched.

## First run findings and speed fixes
- First CI run: `audit-frontend` found 22 vulns (8 high, 14 moderate); `audit-backend` (13 min, NVD download) found high/critical CVEs in `tomcat-embed-core` and `spring-security-core`. Real findings, tracked as a separate dependency-upgrade task.
- NVD cache: `actions/cache` only saves when the job passes, and this job fails when CVEs exist, so the DB was never cached. Now `actions/cache/restore` + `actions/cache/save` with `if: always()`.
- `build-print-agent` took ~19 min compiling tauri-cli/Tauri from scratch; added `Swatinem/rust-cache@v2` (workspace `printing-agent/src-tauri`).
