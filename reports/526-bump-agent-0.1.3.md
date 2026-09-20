# Report 526 — Print agent version bump to 0.1.3

## 1. Identification
- **Report number:** 526
- **Task ID:** RELEASE-AGENT-0.1.3 (bump only)
- **Predecessor:** report 525 (AGENT-UI-HARDENING part 2)

## 2. Objective
Version the agent build that carries AGENT-UI-HARDENING (reports 524-525), so the test installer does not overwrite the published `0.1.2`.

## 3. Modified Files
- `printing-agent/pom.xml` — `0.1.2` → `0.1.3` (names `EmberAgentSetup-<version>.exe`)
- `PROGRESS.md`, `reports/526-bump-agent-0.1.3.md`

## 4. What Changed?
Only the `<version>` line. `printing-agent/build-installer.ps1` was run: `printing-agent/dist/EmberAgentSetup-0.1.3.exe` (48 MB) built from this branch.

## 5. Why It Changed?
Two releases sharing a version overwrite each other in `gs://ember-downloads-prod`. Ember Hub needs **no** rebuild: nothing under `backend/` or `ember-hub/` changed since `v0.2.8`, which already carries the signed heartbeat and the receipt routing.

**Note:** the inner Tauri bundle still reports `0.1.1` in its metadata; the installer name and jpackage version are `0.1.3`.
