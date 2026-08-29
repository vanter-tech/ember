# Ember — Hosted Production Deployment — Design

> Status: **design approved in brainstorming (2026-08-28)**, ready for
> implementation-plan. Sub-project of the "path to production" effort. Branch
> `feat/hosted-production-deployment` off `main` `040bb72`.

## 1. Motivation

Ember (the multi-tenant restaurant SaaS) has only ever run as the local dev
Docker Compose stack and as the on-prem **Ember Hub** build. This sub-project
stands up the **hosted / cloud SKU** on infrastructure the team controls,
reachable over the public internet, hardened enough for a first real restaurant
or a small pilot (a handful of tenants).

It is deliberately **not** a fully-managed cloud-native deployment. It is one
small VM running the Compose stack the team already has, fronted by Cloudflare,
with a documented, low-effort migration path to Cloud Run + Cloud SQL for when
paying volume justifies the cost and operational lift.

### Explicitly out of scope

- **Digital payment processing.** `PaymentService` still returns a `STUB-<uuid>`
  gateway ref (GATEWAY-01 is blocked on provider selection). Cash + manual
  registration only. No card flow ships here.
- **High availability / horizontal scale.** One backend instance. The in-memory
  STOMP broker and `@Scheduled` jobs assume exactly one instance — documented
  ceiling, not solved.
- **dev `ddl-auto=validate` switch + drift-reconciliation migrations.**
  Pre-existing tech debt (the V1 baseline carries Hibernate-generated vs
  hand-authored constraint-name drift). Independent of this work; the `prod`
  profile already pins `ddl-auto=validate`.
- **Transactional email infrastructure.** No `spring-boot-starter-mail` today.
  If the landing contact form or password-reset-by-email needs SMTP, that is its
  own task.
- **Load testing / capacity planning.**

### Related, sequenced around this work

- Self-run Prometheus + Grafana were removed on branch
  `feat/hub-packaging-installer` (report 278) — GCP Cloud Monitoring replaces
  them (§7). That branch also deduped a `spring-boot-starter-actuator` entry in
  `backend/pom.xml`. This sub-project does **not** re-touch the dev
  `docker-compose.yml` or `pom.xml` for that; it only adds a new prod compose
  file. If this branch merges to `main` before `feat/hub-packaging-installer`,
  expect a trivial "both deleted these lines" auto-merge on `docker-compose.yml`.
- Ember Hub sub-projects B1-E2E / B2 / A2(sync) / C / Fase 3 come **after** this
  deployment, except B1's manual VM verification which the maintainer runs in
  parallel.

### Division of labor

Two tracks, worked in parallel:

- **GCP track (maintainer):** VM provisioning + hardening, `deploy/` (prod
  compose, `deploy.sh`, RUNBOOK, backup), all backend config/code changes, the
  CI image build, Ops Agent monitoring, GCS backups + restore test.
- **Cloudflare track (colleague):** `ember.vanter.net` zone delegation, both
  Pages projects, the Tunnel, the R2 media bucket + `cdn.` domain, edge settings
  (HSTS, WAF, `/v1/auth/*` rate-limit), DNS records.

**Integration points** (Cloudflare-track outputs the GCP track consumes):
`TUNNEL_TOKEN`; R2 endpoint + scoped access key/secret + bucket name; the final
`api.` / `app.` / `cdn.` hostnames (all knowable up front). The GCP track can
provision the VM, land every backend change, and boot `postgres` + `app` with a
local health check **before** the Tunnel exists; wiring `cloudflared` and the R2
`minio.*` config are the last joins.

## 2. Guiding constraints

- Team of 2 (maintainer + AI). Minimise new surface area and ops burden.
- Reuse the existing `docker-compose.yml` and `Dockerfile`s as far as possible.
- Steady-state cost in the low tens of USD/month. The GCP $300 / 90-day credit
  is runway, not an architecture driver.
