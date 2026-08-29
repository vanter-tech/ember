# Report 272 — HPD-01: Resolve image URLs from a configurable public base

## 1. Identification
- **Report number:** 272
- **Current Task ID:** HPD-01 (Hosted Production Deployment, Phase 1)
- **Predecessor Task:** Report 271 (HEARTBEAT-07 — Hub dashboard license status line)
- **Plan:** `docs/superpowers/plans/2026-08-28-hosted-production-deployment.md` — Task 1
- **Branch:** `feat/hosted-production-deployment`

## 2. Objective
Persist image URLs built from one configurable public base (CDN / R2 custom
domain) instead of the MinIO S3 API endpoint, so the same code serves dev
(`localhost:9000/ember-media`) and prod (`https://cdn.ember.vanter.net`, no
bucket segment). Collapse the three-way bucket-name drift in the callers, make
`deleteImage` null-safe, and stamp an immutable `Cache-Control` header on
uploads. Also unblocks Ember Hub's portable-MinIO image serving (report 258).

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/MinioProperties.java`
- `backend/src/main/java/com/vanter/ember/catalog/service/ImageUploadService.java`
- `backend/src/main/java/com/vanter/ember/catalog/service/CategoryService.java`
- `backend/src/main/java/com/vanter/ember/catalog/service/MenuItemService.java`
- `backend/src/main/resources/application.yml`
- `.env.example` (repo root — the plan named `backend/src/main/resources/.env.example`, which does not exist)
- `backend/src/test/java/com/vanter/ember/catalog/service/ImageUploadServiceTest.java`
- `backend/src/test/java/com/vanter/ember/catalog/service/CategoryServiceTest.java` (call-site stub)
- `backend/src/test/java/com/vanter/ember/catalog/service/MenuItemServiceTest.java` (call-site stubs + dead mock)

## 4. What Changed
- **`MinioProperties`** — added `private String publicUrl;` (Lombok `@Data`
  generates `getPublicUrl()`).
- **`ImageUploadService.uploadImage`** — signature changed from
  `uploadImage(MultipartFile, String bucketName)` to `uploadImage(MultipartFile)`.
  The bucket for `putObject` now always comes from `minioProperties.getBucket()`.
  The `PutObjectArgs` builder gained
  `.headers(Map.of("Cache-Control", "public, max-age=31536000, immutable"))`.
  The return value is now `minioProperties.getPublicUrl() + "/" + objectName`
  (no bucket segment).
- **`ImageUploadService.deleteImage`** — returns early on `null`/blank input;
  derives the object name from the last path segment of the URL
  (`path.substring(path.lastIndexOf('/') + 1)`) instead of splitting on `/` and
  assuming `parts[1]`/`parts[2]`; the bucket comes from `minioProperties.getBucket()`.
- Removed the unused private `extension(String)` helper (grep confirmed no
  references anywhere in `catalog/`).
- **`CategoryService`** — both `uploadImage(request.getImage(), "ember-media")`
  calls became `uploadImage(request.getImage())`.
- **`MenuItemService`** — `uploadImage(image, minioProperties.getBucket())` and
  `uploadImage(request.getImageUrl(), "ember-media")` became one-arg calls; the
  now-unused `private final MinioProperties minioProperties;` field and its
  import were removed.
- **`application.yml`** — added
  `public-url: ${MINIO_PUBLIC_URL:http://localhost:9000/ember-media}` under the
  `minio:` block (dev-compatible default; dev MinIO serves at `<url>/<bucket>`).
- **`.env.example`** — added `MINIO_PUBLIC_URL=http://localhost:9000/ember-media`
  under the MinIO section (key only, dev default; prod value is
  `https://cdn.ember.vanter.net`).
- **Tests:**
  - `ImageUploadServiceTest` rewritten to 6 cases: URL under the configured
    public base (no bucket segment), immutable `Cache-Control` header, invalid
    MIME rejected, oversize file rejected, `deleteImage` uses the last path
    segment + configured bucket, `deleteImage` ignores `null`/blank.
  - The plan's `Cache-Control` assertion read `PutObjectArgs.extraHeaders()`;
    the MinIO SDK's `.headers(Map)` populates `headers()`, so the assertion was
    changed to `captor.getValue().headers().get("Cache-Control")`. The header is
    still sent on the PUT — only the test accessor was wrong.
  - `CategoryServiceTest` / `MenuItemServiceTest` — the 2-arg `uploadImage`
    stubs (`any(), anyString()` / `any(), eq("ember-media")` / `any(), any()`)
    became 1-arg; removed the now-dead `@Mock MinioProperties minioProperties`
    (+ its stubs and import) from `MenuItemServiceTest` since `MenuItemService`
    no longer depends on it (strict stubbing would have failed on it); removed
    the now-unused `anyString` / `eq` static imports.

## 5. Why It Changed
`uploadImage` previously returned
`minioProperties.getUrl() + "/" + bucketName + "/" + objectName` — the S3 API
endpoint with a bucket path segment. Against Cloudflare R2 fronted by
`cdn.ember.vanter.net` the browser-facing URL is neither the S3 endpoint nor
does it carry a bucket segment, so persisted URLs would be unreachable in prod.
Callers also disagreed on the bucket name: `CategoryService` and
`MenuItemService.update` passed the literal `"ember-media"`, `MenuItemService.create`
passed `minioProperties.getBucket()`. One configurable base (`minio.public-url`)
plus "bucket always from config" removes both problems and keeps dev behaviour
identical via the default. The immutable `Cache-Control` header lets the CDN and
browsers cache images forever (object names are UUIDs, so they never change).
The `deleteImage` hardening removes an `ArrayIndexOutOfBoundsException` risk when
a stored URL has no bucket segment (the new prod shape).

## 6. Verification
- `cd backend && ./mvnw test -Dtest=ImageUploadServiceTest,CategoryServiceTest,MenuItemServiceTest`
  → 25 tests, 0 failures.
- `cd backend && ./mvnw test` → **893 tests, 0 failures, BUILD SUCCESS**
  (baseline 891 + 2 new `ImageUploadServiceTest` cases).
