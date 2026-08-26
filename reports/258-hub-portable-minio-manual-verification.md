# Report 258 — Ember Hub portable MinIO: manual verification (2 bugs found+fixed)

## 1. Identification
- **Report number:** 258
- **Task ID:** Manual end-to-end verification of the portable MinIO bootstrap (gap opened 2026-08-25, implemented in report 257)
- **Predecessor task:** report 257 (feat-hub-portable-minio-status)

## 2. Objective
Manually verify `PortableMinioBootstrap` against a real `minio.exe` binary end-to-end through the `HubDashboard` — the one item report 257 shipped without hardware/binary verification, same shape as the HUB-01-11 gap closed for Postgres.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableDatabaseBootstrap.java`
- `backend/src/main/java/com/vanter/ember/config/MinioConfig.java`

## 4. What Changed?

Two real bugs surfaced during verification, both found+fixed in-session (systematic-debugging: root cause confirmed with direct evidence before either fix):

**Bug 1 — `createdb -h localhost` hangs forever on a dual-stack Windows box (`PortableDatabaseBootstrap.ensureApplicationDatabaseExists`):** on first boot against a genuinely empty data directory, the Hub dashboard hung indefinitely at "iniciando" for both Postgres and MinIO. Root cause: this machine has an unrelated process (`wslrelay.exe`, WSL's own relay) already bound to `[::1]:5432` (IPv6 loopback); the freshly-started portable Postgres could only bind IPv4 `127.0.0.1:5432` (confirmed in `postgres.log`: "no se pudo enlazar a la dirección IPv6 '::1'"). `createdb -h localhost` resolves "localhost" via Windows' own getaddrinfo, which returned the IPv6 address first — `createdb.exe` connected to `wslrelay.exe` instead of our Postgres, which never speaks the Postgres wire protocol back, so the client hung on the server's startup response with no timeout. Confirmed via `tasklist`/`wmic` showing a live `createdb.exe` process and `netstat` showing the IPv6 listener owned by an unrelated PID. Fixed by hardcoding `-h 127.0.0.1` instead of `-h localhost`, matching what the server actually binds and what `PortableMinioBootstrap`'s health check already uses.

**Bug 2 — uploaded images 403 (`MinioConfig.ensureBucketExists`):** after fixing Bug 1 and completing a full dashboard start→login→category-image-upload cycle, the uploaded image never rendered. Confirmed via direct `psql` query (the `categories.img_url` row was a real MinIO URL) and a direct `curl` against that URL returning `403 AccessDenied`. Root cause: `ImageUploadService.uploadImage` hands out raw MinIO URLs (`minioProperties.getUrl() + "/" + bucket + "/" + object`), never presigned and never proxied through the backend — this requires the bucket to be anonymously readable. `MinioConfig`'s `ensureBucketExists` `ApplicationRunner` creates the bucket but never sets a policy; the regular Docker dev environment only "worked" because someone had manually set a public-read policy through MinIO's web console (port 9001) at some prior point — the Hub's portable MinIO runs with `MINIO_BROWSER=off` (report 257), so no console exists and no equivalent manual step was ever possible. Fixed by calling `minioClient.setBucketPolicy` with a standard anonymous `s3:GetObject` policy for the whole bucket, applied unconditionally on every boot (not just on first creation) so an already-existing private bucket — like the `lacocinamia` one created during report 256's testing — self-heals without any manual intervention.

**Verification performed** (via `mvnw clean package` + `java -jar` launches of the dashboard, real `minio.exe` binary downloaded by the user, against the already-provisioned `C:\ember-hub-test-2\` data directory from report 256):
- Dashboard "Iniciar" → all 3 status indicators (Postgres/MinIO/Servidor) reach "en ejecución"/"listo" without hanging.
- `minio.exe` runs as a real child process, `/minio/health/live` → 200, `MinioConfig` log confirms `MinIO bucket 'ember-media' created`/policy applied.
- Logged in as the existing admin (`fer1@lacocina.com`), created a category with an image — upload succeeded and, after the policy fix, the image rendered correctly on reload.
- "Detener" → graceful window-close (not force-kill) correctly triggered the shutdown hook: `tasklist` confirmed zero orphaned `postgres.exe`/`minio.exe` processes afterward, matching report 237's earlier fix for Postgres.

## 5. Why It Changed?
Closes the one remaining unverified claim from report 257 ("Not yet manually verified against a real `minio.exe` binary"). Both bugs are genuine portability/deployment gaps, not artifacts of the test setup — Bug 1 would hang any first-run Hub install on a machine with something else already bound to `::1` on the Postgres port (plausible on any dev machine running WSL, Docker Desktop, or another local Postgres), and Bug 2 would silently break every image upload on every real customer Hub install, since a real customer's MinIO console is deliberately disabled and nobody would ever manually set the bucket policy.