- Everything that can be free is free: Cloudflare Pages / Tunnel / R2, the GCP
  Ops Agent + Cloud Monitoring/Logging within their free tiers, and
  `ember.vanter.net` as a delegated subdomain of the company domain.
- A one-page, ~1-hour-per-step migration path to managed infra with **no
  application rewrite**.

## 3. Target topology

```
                          Internet
                             │
              ┌──────────────┴───────────────────────────┐
              │  Cloudflare — zone `ember.vanter.net`     │
              │  (delegated to the team's CF account via  │
              │   an NS record from `vanter.net`)         │
              │  · Universal SSL covers *.ember.vanter.net│
              │  · WAF, edge rate-limit, HSTS, analytics  │
              └───┬───────────┬───────────┬───────────┬───┘
                  │           │           │           │
        ember.vanter.net  app.ember…  api.ember…  cdn.ember…
                  │           │           │           │
             Pages:       Pages:      Cloudflare    R2 bucket
          ember-landing  ember-app    Tunnel        ember-media-prod
           (Astro,        (React SPA,    │          (public read)
            landing/)      frontend/)    │
                                         │  outbound-only; VM opens
                                         │  NO inbound ports
                        ┌────────────────┴─────────────────────┐
                        │  GCP Compute Engine  e2-medium       │
                        │  Ubuntu LTS + Docker, us-central1    │
                        │  firewall: SSH via IAP only          │
                        │                                      │
                        │  docker compose -f                   │
                        │    deploy/docker-compose.prod.yml:   │
                        │    · cloudflared  → app:8080         │
                        │    · app  (Spring, profile=prod)     │
                        │    · postgres  (never exposed)       │
                        │  + GCP Ops Agent (metrics + logs)    │
                        └──────────────────────────────────────┘
```

### Request flow — login from a waiter tablet

