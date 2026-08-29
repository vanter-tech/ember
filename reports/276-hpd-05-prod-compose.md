# Report 276 — HPD-05: production Docker Compose stack

## 1. Identification
- **Report number:** 276
- **Task ID:** HPD-05 (Hosted Production Deployment plan, Phase 2 — deployment artifacts)
- **Predecessor task:** HPD-04 (report 275 — `AuthRateLimiterFilter` trusts `CF-Connecting-IP`)

## 2. Objective
Add the production Compose project that the infra phases (HPD-07 deploy script, Phase 3/5 operator
runbook) consume: one stack with `postgres`, `app`, `cloudflared`, `backup`, plus the key-list
template the VM's `/opt/ember/.env` must contain. No app code, no tests.

## 3. Modified Files
- `deploy/docker-compose.prod.yml` (new)
- `deploy/.env.prod.example` (new)
- `reports/276-hpd-05-prod-compose.md` (new)
- `PROGRESS.md`

## 4. What Changed?
### `deploy/docker-compose.prod.yml` (new) — project `ember-prod`
- **`postgres`** — `postgres:16`, named volume `ember_pgdata`, tuned `command` flags
  (`shared_buffers=512MB`, `effective_cache_size=1536MB`, `max_connections=50`) for the
  `e2-medium` VM, `pg_isready` healthcheck. Password from `${SPRING_DATASOURCE_PASSWORD:?...}`.
- **`app`** — `ghcr.io/vanter-tech/ember-backend:${EMBER_IMAGE_TAG:-latest}` (image pushed by
  HPD-08), `env_file: /opt/ember/.env`, `SPRING_PROFILES_ACTIVE=prod`,
  `JAVA_TOOL_OPTIONS=-Xms512m -Xmx1536m`. Publishes **only** `127.0.0.1:8081:8081` (the
  loopback management port from HPD-03, for the on-VM Ops Agent) — `8080` is deliberately
  unpublished; only `cloudflared` reaches it over the default compose network as `http://app:8080`.
  `depends_on: postgres (service_healthy)`; own healthcheck hits `/v1/actuator/health` with a 60s
  `start_period`.
- **`cloudflared`** — `cloudflare/cloudflared:latest`, `tunnel --no-autoupdate run`,
  `TUNNEL_TOKEN` from `${TUNNEL_TOKEN:?...}`, `depends_on: app`.
- **`backup`** — `build: ./backup` (image authored in HPD-06), env for the nightly `pg_dump` →
  GCS job (`PGHOST/PGUSER/PGPASSWORD/PGDATABASE`, `GCS_BUCKET`, `DAILY_RETENTION=14`,
  `WEEKLY_RETENTION=8`), `depends_on: postgres (service_healthy)`.
- Top-level `volumes: ember_pgdata`.

### `deploy/.env.prod.example` (new)
Key-list template only, no real values — copied to `/opt/ember/.env` (mode 600) on the VM by the
HPD-07 deploy script from GCP Secret Manager. Sections: datastore
(`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`), auth secrets (`JWT_SECRET`, `PLATFORM_JWT_SECRET`,
`HUB_LICENSE_PRIVATE_KEY` — flagged for rotation, once tracked in a committed `.env`), Cloudflare
R2 (`MINIO_URL/ACCESS_KEY/SECRET_KEY/BUCKET`, `MINIO_PUBLIC_URL=https://cdn.ember.vanter.net` —
consumes HPD-01), CORS (`EMBER_CORS_ALLOWED_ORIGINS=https://app.ember.vanter.net`), rate-limiter
trusted proxies (`EMBER_RATELIMIT_TRUSTED_PROXIES=172.16.0.0/12`, the docker bridge
`cloudflared` sits on — consumes HPD-04), `TUNNEL_TOKEN`, `BACKUP_GCS_BUCKET`, `EMBER_IMAGE_TAG`.

### Verification
`docker compose -f deploy/docker-compose.prod.yml config -q` → **exit 0** (`COMPOSE_CONFIG_OK`).

**Plan drift (2 Windows/host-only validation quirks, no file change):**
1. The plan's Step 3 runs `config` with `--env-file deploy/.env.prod.example` and expects exit 0,
   but that file leaves `SPRING_DATASOURCE_PASSWORD=` blank and the compose uses the
   `${VAR:?msg}` form, which rejects an **empty** value, not just an unset one — the guard doing
   its job. Validated instead by passing placeholder values for the three `:?`-guarded vars
   (`SPRING_DATASOURCE_PASSWORD`, `TUNNEL_TOKEN`, `BACKUP_GCS_BUCKET`).
2. `app`'s `env_file: /opt/ember/.env` is an absolute POSIX path (correct on the Linux VM, where
   HPD-07 installs it); on this Windows host `docker compose` resolves it relative to the compose
   dir, so a throwaway stub was created at that path for the check and deleted immediately after.
   No change to the compose file — the path is intentionally the VM's real absolute location.

The `build: ./backup` context does not exist until HPD-06; `config` still passed (Compose does not
require the build context to exist at parse time).

## 5. Why It Changed?
Phase 2 turns the plan's design into repo artifacts the infra phases execute. The Compose file
encodes the spec's security posture directly: no inbound ports (only `cloudflared` dials out),
actuator reachable solely on VM loopback, secrets injected from a mode-600 file the repo never
contains. `.env.prod.example` is the single source of truth for what that file must hold, so the
deploy script and the operator runbook stay in sync.
