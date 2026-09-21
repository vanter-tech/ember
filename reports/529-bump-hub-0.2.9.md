# Report 529 — Hub / backend version bump to 0.2.9

## 1. Identification
- **Report number:** 529
- **Task ID:** RELEASE-0.2.9 (bump only)
- **Predecessor:** report 528 (FEAT-REAL-RECEIPT)

## 2. Objective
Version the release that carries the real receipt, so the Hub installer does not overwrite the published 0.2.8.

## 3. Modified Files
- `backend/pom.xml` — `0.2.8` → `0.2.9` (names `EmberHubSetup-<version>.exe` and the backend image tag)
- `reports/529-bump-hub-0.2.9.md`

## 4. What Changed?
Only the `<version>` line.

## 5. Why It Changed?
Two releases sharing a version overwrite each other in `gs://ember-downloads-prod`. Release order: tag `v0.2.9` and deploy the cloud (it renders receipts with the same renderer), then publish the Hub installer.