1. Tablet loads the SPA from **Pages** at `app.ember.vanter.net` (static assets
   from Cloudflare's edge; the GCP VM is not touched).
2. The SPA reads `/env-config.js` → `EMBW_API_URL = https://api.ember.vanter.net/v1`
   and issues `POST /v1/auth/login`.
3. Cloudflare receives it on `api.ember.vanter.net`, carries it through the
   **Tunnel** to `cloudflared` on the VM, which forwards to `app:8080` on the
   internal Docker network.
4. Spring validates against `postgres:5432` (traffic never leaves the VM),
   returns the JWT.
5. The tablet then opens `wss://api.ember.vanter.net/v1/ws`; the Tunnel upgrades
   the connection transparently. STOMP frames flow the same path.

`app.` and `api.` are separate origins → CORS applies (already built:
`EMBER_CORS_ALLOWED_ORIGINS`).

## 4. Components — GCP VM & Compose

### 4.1 The VM

| Aspect | Choice |
|---|---|
| Machine type | `e2-medium` (2 vCPU, 4 GB) — `e2-standard-2` (8 GB, ~$50/mo) is the next step if memory pressure shows |
| Image | Ubuntu 24.04 LTS |
| Region | `us-central1` (broad service availability, routes via Miami to Nicaragua ~50-70 ms); `us-east1` acceptable alternative |
| Boot disk | 30 GB `pd-standard` |
| Data disk | Start on the boot disk. Upgrade path: a separate 20 GB `pd-balanced` mounted at `/var/lib/ember/pgdata`, snapshot-independent — note in the RUNBOOK, do not build now |
| Swap | 2 GB swapfile (GC / Postgres spike safety) |
| Firewall | Deny all ingress except IAP SSH range `35.235.240.0/20`. No `80`/`443`. `cloudflared` needs only outbound `443`/`7844`. |
| Hardening | OS Login on; `unattended-upgrades` for security patches; deploy user non-root in the `docker` group |

### 4.2 `deploy/docker-compose.prod.yml`

Derived from the repo's `docker-compose.yml`, with:

- **`postgres`** — image `postgres:16`; **no host `ports`** (internal network
  only); named volume `ember_pgdata`; `POSTGRES_PASSWORD` from the VM `.env`;
  `restart: unless-stopped`; conservative tuning for a 4 GB box shared with the
  JVM (`shared_buffers=512MB`, `effective_cache_size=1536MB`,
  `max_connections=50`); healthcheck `pg_isready`.
- **`app`** — image `ghcr.io/vanter-tech/ember-backend:<tag>` (built in CI, §9,
  **not** built on the VM); `SPRING_PROFILES_ACTIVE=prod`;
  `env_file: /opt/ember/.env` (mode 600, written from Secret Manager by the
  deploy script — never in git, never in an image layer); the app port `8080`
  is **not** published (only `cloudflared` reaches it, by service name on the
  compose network); the management port `8081` (§5.2) is published on
  **loopback only** — `127.0.0.1:8081:8081` — so the host-level Ops Agent can
  scrape it while it stays off the Tunnel and off the LAN;
  `JAVA_TOOL_OPTIONS=-Xms512m -Xmx1536m`; `restart: unless-stopped`; healthcheck
  `wget -qO- http://localhost:8080/v1/actuator/health`.
- **`cloudflared`** — image `cloudflare/cloudflared:latest`;
  `command: tunnel run`; `TUNNEL_TOKEN` from the VM `.env`;
  `restart: unless-stopped`. Token-based (remotely-managed) tunnel: the public
  hostname `api.ember.vanter.net → http://app:8080` is configured in the
  Cloudflare Zero Trust dashboard and recorded in the RUNBOOK.
- **`backup`** — a small cron container (§8) that runs the nightly `pg_dump` +
  `rclone` to R2. Shares the compose network to reach `postgres:5432`.
- **Removed vs the dev compose:** `frontend` (→ Cloudflare Pages), `minio`
  (→ R2), `pgadmin`, `prometheus`, `grafana`.

The dev `docker-compose.yml` stays as-is for local work (minus the
prometheus/grafana already removed on the other branch).

## 5. Components — Backend prod configuration & code changes

### 5.1 Config (`application-prod.properties` + env), no behavioural risk

| Setting | Value | Why |
|---|---|---|
| `server.forward-headers-strategy` | `framework` | Behind the Tunnel, scheme/host come from `X-Forwarded-*` set by Cloudflare; without this, redirects and absolute URLs are wrong |
| `EMBER_CORS_ALLOWED_ORIGINS` | `https://app.ember.vanter.net` | SPA and API are separate origins; also gates the SockJS handshake |
| `EMBER_RATELIMIT_TRUSTED_PROXIES` | the Docker network CIDR | `cloudflared` is the immediate peer the app sees |
| `management.server.port` | `8081` | **Actuator on a separate port, not routed through the Tunnel** — see 5.2 |
| `management.endpoint.health.show-details` | `when-authorized` | Health internals not public even on the management port |

HSTS, "Always Use HTTPS", "Automatic HTTPS Rewrites" — enabled at the Cloudflare
edge, no app change.

### 5.2 Actuator exposure — separate management port

`SecurityConfig` currently `permitAll`s `/actuator/**` and `application.yml`
exposes `health,info,prometheus` with `show-details: always`. Routing
`api.ember.vanter.net` wholesale to `app:8080` would make
`/v1/actuator/prometheus` publicly readable.

**Fix:** move the management endpoints to `management.server.port=8081` (a
distinct connector), published on the loopback interface only
(`127.0.0.1:8081:8081`, §4.2). It is reachable from the VM host — where the GCP
Ops Agent scrapes `http://localhost:8081/actuator/prometheus` — but not from the
Tunnel (which forwards `:8080` only) nor from the LAN. The public uptime check
targets `https://api.ember.vanter.net/v1/actuator/health` — so `health` (and
`info`) must **also** remain on the main port; keep the existing
`/actuator/health` mapping on `:8080` (Spring serves health on both the main and
management ports by default when a management port is set — verify in an
integration test). No `SecurityConfig` rule gymnastics needed.

`micrometer-registry-prometheus` is **kept** (it feeds the Ops Agent scrape).

### 5.3 Media storage — object-key indirection (shared fix with Ember Hub)

`ImageUploadService.uploadImage()` today returns
`minioProperties.getUrl() + "/" + bucket + "/" + object` — an absolute URL built
from the S3 endpoint — and callers persist that string in the DB. Against R2 the
S3 endpoint is not the public host, and any future host/CDN change breaks every
stored row. This also bit the Hub (report 258).

**Change:**

- New property `minio.public-url` (prod: `https://cdn.ember.vanter.net`; dev:
  `http://localhost:9000`; Hub: `http://localhost:9000` or the LAN form).
- `uploadImage` persists **only the object key** (`<uuid>.jpg`).
- Wherever an image field is serialized into a DTO (category, menu item — locate
  the mappers), resolve `key` → `${minio.public-url}/${key}` at read time.
  A null/blank key resolves to null.
- `deleteImage` takes the key directly (drop the URL-parsing).
- Set `Cache-Control: public, max-age=31536000, immutable` on `putObject`
  (`PutObjectArgs` headers). Filenames are UUIDs, so immutable is safe; the CDN
  then caches forever and origin egress ≈ 0.
- `MinioConfig.ensureBucketExists` calls `setBucketPolicy` for public read;
  against R2 that call may fail (as on portable MinIO). It already `log.warn`s
  and continues — leave that, and document that R2 public read is configured
  out-of-band (Cloudflare R2 → custom domain + public access toggle).
- **Data note:** a fresh prod DB has no image rows; onboarding/seed data with
  image URLs (if any) must be re-pointed. Existing dev rows keep their old
  absolute URLs unless re-uploaded — non-blocking.

### 5.4 Rate limiter — real client IP

`AuthRateLimiterFilter` keys buckets by `(tenant, client IP)` and reads the
forwarded IP. Behind Cloudflare, prefer the single-value, harder-to-spoof
`CF-Connecting-IP` header when the immediate peer is trusted (falls back to the
current `X-Forwarded-For` parsing otherwise). Minimal, well-tested change.

### 5.5 Secrets

All from **GCP Secret Manager**, pulled by the deploy script into
`/opt/ember/.env` (mode 600). **Rotate every value that was ever in the
previously-tracked `.env`:** `JWT_SECRET`, `PLATFORM_JWT_SECRET`,
`SPRING_DATASOURCE_PASSWORD`, R2 keys (`MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`),
`TUNNEL_TOKEN`, `HUB_LICENSE_PRIVATE_KEY`.

> Rotating `HUB_LICENSE_PRIVATE_KEY` invalidates every Hub `license.key` already
> signed by the old key. No paying Hub is in the field yet, so rotate now; the
> matching `hub-public-key.der` bundled in the Hub installer must be regenerated
> from the new key.

`SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/ember`,
`ddl-auto=validate` (already the prod default). Flyway `V1`→`V5` run on first
boot against the empty database.

## 6. Components — Object storage (Cloudflare R2)

- Bucket **`ember-media-prod`** — public read via custom domain
  `cdn.ember.vanter.net` (R2 → Settings → Custom Domains + public access).
  Owned by the Cloudflare track.
- `pg_dump` archives go to **GCS**, not R2 (§8) — a private
  `gs://ember-backups-<project>` bucket, so backups stay entirely in the GCP
  track with no cross-account credentials.
- Backend talks to R2 through the existing MinIO Java SDK:
  - `minio.url = https://<accountid>.r2.cloudflarestorage.com` (S3 API endpoint)
  - `minio.access-key` / `minio.secret-key` = an R2 API token scoped to
    `ember-media-prod`, Object Read & Write
  - `minio.bucket = ember-media-prod`
  - `minio.public-url = https://cdn.ember.vanter.net` (§5.3)
- Free tier: 10 GB storage, 1 M Class-A + 10 M Class-B ops/month, **zero egress**.
  Ember's resized images (~50-150 KB each) put realistic early usage in the
  single-digit GB — effectively $0.

## 7. Components — Monitoring & alerting

- **GCP Ops Agent** on the VM: host metrics (CPU, memory, disk, network, swap),
  Docker container metrics, and a **Prometheus receiver** scraping
  `localhost:8081/actuator/prometheus` (JVM heap/GC, HTTP latency, Hikari pool)
  → Cloud Monitoring.
- **Cloud Logging:** the Ops Agent ships container stdout/stderr + syslog
  (50 GiB/month free, 30-day retention). `journald` + logrotate as local
  fallback. Optional log-based metric on ERROR rate.
- **Uptime check:** Cloud Monitoring → `GET https://api.ember.vanter.net/v1/actuator/health`
  every 5 min, multi-region.
- **Alert policies** (email + SMS): uptime check failing; CPU > 90 % for 10 min;
  disk > 85 %; memory > 90 %; Postgres or app container restart; (optional)
  ERROR-log-rate spike.
- **Cloudflare dashboard** covers edge/traffic analytics (requests, bandwidth,
  cache-hit ratio, WAF events) with no setup.
- No self-run Prometheus/Grafana (removed, report 278).

## 8. Components — Backups & disaster recovery

**Layer 1 — logical (`pg_dump`).** A small cron container in the prod compose
runs nightly (~08:00 UTC, pre-dawn Nicaragua): `pg_dump -Fc | gzip` →
`gcloud storage cp` to `gs://ember-backups-<project>/postgres/YYYY-MM-DD.dump.gz`.
Auth via the VM's attached service account (grant `roles/storage.objectAdmin`
scoped to that one bucket) — no access keys. Retention: 14 daily + 8 weekly
(Sunday), pruned by the same job (a GCS Object Lifecycle rule as a backstop).
Extract and share the rotation logic with the Hub's planned `pg_dump` backup
(spec §2.9).

