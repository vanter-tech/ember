# Report 599 — DEPENDABOT-IGNORE-MINIO

## 1. Identification
- Report: 599
- Task ID: DEPENDABOT-IGNORE-MINIO
- Predecessor: FIX-MINIO-REVERT (report 598)

## 2. Objective
Stop Dependabot from reopening a Maven PR for every new `io.minio:minio` patch.

## 3. Modified Files
- `.github/dependabot.yml`

## 4. What Changed?
- The `io.minio:minio` ignore entry no longer limits `update-types`: Dependabot now skips every version of it. `minio` is updated by hand.

## 5. Why It Changed?
After pinning `minio` to 8.5.12 (report 598), Dependabot kept proposing the next patch (8.5.17, PR #167), so the Maven branch looked like it never finished. Manual bumps also avoid a repeat of the 8.6.0 / okhttp 5 breakage.

Verified before this change: PR #167 (only `minio` 8.5.12 -> 8.5.17 in `backend/pom.xml`) passes backend `./mvnw test` 1599/1599 with the reports wiped first. Merge #167 BEFORE this PR, otherwise Dependabot closes #167 as ignored.

Verification: YAML parses; effect confirmed only after merge.
