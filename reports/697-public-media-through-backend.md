# Report 697 — Menu and category photos served by the backend (bucket can be closed)

## 1. Identification
- **Report number:** 697
- **Current Task:** PUBLIC-MEDIA-THROUGH-BACKEND (task 1 of 4 in the GCS hardening series)
- **Predecessor Task:** Report 696 — LANDING-COOKIE-CONSENT (report 695 is in PR #183)

## 2. Objective
Stop depending on a world-readable media bucket. Browsers fetched photos straight from
`storage.googleapis.com/ember-media-prod/…`, which required `allUsers` → `objectViewer`; that role
also allows anonymous **listing** (verified: `curl https://storage.googleapis.com/ember-media-prod`
returned every key). Photos now go through the backend, so the bucket can be made private.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/catalog/controller/PublicMediaController.java` — new
- `backend/src/test/java/com/vanter/ember/catalog/controller/PublicMediaControllerTest.java` — new
- `backend/src/main/resources/db/migration/V26__media_through_backend.sql` — new
- `backend/src/main/resources/application.yml`
- `deploy/RUNBOOK.md`
- `deploy/.env.prod.example`
- `PROGRESS.md`
- `reports/697-public-media-through-backend.md` — new

## 4. What Changed?
- `GET /public/media/{name}` (`/public/**` was already `permitAll`). The name must match
  `<lowercase-uuid>.jpg`, the only shape `ImageUploadService` produces, so ticket logos and anything else in the
  bucket cannot be requested. Reads the object with the existing `MinioClient`; answers `Cache-Control: max-age=31536000, public, immutable`, an `ETag`, `304` on a matching
  `If-None-Match`, `404` (`no-store`) for a missing key and `502` (`no-store`) for storage failures.
- `V26` rewrites `categories.img_url` and `menu_items.image_url` that still start with
  `https://storage.googleapis.com/` to `${mediaPublicUrl}/<uuid>.jpg`. The placeholder is wired in
  `application.yml` (`spring.flyway.placeholders.mediaPublicUrl`) to `MINIO_PUBLIC_URL`, the same
  value new uploads get. Idempotent; dev and Hub rows (other prefixes) are untouched.
- RUNBOOK: documents the wrong "no listing" comment, the new rollout order and the rollback; `.env.prod.example` sets
  `MINIO_PUBLIC_URL=https://api.ember.vanter.net/v1/public/media` (the API lives under the `/v1/` context path).

## 5. Why It Changed?
- Removes anonymous listing/direct GCS access once the bucket is closed, lets Cloudflare cache photos
  (`.jpg` is cached by default, the `immutable` header applies) and puts them behind the backend, where they can be rate limited.
- Extra latency is only on the first request per photo and per Cloudflare location (estimated 50-150 ms, not measured).
- Rollout is staged on purpose: the bucket stays public until the new route is verified in production.

## Verification
- `PublicMediaControllerTest` 5/5 (serve + headers, 304, 404, 502, malformed names never reach the bucket).
- `V26` run twice on a scratch Postgres database (since dropped): GCS rows rewritten, NULL/empty/dev rows untouched, second run no-op.
- Full backend `./mvnw test`: 1803/1803.
- NOT done: real request through Cloudflare (`cf-cache-status: HIT`), latency measurement, closing the bucket — those are deploy-time steps in the RUNBOOK.
- Series: task 2 downloads bucket listing (`legacyObjectReader`; the Caddy proxy at `downloads.ember.vanter.net` reads it anonymously, so it must stay readable), task 3 close `ember-media-prod` after deploy, task 4 dedicated service-account HMAC key.