**Layer 2 — disk snapshots.** A GCP resource policy on the VM disk: daily
snapshot, retain 7. Incremental → cents/month. Covers full-VM / OS / physical
disk recovery.

**Restore test.** Run once at setup and documented as a quarterly drill: pull
the latest dump, `pg_restore` into a throwaway `postgres` container, boot the
`app` image against it with `ddl-auto=validate`, confirm `/actuator/health` is
UP and a login succeeds.

**`deploy/RUNBOOK.md`** covers: VM-dead recovery (new VM from snapshot +
`docker compose up`), DB-corruption recovery (fresh `postgres` + `pg_restore`
latest dump), secret rotation, and how to `psql` in over IAP.

## 9. Components — Frontend deployment (two Cloudflare Pages projects)

### 9.1 `ember-landing`

- Root directory `landing/`, build `pnpm build`, output `dist`,
  env `NODE_VERSION=22` (the package requires `>=22.12`).
- Custom domain `ember.vanter.net` (optionally `www` → 301).
- Astro static, no SSR adapter. Update `astro.config.mjs` `site:` →
  `https://ember.vanter.net` (currently a `.com` placeholder) so sitemap and
  canonical URLs are correct.
- **Contact form (EMB-LP-16):** locate the current submit target. Options,
  decided at plan time: (a) `POST /v1/public/contact` — a new rate-limited
  public backend endpoint that persists a contact row; (b) a Cloudflare Pages
  Function `landing/functions/contact.ts` forwarding to a webhook/email;
  (c) a third-party form service. Recommendation: **(b)** for v1 (no backend
  coupling, no mail infra).
