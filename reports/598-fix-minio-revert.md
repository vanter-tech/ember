# Report 598 — FIX-MINIO-REVERT

## 1. Identification
- Report: 598
- Task ID: FIX-MINIO-REVERT
- Predecessor: DEPENDABOT-QUIET (report 597)

## 2. Objective
Restore a compiling `main` after merging the Dependabot PRs unverified.

## 3. Modified Files
- `backend/pom.xml`
- `.github/dependabot.yml`

## 4. What Changed?
- `io.minio:minio` back to 8.5.12. PR #164 (maven minor/patch group, 21 updates) moved it to 8.6.0, which brings okhttp 5.1.0; `dependency:tree` shows `okhttp` and `okio` but the okhttp classes are not on the compile classpath, so `MinioConfig` failed with `class file for okhttp3.HttpUrl not found` and `main` did not compile.
- `dependabot.yml`: ignore `io.minio:minio` minor updates until okhttp 5 is sorted by hand.

## 5. Why It Changed?
The other 20 updates of #164 are fine: with minio pinned, backend `./mvnw test` 1599/1599 (surefire reports wiped first, so not stale), `printing-agent` 99/99. Also checked on merged `main`: frontend build/lint clean and 245/246 (pre-existing `MenuJoin`), landing / ember-hub ui / printing-agent ui build and tests green, `cargo check` on `ember-hub/src-tauri` clean (#148).

Lesson: Dependabot PRs were merged before anyone built them; CI has no backend compile/test job (only Checkstyle), which is why #164 showed green. Follow-up: add a backend `./mvnw test` job to CI.
