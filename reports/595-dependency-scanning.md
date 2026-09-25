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

## 4. What Changed?
- Dependabot, weekly, minor/patch grouped per ecosystem: maven (`backend`, `printing-agent`), npm (`frontend`, `landing`, `ember-hub/ui`, `printing-agent/ui`), cargo (both `src-tauri`), github-actions.
- Workflow `Dependency Security Scan` (PR, push to `main`, Monday cron): `audit-frontend` runs `pnpm audit --prod --audit-level high`; `audit-backend` runs OWASP dependency-check through the Maven plugin CLI (no `pom.xml` change), failing on CVSS >= 7, NVD data cached, HTML report uploaded.
- Neither job is a required check of the "EMBER - PROTECTION" ruleset, so findings show red but do not block merges.

## 5. Why It Changed?
Backend handles money and tenant data; a vulnerable library (JWT, deserialization, Spring) is a risk even if our code is right. Cheapest gap in the API-security checklist review.

Owner action: add repo secret `NVD_API_KEY` (free at nvd.nist.gov) or the first OWASP run may be very slow / rate-limited.

Verification: both YAML files parse; the workflow itself is validated by its first run on the PR. No app code or tests touched.