- Verify the landing builds standalone first (`cd landing && pnpm install &&
  pnpm build`) — there is a `landing/pnpm-workspace.yaml` that could make pnpm
  treat it as a workspace root.

### 9.2 `ember-app`

- Root directory `frontend/`, build `pnpm build && node scripts/gen-env-config.mjs`,
  output `dist`.
- Custom domain `app.ember.vanter.net`.
- **`env-config.js` (new task):** the Docker `entrypoint.sh` that writes it at
  container start does not run on Pages. Add `frontend/scripts/gen-env-config.mjs`
  that writes `dist/env-config.js` from Pages environment variables
  `EMBW_API_URL` / `EMBW_WS_URL` (set per project, and per branch if staging is
  added later). This preserves the runtime-indirection contract the SPA and the
  Hub both depend on. The repo `Dockerfile` / `entrypoint.sh` are untouched.
  Prod values: `EMBW_API_URL=https://api.ember.vanter.net/v1`,
  `EMBW_WS_URL=wss://api.ember.vanter.net/v1/ws`.
- Router `basename` stays `/` (Vite `BASE_URL`), identical to today's normal
  build.

### 9.3 Both

- Connect the Git repo; production branch provisionally `main` (a dedicated
  `release` branch/tag is one of the §16 open items). Enable path-scoped build
  skipping so a landing-only change does not rebuild the app and vice versa.
