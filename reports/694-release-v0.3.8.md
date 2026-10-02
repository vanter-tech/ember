# Report 694 — RELEASE-V0.3.8

## 1. Identification
- **Report number:** 694
- **Task ID:** RELEASE-V0.3.8
- **Predecessor task:** report 693 — MODAL-ENTRY-ANIMATION

## 2. Objective
Ship reports 687-693 as `v0.3.8`: squash PR to `main`, tag, Hub installer, deploy commands.

## 3. Modified Files
- `backend/pom.xml` (0.3.7 → 0.3.8, inside the squashed PR)
- `reports/694-release-v0.3.8.md`, `PROGRESS.md`

## 4. What Changed?
- PR #180 `release/v0.3.8` squash-merged into `main` as `34a9bde9` (one commit: per-tenant document numbering `V25`, shift audit views, analytics and ops fields, Corte Z breakdown, animated modals, bump to 0.3.8). CI: lint-backend, test-backend, lint-frontend, lint-gateway and build-hub green; build-print-agent reported no conclusion.
- Hub installer `ember-hub/dist/EmberHubSetup-0.3.8.exe`, 190,006,975 bytes, built from the PR's tree (version read from the pom).
- Tag `v0.3.8` to be created on `34a9bde9` by the user (the push to origin from the agent session is blocked by the permission classifier; the branch push, the PR creation and the checkout were run by the user).

## 5. Why It Changed?
Everything since `v0.3.7` was only on local branches; this puts it on `main`, builds the backend image through the tag, and gives the Hub a matching installer.

## Deploy result (2026-10-02)
- Backend image `0.3.8` built from tag `v0.3.8` (run 37046463533, success); `deploy.sh 0.3.8` run by the user.
- Prod `flyway_schema_history` after the deploy: `V25` applied, every row `success = t`.
- Tag `v0.3.8` points at the docs commit `de53e4a1` (same code as the squash `34a9bde9`), like `v0.3.6`; that commit only reaches `main` once the docs PR is merged.
- Hub installer published by the user with `publish-installer.sh hub 0.3.8`.

## Deploy commands (as given)
```
./deploy/deploy.sh 0.3.8        # no "v"; V25 runs on boot, never pre-run by hand
./deploy/publish-installer.sh hub 0.3.8 EmberHubSetup-0.3.8.exe
```
Frontend and landing deploy by themselves from `main` through Cloudflare. Check `flyway_schema_history` on prod first (V24 was verified on v0.3.6); V25 is idempotent.
