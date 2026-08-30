# Report 285 — HPD-13: secret store + first stack bring-up

## 1. Identification
- **Report number:** 285
- **Current Task ID:** HPD-13
- **Predecessor Task:** HPD-12 (report 284 — GCS backup bucket, SA role, disk snapshots)
- **Branch:** `feat/hpd-13-secrets-bringup` (stacked on `feat/hpd-12-backups` → `feat/hpd-11-runbook`, off `main` `8fd4a9e`)

## 2. Objective
Bring the hosted production stack up for the first time on `ember-prod`: publish the
backend image, store the full `.env` body as one Secret Manager secret, run
`deploy.sh`, and prove Flyway and the nightly backup work against real infrastructure.
`cloudflared` + R2 remain on placeholder values until the Cloudflare track (HPD-20).

## 3. Modified Files
- `deploy/docker-compose.prod.yml` — health-check URL `:8080/v1/actuator/health` →
  `:8081/actuator/health`.
- `deploy/deploy.sh` — same fix in the health-gate poll loop.
- `deploy/RUNBOOK.md` — `#### HPD-13 — executed 2026-08-29` provisioning block; the
  `## Secret rotation` section filled in; "Routine deploy" health URL corrected.
- `PROGRESS.md` — Last Completed Task (report 285), Current Active Task → HPD-14,
  System health, HPD-13 checkbox ticked.
- `reports/285-hpd-13-secrets-bringup.md` — this report.

## 4. What Changed?

### Code / deployment artifacts
- **Health-check port fix.** HPD-03 set `management.server.port=8081` in
  `application-prod.properties`. A separate management port does not inherit
  `server.servlet.context-path` (`/v1/`), so actuator health in prod is at
  `http://localhost:8081/actuator/health`, not `:8080/v1/actuator/health`. The prod
  Compose healthcheck (HPD-05) and the `deploy.sh` health gate (HPD-07) both still
  used the old URL, which would fail the gate on every deploy even with a healthy app.
  Both now poll `:8081/actuator/health`. Confirmed live — `deploy.sh` reported
  `app healthy`.

### Infrastructure (executed live by the operator)
- **Image published.** Pushed tag `v0.1.0` → `backend-image.yml` (HPD-08) built and
  pushed `ghcr.io/vanter-tech/ember-backend:0.1.0` + `:latest` (build ~2m30s, green).
- **Registry auth on the VM.** The org blocks public container packages, so the GHCR
  package stays **private**. The VM authenticates once with a classic PAT scoped to
  `read:packages`:
  `sudo docker login ghcr.io -u <user> --password-stdin` over IAP SSH. Credentials
  persist in `/root/.docker/config.json`, which `sudo docker compose pull` uses.
- **Secret `ember-prod-env`.** Assembled the full `.env` body from
  `deploy/.env.prod.example` and stored it as one Secret Manager secret
  (`--replication-policy=automatic`):
  - `SPRING_DATASOURCE_PASSWORD` = `openssl rand -hex 24`
  - `JWT_SECRET`, `PLATFORM_JWT_SECRET` = `openssl rand -base64 48`
  - `HUB_LICENSE_PRIVATE_KEY` = base64 of a fresh 2048-bit RSA PKCS8 DER key; the
    matching public key uploaded to
    `gs://ember-backups-ember-prod-vanter/keys/hub-public-key.der`
  - `BACKUP_GCS_BUCKET=ember-backups-ember-prod-vanter`,
    `MINIO_PUBLIC_URL=https://cdn.ember.vanter.net`,
    `EMBER_CORS_ALLOWED_ORIGINS=https://app.ember.vanter.net`,
    `EMBER_RATELIMIT_TRUSTED_PROXIES=172.16.0.0/12`
  - `TUNNEL_TOKEN`, `MINIO_URL`/`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` = `PLACEHOLDER`
    (real values added in HPD-20)
  The local `prod.env` + private-key files were `shred -u`'d immediately.
- **Deploy + verification.**
  - `./deploy/deploy.sh latest` → `>> pushing /opt/ember/.env …`, `compose pull`
    (incl. building the `backup` image), `up -d`, health gate → `app healthy`.
  - `flyway_schema_history`: `V1 baseline consolidated`, `V2 hub activations`,
    `V3 printer config windows queue`, `V4 printer config render mode`,
    `V5 cash shift expiry` — all `success = t`.
  - `docker compose exec -T backup /usr/local/bin/backup.sh` → dump uploaded to
    `gs://ember-backups-ember-prod-vanter/postgres/2026-08-29.dump.gz`
    (keyless SA auth via the metadata server, per HPD-06/12).
  - `cloudflared` crash-loops on the placeholder token — expected; the health gate
    only checks `app`.

### RUNBOOK
- `## Secret rotation` now documents the real flow: `versions access latest` → edit →
  `versions add` → `deploy.sh <tag>`, with the specific caveats for `JWT_SECRET` /
  `PLATFORM_JWT_SECRET` (invalidate sessions), `SPRING_DATASOURCE_PASSWORD` (only read
  on first volume init — `ALTER USER` on an existing DB), and `HUB_LICENSE_PRIVATE_KEY`
  (invalidates issued Hub licenses). This is also the HPD-20 mechanism for swapping the
  placeholders.

## 5. Why It Changed?
- **One secret, whole `.env`:** `deploy.sh` reads `ember-prod-env`'s latest version on
  every run and writes it to `/opt/ember/.env` (mode 600). Keeping the entire body in
  one versioned secret makes rotation and rollback atomic and keeps plaintext secrets
  off every disk except the VM's root-only `.env`.
- **Private image + VM login rather than public:** the image is just compiled
  bytecode, but the org policy forbids public container packages and loosening it is
  broader than this task needs. A one-time `read:packages` login on the VM is the
  minimal unblock and survives reboots.
- **Fresh Hub keypair now:** rotating `HUB_LICENSE_PRIVATE_KEY` later would invalidate
  every issued Hub `license.key`. No paying Hub is deployed yet, so generating it at
  first bring-up costs nothing; the public key is archived in the backup bucket for the
  Hub build to pick up.
- **Placeholders for `TUNNEL_TOKEN` / `MINIO_*`:** the Cloudflare track (HPD-15..19)
  has not produced the Tunnel token or R2 credentials. The plan's documented partial
  path is to bring up `postgres` + `app` (+ `backup`) now, verify health/Flyway/backup,
  and finish `cloudflared` + image storage in HPD-20. `MinioConfig.ensureBucketExists`
  is a try/catch `ApplicationRunner`, so a bad `MINIO_URL` only logs a WARN — the app
  boots fine.
- **Health-check port fix:** without it, `deploy.sh` fails its gate on every deploy
  against a perfectly healthy app, and Docker marks the `app` container `unhealthy`.
