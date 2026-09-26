# Report 597 — DEPENDABOT-QUIET

## 1. Identification
- Report: 597
- Task ID: DEPENDABOT-QUIET
- Predecessor: DEPENDENCY-UPGRADE (report 596)

## 2. Objective
Stop Dependabot flooding the repo (18 PRs in the first run, most of them major-version migrations).

## 3. Modified Files
- `.github/dependabot.yml`

## 4. What Changed?
- Maven, npm and cargo now ignore `version-update:semver-major` for every dependency: majors (Spring Boot 4, vitest 5, @astrojs/react 7, jsdom 30, oshi 7, minio 9, rand 0.10) are done by hand as their own task.
- Schedule weekly -> monthly; `open-pull-requests-limit` 5/3 -> 2 (per directory) for the three code ecosystems.
- github-actions keeps majors (they are runtime bumps, low risk), monthly, limit 3.
- Comment in the file explaining that `package-ecosystem: npm` is Dependabot's name for the JS ecosystem and that it updates the repo's `pnpm-lock.yaml`.

## 5. Why It Changed?
The first run opened 9 PRs (maven/cargo/actions) plus 9 more (npm across 4 directories, majors as individual PRs), each running CI incl. the ~19 min Windows Tauri job. Minor/patch stay grouped and automatic. Security updates are a separate Dependabot mechanism and are not blocked by `ignore`.

Verification: YAML parses; effect (Dependabot closing the now-ignored major PRs #149, #151-#153, #155-#162) is confirmed only after merge.
