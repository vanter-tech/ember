# Report 660 — RELEASE-V0.3.4

## 1. Identification
- Report: 660
- Task ID: RELEASE-V0.3.4
- Predecessor: 659 (PLAN-EXPIRY-ALERT-10-DAYS)

## 2. Objective
Commit r634-659, push `main`, rebuild the Ember Hub installer, tag `v0.3.4` and hand over the deploy commands.

## 3. Modified Files
- `backend/pom.xml` (0.3.3 -> 0.3.4)
- `frontend/src/layouts/AdminLayout.test.tsx` (mock gained `cashDrawerService`)
- r634-659 work committed as one commit (`227fed76`, 164 files: cash drawer V19-V22, admin password reset, force-close, prolong shift, cent rounding, plan tab + console renewal V23).
- Excluded on purpose (still untracked/modified locally): `landing/CLAUDE.md` (Windows symlink artifact), `backend/mvnw.cmd` (line endings only), `docs/legal/`, `docs/superpowers/` plans/specs.

## 4. What Changed?
Release commits `227fed76` (features) and `d7acc2e1` (version bump). `ember-hub/dist/EmberHubSetup-0.3.4.exe` built with `ember-hub/build-installer.ps1` (exit 0, ~190 MB). `v0.3.3` already existed on the remote, so the new tag is `v0.3.4`.

## 5. Why It Changed?
Ship the accumulated work. The pom version names the Hub installer and must differ per release.

Verification: frontend build/lint clean; backend `./mvnw test` 1696 with 1 error in `PortableMinioBootstrapCredentialsIntegrationTest` (MinIO "server not initialized", passes 2/2 alone: known flake); the 3 `AdminLayout` test failures were a missing mock and are fixed (4/4). `MenuJoin.test.tsx` "authenticated: submitting the name joins via the QR token" fails deterministically (also alone), untouched by this work and already failing on `main`; NOT fixed. The Hub installer was not installed or run.
