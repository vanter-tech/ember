# Report 511 — Hotfix bump 0.2.6.1.1 + Hub installer rebuild

## 1. Identification
- **Report number:** 511
- **Task ID:** release prep, version `0.2.6.1.1`
- **Predecessor task:** report 510 — Hub plan gates disabled

## 2. Objective
Ship the report-510 fix (Hub onboarding wizard rejected by plan gates) as a new patch version and rebuild the Ember Hub installer with it.

## 3. Modified Files
- `backend/pom.xml` (`0.2.6.1` → `0.2.6.1.1`)
- `PROGRESS.md`
- `reports/511-hotfix-bump-0.2.6.1.1-hub-installer.md`

## 4. What Changed?
- `pom.xml` version bumped; `build-installer.ps1` reads it, so the installer, jar and jpackage app-version all become `0.2.6.1.1`.
- Merged `origin/main` (the squash of v0.2.6.1, PR #132) into `fix/hub-plan-gates`; the only conflicts were the two `PROGRESS.md` status lines (kept ours). The branch now differs from `main` only by the report-510 fix, the version bump and docs.
- Built `ember-hub/dist/EmberHubSetup-0.2.6.1.1.exe` (≈191 MB) with `ember-hub/build-installer.ps1` (exit 0, duplicate-migration guard passed).

## 5. Why It Changed?
`v0.2.6.1` is already tagged/released without the plan-gate fix, and a released tag can't be reused, so the fix needs a new version. Checked in the built artifacts: `backend/target/ember-0.2.6.1.1.jar` has `Implementation-Version: 0.2.6.1.1` (5 components built fine in jpackage/NSIS) and its `application-hub.yml` contains `ember.plans.enforced: false`. Not yet run on an installed Hub.