- Free tier: 500 builds/month/project, unlimited bandwidth/requests. Rollback =
  promote a previous deployment in the Pages UI.

## 10. Components — DNS & TLS

1. The `vanter.net` owner adds `ember  NS  <two nameservers from the team's
   Cloudflare account>` — subdomain delegation.
2. The team adds `ember.vanter.net` as a **zone** in their Cloudflare account.
   Universal SSL then covers `ember.vanter.net` **and** `*.ember.vanter.net` —
   `app.`, `api.`, `cdn.` are all first-level in this zone → free certs, **no
   Advanced Certificate Manager**.
3. Records (all proxied / orange-cloud):
   - `ember.vanter.net` → Pages `ember-landing`
   - `app` → Pages `ember-app`
   - `api` → Tunnel (`CNAME <tunnel-id>.cfargotunnel.com`, auto-created with the
     public hostname)
   - `cdn` → R2 custom domain
4. Edge: enable HSTS, "Always Use HTTPS", "Automatic HTTPS Rewrites", the
   managed WAF ruleset, and a rate-limiting rule on `/v1/auth/*`
   (defense-in-depth; the app limiter still runs).

## 11. Deploy pipeline (v1 — minimal)

- **Backend image** — GitHub Actions on git tag `v*` (or push to `release`):
  `mvnw -DskipTests package` → `docker build` → push to
  `ghcr.io/vanter-tech/ember-backend:<tag>` (GHCR is free for the org's repos
  and skips GCP Artifact Registry setup; Artifact Registry is the alternative if
  the team prefers staying entirely in GCP). Never build the JVM app on the
  4 GB VM.
- **VM deploy** — `deploy/deploy.sh`, run from a laptop or a second CI job with
  Workload Identity: refresh `/opt/ember/.env` from Secret Manager
  (`gcloud secrets versions access`), then over IAP SSH:
  `docker compose -f docker-compose.prod.yml pull && up -d`, then poll
  `/actuator/health` for up to N retries and fail loudly if the new `app`
  container is not healthy (a failed Flyway migration blocks boot; the script
  must catch that rather than leave `restart: unless-stopped` looping silently).
- **Frontend** — Cloudflare Pages auto-builds on push to the production branch.
- **Migrations** — run automatically on `app` container start
  (`baseline-on-migrate`, `V1`→`V5` present). No separate migration job for v1.

## 12. Cost summary (steady state, early production)

| Item | Monthly |
|---|---|
| GCP `e2-medium` (2 vCPU / 4 GB), `us-central1`, ~30 GB `pd-standard`, 24/7 | ~$25 |
| GCP disk snapshots (7 daily, incremental) | ~$0.30 |
| GCS `ember-backups` (dumps, ~14+8 retained) | ~$0.10 |
| GCP egress (API JSON) | ~$1-3 |
| GCP Ops Agent / Cloud Monitoring / Cloud Logging | $0 (free tier) |
| Cloudflare Pages ×2, Tunnel, R2 media (<10 GB, zero egress), zone | $0 |
| `ember.vanter.net` (subdomain of the company domain) | $0 |
| **Total** | **~$27-30 / month** |

