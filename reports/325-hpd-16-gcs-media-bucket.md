# Report 325 — HPD-16: media object storage (GCS)

## 1. Identification
- **Report number:** 325
- **Task ID:** HPD-16 (Hosted Production Deployment — `docs/superpowers/plans/2026-08-28-hosted-production-deployment.md`, Task 16)
- **Predecessor task:** report 324 (HPD-19 — Cloudflare edge hardening)
- **Branch:** `main`

## 2. Objective
Provision the production media object store so image uploads have somewhere to
live, and hand the credentials to the deploy track (they are wired into
`ember-prod-env` at HPD-20). The plan specified Cloudflare R2 behind
`cdn.ember.vanter.net`.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/MinioProperties.java`
- `backend/src/main/java/com/vanter/ember/config/MinioConfig.java`
- `backend/src/main/resources/application-prod.properties`
- `backend/src/test/java/com/vanter/ember/config/MinioConfigTest.java`
- `deploy/.env.prod.example`
- `deploy/RUNBOOK.md`
- `docs/superpowers/plans/2026-08-28-hosted-production-deployment.md`
- `PROGRESS.md`
- `reports/325-hpd-16-gcs-media-bucket.md` (new)

Infrastructure provisioned by the operator (not in the repo): GCS bucket
`gs://ember-media-prod`, its `allUsers:objectViewer` binding, and a user-account
HMAC key.

## 4. What Changed?

### Three deviations from the plan's R2 design
Each was forced by a limit hit live during provisioning:

1. **GCS instead of Cloudflare R2.** Enabling R2 requires a payment method on the
   Cloudflare account. The GCP billing account is already funded and HPD-12
   already stood up GCS + an IAM path, so media reuses GCS, consumed through its
   **S3-compatible XML API** (`https://storage.googleapis.com` + an HMAC key).
   The app's `minio-java` client is unchanged. Cost at Ember's scale (≤120 KB
   compressed JPEGs, `immutable` cache) is a few cents/month.

2. **User-account HMAC key, not a service-account key.** The organization
   enforces `constraints/iam.disableServiceAccountKeyCreation` (an HMAC key for
   an SA counts as an SA key), and the operator account has no
   `roles/orgpolicy.policyAdmin` at the org to override it (`orgpolicy.policyAdmin`
   is not grantable at project level). A **user-account HMAC key**
   (GCS → Settings → Interoperability → *Access keys for your user account*, after
   setting the default interop project to `ember-prod-vanter`) is not covered by
   that constraint. Trade-off: the credential authenticates as the operator's
   Google account (a project Owner) — acceptable for a single-operator SaaS, but
   image uploads break if that account is lost or its access changes. Rotation is
   the same Interoperability screen. **TODO:** migrate to a dedicated-SA HMAC key
   (`objectAdmin` on this bucket only) if the org constraint is ever lifted for
   this project.

3. **No `cdn.ember.vanter.net` custom domain.** Putting a pretty domain in front
   of GCS on the Cloudflare **Free** plan needs either an Origin Rule *Host
   Header* override (Pro-only — Free exposes only *Destination Port*) or a small
   proxy Worker. Both were declined to keep the deployment surface minimal, so
   `MINIO_PUBLIC_URL` points at the bucket URL directly:
   `https://storage.googleapis.com/ember-media-prod`. Browsers hit GCS without a
   Cloudflare cache in front — egress is GCS→internet (cents/month at this size).
   Optional follow-up: an `ember-cdn` Worker (`fetch` →
   `storage.googleapis.com/ember-media-prod$path`, `cf.cacheEverything`) bound to
   `cdn.ember.vanter.net`, then flip `MINIO_PUBLIC_URL`. Do it before real media
   exists to avoid rewriting absolute image URLs stored in the DB.

### Backend
- `MinioProperties` gains `boolean manageBucket` (default `true`). `MinioConfig`'s
  `ensureBucketExists` `ApplicationRunner` returns a log-only no-op when it is
  `false`. `application-prod.properties` sets `minio.manage-bucket=false` (and
  `${MINIO_MANAGE_BUCKET:false}` so it stays overridable). GCS's XML API has no
  S3 `PutBucketPolicy` endpoint, so the pre-existing per-boot
  `setBucketPolicy(publicReadPolicy(...))` call would only log a WARN every boot
  in prod; the app should also not try to create the bucket. Dev / the portable
  Hub keep `manageBucket = true` — local MinIO behaviour is unchanged.
- `MinioProperties.publicUrl` javadoc corrected — it may include a bucket/path
  segment (the dev default `.../ember-media` already does; prod now does too).
- `MinioConfigTest.minioPropertiesBindFromConfig` asserts `manageBucket` defaults
  to `true`. No new test method — one added assertion.

### Provisioning (operator, recorded in `deploy/RUNBOOK.md` "HPD-16 — executed")
- `gcloud storage buckets create gs://ember-media-prod` — Standard class,
  `us-central1`, uniform bucket-level access.
- `allUsers` → `roles/storage.objectViewer` on the bucket (anonymous object read;
  no listing).
- User-account HMAC key created in the Console.
- Verified: `gcloud storage cp` a healthcheck object with a custom `Cache-Control`,
  then `curl -sI https://storage.googleapis.com/ember-media-prod/healthcheck.txt`
  → `HTTP/2 200`, `cache-control: public, max-age=31536000, immutable`,
  `x-goog-storage-class: STANDARD`; object removed.

### Docs
- `deploy/.env.prod.example` — the `MINIO_*` block relabelled to GCS S3 XML API;
  `MINIO_URL=https://storage.googleapis.com`,
  `MINIO_PUBLIC_URL=https://storage.googleapis.com/ember-media-prod`.
- `deploy/RUNBOOK.md` — topology diagram updated (media no longer a Cloudflare
  column); new "HPD-16 — executed" block with the three DEVIATION notes, the
  provisioning commands, and the verification transcript; the Phase 4 bullet list
  entry rewritten.
- The plan file gets a `DONE … as GCS, not R2` note at the head of Task 16.
- `PROGRESS.md` — Current Execution State bullet + Task Queue checkbox; HPD-20's
  entry reworded from "real `MINIO_*`/R2" to the concrete GCS values.

## 5. Why It Changed?
HPD-16's deliverable is "a media bucket exists and its credentials are ready for
the deploy step." The plan assumed Cloudflare R2, but R2 needs a card on the
Cloudflare account, which the operator does not want to add. GCS reaches the same
end state through an S3-compatible API the app already speaks, at negligible cost,
on billing that is already set up. The organization's security baseline then
forced two further compromises (a user-account HMAC key; no custom domain on the
Free plan) — each is documented with its trade-off and a follow-up path rather
than blocking the task. The `minio.manage-bucket` flag exists because
`MinioConfig` was written for a MinIO server the app administers; against GCS the
bucket is operator-managed and the app must not attempt bucket creation or an
S3 bucket-policy write.

## 6. Verification
- `cd backend && ./mvnw test` → BUILD SUCCESS, **941 tests, 0 failures, 0 errors**
  (run twice — after the code change and after the doc edits; the only source
  change is one added assertion in `MinioConfigTest`).
- Bucket public read + custom `Cache-Control` confirmed by anonymous `curl`
  (transcript in the RUNBOOK block). The HMAC key itself is exercised end-to-end
  at HPD-20 when the app first boots against it and uploads a real image.
