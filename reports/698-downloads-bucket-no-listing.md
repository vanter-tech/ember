# Report 698 — Downloads bucket: stop anonymous listing

## 1. Identification
- **Report number:** 698
- **Current Task:** DOWNLOADS-BUCKET-NO-LISTING (task 2 of 4 in the GCS hardening series)
- **Predecessor Task:** Report 697 — PUBLIC-MEDIA-THROUGH-BACKEND

## 2. Objective
`gs://ember-downloads-prod` is public on purpose (Hub and agent installers), but its `allUsers` →
`roles/storage.objectViewer` binding also let anyone list it: `curl https://storage.googleapis.com/ember-downloads-prod`
returned all 19 files, including every old installer version. Keep downloads public, remove listing.

## 3. Modified Files
- `deploy/RUNBOOK.md`
- `PROGRESS.md`
- `reports/698-downloads-bucket-no-listing.md` — new

## 4. What Changed?
- In Cloud Shell, run by the user: added `allUsers` → `roles/storage.legacyObjectReader` (object get, no list), then removed `roles/storage.objectViewer`, in that order so there was no gap.
- RUNBOOK records the commands, the verification, the rollback and the rule that any new public bucket uses `legacyObjectReader`.
- No application code changed: the landing, the printing settings and `publish-installer.sh` use exact object names (`…-latest.exe`), and the Caddy proxy at `downloads.ember.vanter.net` only does GETs of one object.

## 5. Why It Changed?
- Listing exposed the whole release history and the bucket layout to anyone, which is the enumeration step of the public-bucket attacks. Downloads by name are unaffected.

## Verification (reported by the user, 2026-10-02)
- `curl https://storage.googleapis.com/ember-downloads-prod` → `403`.
- `https://downloads.ember.vanter.net/EmberAgentSetup-latest.exe` → `200`; `…/EmberHubSetup-latest.exe` → `200`.
- Not done: removing old installer versions; they remain downloadable if the exact name is guessed (installers only, no data).
- Next: task 3 close `ember-media-prod` (only after the r697 deploy is verified), task 4 dedicated service-account HMAC key.