The $300 GCP credit → ~10 months of runway. No Advanced Certificate Manager, no
Cloud SQL, no Cloud Run, no Artifact Registry.

## 13. Migration path to managed infra (documented, NOT in scope)

1. **Cloud SQL for PostgreSQL** — create instance → `pg_restore` the latest
   `ember-backups` dump → flip `SPRING_DATASOURCE_URL` and add the Cloud SQL
   Auth Proxy sidecar (or the socket-factory dependency) → drop the `postgres`
   container. ~1 hour. No app code change beyond an optional dependency.
2. **Cloud Run** — deploy the same backend image with `min-instances=1`, "CPU
   always allocated", a VPC connector to Cloud SQL → repoint the Tunnel (or a
   direct custom domain) at the Cloud Run URL → retire the VM. Adds the Cloud
   SQL connector dependency; the in-memory STOMP broker is still fine at one
   instance.
3. R2, Cloudflare, and Pages are unchanged.

Trigger: enough paying restaurants that managed backups/PITR and
no-server-operations are worth ~$100+/month.

## 14. Testing strategy

- **Backend code changes** (§5.2 management port, §5.3 media-key indirection,
  §5.4 `CF-Connecting-IP`, §5.1 prod config) each ship with unit/slice tests;
  the full `./mvnw test` stays green (currently **899**).
- **Frontend build changes** (§9.2 `gen-env-config.mjs`): `pnpm build` locally
  produces `dist/env-config.js` with the expected values; the existing 41 tests
  + `pnpm run lint` stay green; the Docker/dev path is untouched.
- **Infra provisioning is manual, verified by a checklist** in the plan (no CI
  covers it): VM up → firewall confirmed closed (`nmap` from outside shows no
  open ports) → Compose up → `cloudflared` registered → `api.ember.vanter.net/v1/actuator/health`
  UP → CORS preflight from `app.ember.vanter.net` passes → `wss://api…/v1/ws`
  connects → image upload lands in R2 and serves via `cdn.` with the immutable
  cache header → the `pg_dump` job writes a restorable archive → restore test
  passes → Ops Agent metrics + logs appear in Cloud Monitoring → a deliberate
  container stop fires the uptime alert.
- **End-to-end smoke on real infra** (final plan task): provision a restaurant
  through onboarding, log in as each role from a separate device against
  `app.ember.vanter.net`, run a full order customer → KDS → waiter → bill,
  upload a menu image, and confirm the cash-shift sentinel behaves.

## 15. Deliverables / new files

- `docs/superpowers/specs/2026-08-28-hosted-production-deployment-design.md` (this doc)
- `deploy/docker-compose.prod.yml`
- `deploy/deploy.sh`
- `deploy/RUNBOOK.md`
- `deploy/backup/` — `pg_dump` + rotation script (`gcloud storage`, VM service account)
- `.github/workflows/backend-image.yml`
- `frontend/scripts/gen-env-config.mjs`
- Backend: `application-prod.properties` additions; new `minio.public-url`
  property + `MinioProperties` field; `ImageUploadService` + image DTO mappers;
  `AuthRateLimiterFilter`; management-port config; associated tests
- `landing/functions/contact.ts` **or** a `POST /v1/public/contact` endpoint (decided at plan time)
- `landing/astro.config.mjs` — `site:` update

## 16. Open items to settle during the implementation plan

- Contact-form target: Pages Function vs backend endpoint vs third-party (§9.1).
- Production branch model: build straight off `main` vs a dedicated `release`
  branch/tag (§9.3, §11).
- Whether Spring serves `/actuator/health` on the main port automatically once
  `management.server.port` is set, or an explicit mapping is needed (§5.2) —
  resolve with an integration test early.
- Container registry: GHCR vs GCP Artifact Registry (§11).
