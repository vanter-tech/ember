# Ember Hosted Production — Runbook

Operational procedures for the hosted SaaS SKU: one GCP `e2-medium` VM running
`deploy/docker-compose.prod.yml`, fronted by Cloudflare. Command bodies under the
provisioning and recovery sections are filled in as Phases 3–5 (HPD-11..22) are
executed.

## Topology

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
                        │    · backup  (nightly pg_dump → GCS) │
                        │  + GCP Ops Agent (metrics + logs)    │
                        └──────────────────────────────────────┘
```

Login request flow: SPA (Pages) → `POST /v1/auth/login` on `api.ember.vanter.net`
→ Cloudflare Tunnel → `cloudflared` on the VM → `app:8080` → `postgres:5432`
(never leaves the VM). `wss://api.ember.vanter.net/v1/ws` upgrades over the same
Tunnel path. `app.` and `api.` are separate origins → CORS applies
(`EMBER_CORS_ALLOWED_ORIGINS`).

## First-time provisioning

### GCP (maintainer) — Phase 3

- **HPD-11** — project + APIs, VM `ember-prod` (no public IP), IAP-SSH-only
  firewall, swap file, Docker install.
- **HPD-12** — `ember-backups-<project>` GCS bucket (private), VM service account
  `roles/storage.objectAdmin` on it, daily disk snapshot schedule.
- **HPD-13** — `ember-prod-env` Secret Manager secret (rotated `JWT_SECRET` /
  `PLATFORM_JWT_SECRET` / `HUB_LICENSE_PRIVATE_KEY` / DB password), first
  bring-up, Flyway V1–V5 applied, backup container verified.
- **HPD-14** — Ops Agent (Prometheus scrape of `127.0.0.1:8081`), uptime check on
  `https://api.ember.vanter.net/v1/public/ping`, alert policies.

### Cloudflare (colleague) — Phase 4

- **HPD-15** — `ember.vanter.net` zone delegation (NS record from `vanter.net`) +
  DNS records.
- **HPD-16** — R2 `ember-media-prod` bucket + `cdn.ember.vanter.net` custom
  domain + scoped API token handed to the GCP track.
- **HPD-17** — Tunnel `ember-prod`, public hostname
  `api.ember.vanter.net → http://app:8080`, `TUNNEL_TOKEN` handed to the GCP
  track.
- **HPD-18** — Pages `ember-app` (root `frontend`, build `pnpm run build:pages`)
  + `ember-landing` (root `landing`) + custom domains + env vars.
- **HPD-19** — HSTS / Always-Use-HTTPS, managed WAF, edge rate-limit on
  `/v1/auth/*`, block rule for `/v1/actuator/*`.

## Routine deploy

`./deploy/deploy.sh <image-tag>` from a machine with `gcloud` auth + IAP access.

The script pushes the current `/opt/ember/.env` from Secret Manager
(`ember-prod-env`), appends `EMBER_IMAGE_TAG=<tag>`, then over IAP SSH runs
`docker compose -f docker-compose.prod.yml pull && up -d` and polls
`app`'s `/v1/actuator/health` for up to 120s. Non-zero exit + last 80 log lines
if it never reports `UP`.

Overridable via env: `EMBER_VM` (default `ember-prod`), `EMBER_ZONE` (default
`us-central1-a`), `EMBER_ENV_SECRET` (default `ember-prod-env`).

## Recovery

### App container unhealthy

1. `gcloud compute ssh ember-prod --zone us-central1-a --tunnel-through-iap`
2. `cd /opt/ember && sudo docker compose -f docker-compose.prod.yml logs --tail=200 app`
3. Common causes: bad `/opt/ember/.env` (re-run `deploy.sh` to refresh from
   Secret Manager), Flyway migration failure (check `flyway_schema_history`),
   Postgres not healthy (see below). Roll back with
   `./deploy/deploy.sh <previous-tag>`.

### Postgres data corruption — restore latest dump

(filled in by HPD-21 — restore test) Pull the newest
`gs://ember-backups-<project>/postgres/YYYY-MM-DD.dump.gz`, `gunzip`, and
`pg_restore` into a fresh volume; see "Restore test" for the verified procedure.

### VM lost

Recreate the instance from the most recent disk snapshot (HPD-12 policy),
re-attach, `cd /opt/ember && sudo docker compose -f docker-compose.prod.yml up -d`.
`cloudflared` reconnects the Tunnel automatically from `TUNNEL_TOKEN` in the env
file.

## Secret rotation

(filled in by HPD-13 / HPD-20) Add a new version to the `ember-prod-env` Secret
Manager secret, then `./deploy/deploy.sh <current-tag>` to push it and restart the
stack. Rotating `JWT_SECRET` / `PLATFORM_JWT_SECRET` invalidates all live
sessions by design.

## Restore test (run quarterly)

(filled in by HPD-21) Restore the latest GCS dump into a throwaway Postgres
container, boot the app against it read-only, confirm row counts and a login.
Record the run date here.
