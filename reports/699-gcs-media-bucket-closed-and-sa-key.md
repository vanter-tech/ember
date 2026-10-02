# Report 699 — Media bucket closed and service-account HMAC key (GCS hardening tasks 3 and 4)

## 1. Identification
- **Report number:** 699
- **Current Task:** GCS-MEDIA-BUCKET-CLOSED-AND-SA-KEY (tasks 3 and 4 of 4)
- **Predecessor Task:** Report 698 — DOWNLOADS-BUCKET-NO-LISTING

## 2. Objective
Finish the GCS hardening: close `ember-media-prod` now that photos are served through the backend, and replace the
HMAC key of the operator's Owner account with one belonging to a dedicated service account limited to that bucket.

## 3. Modified Files
- `deploy/RUNBOOK.md`
- `PROGRESS.md`
- `reports/699-gcs-media-bucket-closed-and-sa-key.md` — new

## 4. What Changed?
- Release `v0.3.9` (pom bump `b24d14ab`, tag) deployed by the user after setting `MINIO_PUBLIC_URL=https://api.ember.vanter.net/v1/public/media`; `V26` applied, 0 `storage.googleapis.com` URLs left, photos load in the app.
- Bucket `ember-media-prod`: removed the remaining `allUsers` binding and enforced public access prevention. Listing, direct object and the old URLs answer `403`; `/v1/public/media/<uuid>.jpg` still answers `200` with the bucket private.
- HMAC key: created for `ember-media@ember-prod-vanter.iam.gserviceaccount.com` under a temporary project-scoped exception of `iam.disableServiceAccountKeyCreation` (the user holds `resourcemanager.organizationAdmin`), policy restored right after (`enforce: true`), key written into `ember-prod-env` version 5 without printing it, temp files shredded, redeployed. The user-account key was deactivated, tested and deleted.
- First attempt with the new key failed: uploads returned HTTP 500 (`AccessDenied` on `GET https://storage.googleapis.com/ember-media-prod?location=`). Fixed by granting the service account `roles/storage.legacyBucketReader` on the bucket; upload and uncached read then worked.
- RUNBOOK: documents the closure, the rotation procedure, the extra role, and that the policy exception can take ~20 minutes to take effect.

## 5. Why It Changed?
- A world-readable, listable media bucket and an Owner-level credential in the app's environment were the two exposures that motivated the series; both are gone.
- Honest limit: the VM's default service account has project `roles/editor`, so anyone who gets code execution on the VM can still reach every bucket through the metadata token. That is the next task (queued in `PROGRESS.md`); this key rotation only limits leaks of the secret from outside the VM.

## Verification (reported by the user, 2026-10-02)
- Bucket describe: `public_access_prevention: enforced`; listing and direct object `403`; backend route with cache bypass `200`.
- New photo upload from the admin panel works; old photos still load; the old key was deleted only after both passed.
- Not done: setting `region` in `MinioConfig` (would make `legacyBucketReader` unnecessary); disabling old `ember-prod-env` versions 1-4 (they hold the deleted user key; keep 4 until the next release is stable).
