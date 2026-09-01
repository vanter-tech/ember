# Ember Hosted Production — Runbook

Operational procedures for the hosted SaaS SKU: one GCP `e2-medium` VM running
`deploy/docker-compose.prod.yml`, fronted by Cloudflare. Command bodies under the
provisioning and recovery sections are filled in as Phases 3–5 (HPD-11..22) are
executed.

> **2026-09-01 — edge reworked.** The Cloudflare **Tunnel** (`cloudflared`) was
> dropped. `api.ember.vanter.net` is now a **proxied Cloudflare A record → the
> VM's external IP**, and a `caddy` container on the VM terminates a Cloudflare
> **Origin Certificate** on `:443` and reverse-proxies to `app:8080`. Inbound
> `:443` is opened **only to Cloudflare's published IP ranges** by the
> `allow-cf-https` firewall rule. There is no delegated `ember.vanter.net`
> zone — everything lives directly in the **`vanter.net`** Cloudflare zone
> (HPD-15's NS delegation was not done). The landing and the SPA are Cloudflare
> **Workers with static assets** (`ember` and `ember-app`), not Pages projects.
> See "HPD-17 — executed 2026-09-01" below and report 322.

## Topology

```
                          Internet
                             │
              ┌──────────────┴───────────────────────────┐
              │  Cloudflare — zone `vanter.net`           │
              │  · Universal SSL covers the edge hostnames│
              │  · WAF, edge rate-limit, HSTS, analytics  │
              └───┬───────────┬───────────┬───────────┬───┘
                  │           │           │           │
        ember.vanter.net  app.ember…  api.ember…  cdn.ember…
                  │           │           │           │
            Worker:      Worker:     proxied A     R2 bucket
             `ember`     `ember-app`  record →      ember-media-prod
           (Astro,        (React SPA,  VM ext. IP   (public read)
            landing/)      frontend/)  34.44.144.220   (HPD-16, tbd)
                                         │  Full (strict) → Origin Cert
                                         │  fw: :443 from Cloudflare ranges only
                        ┌────────────────┴─────────────────────┐
                        │  GCP Compute Engine  e2-medium       │
                        │  Ubuntu LTS + Docker, us-central1    │
                        │  firewall: IAP SSH + allow-cf-https  │
                        │                                      │
                        │  docker compose -f                   │
                        │    deploy/docker-compose.prod.yml:   │
                        │    · caddy  :443 → app:8080          │
                        │        (TLS: /opt/ember/caddy/       │
                        │         origin.pem + origin.key)     │
                        │    · app  (Spring, profile=prod)     │
                        │    · postgres  (never exposed)       │
                        │    · backup  (nightly pg_dump → GCS) │
                        │  + GCP Ops Agent (metrics + logs)    │
                        └──────────────────────────────────────┘
```

Login request flow: SPA (Worker `ember-app`) → `POST /v1/auth/login` on
`api.ember.vanter.net` → Cloudflare edge (TLS #1, public cert) → proxied to the
VM external IP `:443` → `caddy` (TLS #2, Cloudflare Origin Cert) → `app:8080` →
`postgres:5432` (never leaves the VM). `wss://api.ember.vanter.net/v1/ws` upgrades
over the same path (Caddy proxies WebSockets by default). `app.` and `api.` are
separate origins → CORS applies (`EMBER_CORS_ALLOWED_ORIGINS`). Caddy forwards
Cloudflare's `CF-Connecting-IP` untouched, so `AuthRateLimiterFilter` keys the
real client IP (`EMBER_RATELIMIT_TRUSTED_PROXIES=172.16.0.0/12` trusts the
compose bridge Caddy sits on).

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

#### HPD-11 — executed 2026-08-29

Project `ember-prod-vanter`, region `us-central1`, zone `us-central1-a`. A billing
budget alert was created on the project (operator step, outside this plan).

```bash
# 1. Project + APIs
gcloud config set project ember-prod-vanter
gcloud services enable compute.googleapis.com secretmanager.googleapis.com \
  monitoring.googleapis.com logging.googleapis.com iap.googleapis.com storage.googleapis.com

# 2. Firewall — IAP SSH only; default broad SSH/RDP rules removed
gcloud compute firewall-rules create allow-iap-ssh \
  --direction=INGRESS --action=ALLOW --rules=tcp:22 \
  --source-ranges=35.235.240.0/20 --network=default
gcloud compute firewall-rules delete default-allow-ssh default-allow-rdp --quiet
# Verify: only allow-iap-ssh ingress; nothing exposes 80/443/8080.
gcloud compute firewall-rules list \
  --format="table(name,direction,sourceRanges.list(),allowed[].map().firewall_rule().list())"

# 3. VM
gcloud compute instances create ember-prod \
  --zone=us-central1-a --machine-type=e2-medium \
  --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud \
  --boot-disk-size=30GB --boot-disk-type=pd-standard \
  --shielded-secure-boot --shielded-vtpm \
  --metadata=enable-oslogin=TRUE
# Internal IP 10.128.0.2, external IP 34.44.144.220 — see DEVIATION below.

# 4. Base setup on the VM (over IAP SSH)
gcloud compute ssh ember-prod --zone us-central1-a --tunnel-through-iap --command '
  set -e
  sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 unattended-upgrades
  sudo systemctl enable --now docker
  swapon --show | grep -q /swapfile || { sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile; }
  grep -q "^/swapfile " /etc/fstab || echo "/swapfile none swap sw 0 0" | sudo tee -a /etc/fstab
  sudo mkdir -p /opt/ember && sudo chmod 755 /opt/ember
  sudo dpkg-reconfigure -f noninteractive unattended-upgrades
'
# Installed: docker.io 29.1.3, docker-compose-v2 2.40.3. Swap 2G active + in fstab.

# 5. Stage the compose stack on the VM
gcloud compute scp deploy/docker-compose.prod.yml deploy/backup/Dockerfile deploy/backup/backup.sh \
  ember-prod:/tmp/ --zone us-central1-a --tunnel-through-iap
gcloud compute ssh ember-prod --zone us-central1-a --tunnel-through-iap --command '
  sudo mkdir -p /opt/ember/backup
  sudo mv /tmp/docker-compose.prod.yml /opt/ember/
  sudo mv /tmp/Dockerfile /tmp/backup.sh /opt/ember/backup/
'

# 6. Verify egress (image pull works)
gcloud compute ssh ember-prod --zone us-central1-a --tunnel-through-iap \
  --command 'sudo docker run --rm hello-world'
```

**DEVIATION — external IP instead of `--no-address` + Cloud NAT.** The plan
specified a VM with no public IP. As provisioned, `ember-prod` carries an external
IP (`34.44.144.220`) for outbound access (GHCR image pulls, `apt`) because no
Cloud NAT was set up. *(At the time of HPD-11 inbound was fully closed. Since
HPD-17 the external IP also serves inbound `:443`, restricted to Cloudflare's IP
ranges by `allow-cf-https`; the A record for `api.ember.vanter.net` points here,
and the IP is now the static reservation `ember-prod-ip`.)* A later
hardening pass can still add Cloud NAT for egress and keep `:443` as the only
inbound.

#### HPD-12 — executed 2026-08-29

Backup bucket `gs://ember-backups-ember-prod-vanter` was already created (private).
This task granted the VM's service account write access to it, turned on object
versioning, and attached a daily boot-disk snapshot schedule.

```bash
# VM service account (default compute SA) — already has the cloud-platform scope,
# so no stop/start was needed.
SA=$(gcloud compute instances describe ember-prod --zone us-central1-a \
     --format="value(serviceAccounts[0].email)")
# -> 253780825021-compute@developer.gserviceaccount.com

# 1. Write access to just this bucket + object versioning
gcloud storage buckets add-iam-policy-binding gs://ember-backups-ember-prod-vanter \
  --member="serviceAccount:${SA}" --role=roles/storage.objectAdmin
gcloud storage buckets update gs://ember-backups-ember-prod-vanter --versioning

# 2. Daily disk snapshot policy, attached to the boot disk
gcloud compute resource-policies create snapshot-schedule ember-daily-snap \
  --region=us-central1 --max-retention-days=7 \
  --daily-schedule --start-time=07:00 --on-source-disk-delete=keep-auto-snapshots
gcloud compute disks add-resource-policies ember-prod \
  --zone us-central1-a --resource-policies=ember-daily-snap

# 3. Verify
gcloud storage buckets describe gs://ember-backups-ember-prod-vanter \
  --format="yaml(public_access_prevention,uniform_bucket_level_access,versioning_enabled,location)"
# -> public_access_prevention: enforced ; uniform_bucket_level_access: true ;
#    versioning_enabled: true ; location: US
gcloud compute disks describe ember-prod --zone us-central1-a \
  --format="value(resourcePolicies)"
# -> .../resourcePolicies/ember-daily-snap
```

**DEVIATION — bucket is `US` multi-region, not `us-central1`.** The plan created the
bucket with `--location=us-central1`; as provisioned it is a `US` multi-region
bucket. This only raises storage cost slightly and increases durability — no code or
process depends on the bucket's region. `backup.sh` writes by bucket name only.

#### HPD-13 — executed 2026-08-29

First real bring-up: backend image published, the `.env` body stored as one Secret
Manager secret, `deploy.sh` run, Flyway + the backup job verified against live infra.
`cloudflared` + R2 stay on placeholder values until HPD-20 (Cloudflare track).

```bash
# 1. Publish the backend image to GHCR (CI: .github/workflows/backend-image.yml)
git tag -a v0.1.0 -m "v0.1.0 - first hosted production image" && git push origin v0.1.0
# -> ghcr.io/vanter-tech/ember-backend:0.1.0 + :latest

# 2. The GHCR package is PRIVATE (org policy blocks public container packages), so the
#    VM authenticates to the registry once. Classic PAT, scope: read:packages.
printf '%s' "<PAT>" | gcloud compute ssh ember-prod --zone us-central1-a \
  --tunnel-through-iap --command "sudo docker login ghcr.io -u <github-user> --password-stdin"
#    Creds land in /root/.docker/config.json on the VM and persist for `compose pull`.

# 3. Assemble the .env body from deploy/.env.prod.example and store it as ONE secret.
#    Real, rotated values for SPRING_DATASOURCE_PASSWORD / JWT_SECRET /
#    PLATFORM_JWT_SECRET; a fresh RSA keypair for HUB_LICENSE_PRIVATE_KEY (base64 of
#    the PKCS8 DER). TUNNEL_TOKEN + MINIO_* left as PLACEHOLDER for now.
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -outform DER -out hub_priv.der
openssl pkcs8 -topk8 -nocrypt -inform DER -in hub_priv.der -outform DER -out hub_priv_pkcs8.der
openssl pkey -inform DER -in hub_priv_pkcs8.der -pubout -outform DER -out hub_pub.der
# ...write prod.env with SPRING_DATASOURCE_PASSWORD=$(openssl rand -hex 24),
#    JWT_SECRET/PLATFORM_JWT_SECRET=$(openssl rand -base64 48),
#    HUB_LICENSE_PRIVATE_KEY=$(base64 -w0 hub_priv_pkcs8.der), BACKUP_GCS_BUCKET=...
gcloud secrets create ember-prod-env --replication-policy=automatic --data-file=prod.env
gcloud storage cp hub_pub.der gs://ember-backups-ember-prod-vanter/keys/hub-public-key.der
shred -u prod.env hub_priv.der hub_priv_pkcs8.der && rm -f hub_pub.der

# 4. Deploy + verify
./deploy/deploy.sh latest          # -> "app healthy"
gcloud compute ssh ember-prod --zone us-central1-a --tunnel-through-iap --command \
  "sudo docker compose -f /opt/ember/docker-compose.prod.yml exec -T postgres \
   psql -U ember -d ember -c 'select version, success from flyway_schema_history order by installed_rank;'"
# -> V1..V5, all success = t
gcloud compute ssh ember-prod --zone us-central1-a --tunnel-through-iap --command \
  "sudo docker compose -f /opt/ember/docker-compose.prod.yml exec -T backup /usr/local/bin/backup.sh"
gcloud storage ls gs://ember-backups-ember-prod-vanter/postgres/
# -> gs://ember-backups-ember-prod-vanter/postgres/YYYY-MM-DD.dump.gz
```

**FIX shipped with this task.** `deploy/docker-compose.prod.yml` (healthcheck) and
`deploy/deploy.sh` (health gate) polled `http://localhost:8080/v1/actuator/health`,
but HPD-03 moved the management server to port `8081` and the management context has
no `/v1` prefix. Both now poll `http://localhost:8081/actuator/health`.

**Hub keypair follow-up.** The fresh `HUB_LICENSE_PRIVATE_KEY` invalidates every
previously issued Hub `license.key`. The matching public key is at
`gs://ember-backups-ember-prod-vanter/keys/hub-public-key.der`; the Hub build's
`ember-hub/keys/hub-public-key.der` must be replaced with it (tracked as Hub work,
not this plan). No paying Hub is deployed, so nothing breaks today.

#### HPD-14 — executed 2026-08-29 (partial — uptime check → HPD-20)

Ops Agent + a Prometheus scrape of the app's actuator + resource alert policies.
The Cloud Monitoring uptime check targets `https://api.ember.vanter.net/v1/public/ping`,
which did not resolve until the edge was live — it and its alert policy are done in
HPD-20. *(Update 2026-09-01: the hostname now resolves via the HPD-17 Caddy edge —
the check can be created; the "Cloudflare Tunnel" wording here is historical.)*

```bash
# 1. Install the Ops Agent. NOTE: the documented bootstrap script
#    https://dl.google.com/cloudagents/add-google-cloud-ops-agent.sh now 404s — use
#    the apt repo directly. (Ubuntu 24.04 "noble", Ops Agent major 2.)
gcloud compute ssh ember-prod --zone us-central1-a --tunnel-through-iap --command '
  curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg \
    | sudo gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg
  echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt google-cloud-ops-agent-noble-2 main" \
    | sudo tee /etc/apt/sources.list.d/google-cloud-ops-agent.list
  sudo apt-get update && sudo apt-get install -y google-cloud-ops-agent
'   # installed 2.70.0

# 2. Config: hostmetrics (guest CPU/mem/disk/swap) + a prometheus receiver scraping
#    the app's actuator on the loopback-only management port. Full file:
sudo tee /etc/google-cloud-ops-agent/config.yaml >/dev/null <<'YAML'
logging:
  receivers:
    syslog:
      type: files
      include_paths: [/var/log/messages, /var/log/syslog]
  service:
    pipelines:
      default_pipeline:
        receivers: [syslog]
metrics:
  receivers:
    hostmetrics:
      type: hostmetrics
    ember_app:
      type: prometheus
      config:
        scrape_configs:
          - job_name: ember-app
            scrape_interval: 30s
            metrics_path: /actuator/prometheus
            static_configs:
              - targets: ["localhost:8081"]
  processors:
    metrics_filter:
      type: exclude_metrics
      metrics_pattern: []
  service:
    pipelines:
      default_pipeline:
        receivers: [hostmetrics]
        processors: [metrics_filter]
      ember_app_pipeline:
        receivers: [ember_app]
YAML
sudo systemctl restart google-cloud-ops-agent
# Verify: curl -s localhost:8081/actuator/prometheus | grep -c '^jvm_'  -> 56

# 3. Email notification channel + three resource alert policies (Cloud Shell).
EMAIL=<ops-email>
CH=$(gcloud beta monitoring channels create --display-name="ember ops email" \
     --type=email --channel-labels=email_address=$EMAIL --format="value(name)")
# For each of cpu/mem/disk, a policy JSON referencing "$CH", then:
#   gcloud alpha monitoring policies create --policy-from-file=<f>.json
# Thresholds: CPU compute.googleapis.com/instance/cpu/utilization > 0.9 for 600s;
#   memory agent.googleapis.com/memory/percent_used{state=used} > 90 for 600s;
#   disk agent.googleapis.com/disk/percent_used{state=used} > 85 for 300s.
# All autoClose 1800s. Policy JSON committed under deploy/monitoring/.
```

**Deferred to HPD-20** *(unblocked 2026-09-01 — `api.ember.vanter.net` resolves
via the Caddy edge)*. Uptime check:
`gcloud monitoring uptime create ember-api-ping --resource-type=uptime-url \
--resource-labels=host=api.ember.vanter.net,project_id=ember-prod-vanter \
--path=/v1/public/ping --period=5` plus an alert policy on
`monitoring.googleapis.com/uptime_check/check_passed`, and the end-to-end test
(`docker compose stop app` ~6 min → alert email → `start`).

**OS Login quirk.** VM SSH lands in a home dir the login user cannot write to
(`Could not chdir to home directory … Permission denied`, harmless). Any command that
writes to the CWD must `cd /tmp` first (e.g. `curl -O`).

### Cloudflare — Phase 4

- **HPD-15** — ~~`ember.vanter.net` zone delegation~~ **not done.** No NS
  delegation; all records live directly in the existing **`vanter.net`** zone.
- **HPD-16** — R2 `ember-media-prod` bucket + `cdn.ember.vanter.net` custom
  domain + scoped API token handed to the GCP track. **Still pending** — media
  uploads are not wired in prod yet.
- **HPD-17** — ~~Tunnel `ember-prod`~~ **superseded** by a proxied A record +
  `caddy` edge. Done 2026-09-01 — see the executed block below.
- **HPD-18** — Worker `ember-app` (static assets, root `frontend`, build
  `pnpm run build:pages`) at `app.ember.vanter.net`; Worker `ember` keeps the
  landing at `ember.vanter.net`. Done 2026-08-31/09-01 — see report 321.
- **HPD-19** — HSTS / Always-Use-HTTPS, managed WAF, edge rate-limit on
  `/v1/auth/*`, block rule for `/v1/actuator/*`. **Still pending.**

#### HPD-18 — executed 2026-08-31 → 09-01

`frontend/wrangler.jsonc` (`name: "ember-app"`, `assets ./dist`,
`not_found_handling: "single-page-application"`) added by report 321. The
operator then:

1. Created a Cloudflare **Worker** `ember-app` connected to this Git repo — root
   directory `frontend`, build command `pnpm run build:pages`, deploy command
   `npx wrangler deploy`. Build variables: `EMBW_API_URL=https://api.ember.vanter.net/v1`,
   `EMBW_WS_URL=wss://api.ember.vanter.net/v1/ws`, `NODE_VERSION=20`.
   (`scripts/gen-env-config.mjs` writes `dist/env-config.js` from those at build
   time; `index.html` loads it as `window.ENV`.)
2. Moved the custom domain `app.ember.vanter.net` off the `ember` Worker and onto
   `ember-app`. `ember.vanter.net` stays on `ember`.

The `/console` platform UI is a route in the same SPA bundle (`/console/*` →
`ConsoleApp`), served by the SPA fallback — no separate deploy. Seed platform
operator (Flyway `V1__baseline_consolidated.sql`): `platform-admin@ember.local` /
`ChangeMe123!` — **change on first login**, the hash is in the repo.

Verify: `https://app.ember.vanter.net/` serves the SPA;
`https://app.ember.vanter.net/env-config.js` carries the `wss://api.ember.vanter.net`
values; `https://app.ember.vanter.net/console` reaches the console login.

#### HPD-17 — executed 2026-09-01

The Cloudflare Tunnel was replaced by a direct proxied path. `deploy/docker-compose.prod.yml`
drops `cloudflared` and adds a `caddy:2` service (report 322); `deploy/caddy/Caddyfile`
is one site block for `api.ember.vanter.net` (`tls /certs/origin.pem /certs/origin.key`,
`reverse_proxy app:8080`, `encode gzip`).

```bash
# 1. Cloudflare DNS (zone vanter.net): A  api.ember.vanter.net  34.44.144.220  — PROXIED.

# 2. GCP firewall — open :443 to Cloudflare's ranges only (confirm the list at
#    https://www.cloudflare.com/ips-v4 before re-running).
gcloud compute firewall-rules create allow-cf-https \
  --project=ember-prod-vanter --network=default \
  --direction=INGRESS --action=ALLOW --rules=tcp:443 \
  --source-ranges=173.245.48.0/20,103.21.244.0/22,103.22.200.0/22,103.31.4.0/22,141.101.64.0/18,108.162.192.0/18,190.93.240.0/20,188.114.96.0/20,197.234.240.0/22,198.41.128.0/17,162.158.0.0/15,104.16.0.0/13,104.24.0.0/14,172.64.0.0/13,131.0.72.0/22

# 3. Cloudflare Origin Certificate for api.ember.vanter.net
#    (dash → vanter.net → SSL/TLS → Origin Server → Create Certificate, RSA, 15y).
#    Stage the two PEM blocks + the Caddyfile, ship to the VM:
gcloud compute scp --zone us-central1-a --tunnel-through-iap \
  origin.pem origin.key Caddyfile ember-prod:/tmp/
gcloud compute ssh ember-prod --zone us-central1-a --tunnel-through-iap --command '
  sudo mkdir -p /opt/ember/caddy
  sudo mv /tmp/origin.pem /tmp/origin.key /tmp/Caddyfile /opt/ember/caddy/
  sudo chmod 600 /opt/ember/caddy/origin.key
  sudo chmod 644 /opt/ember/caddy/origin.pem /opt/ember/caddy/Caddyfile
'

# 4. Ship the new compose file (deploy.sh does NOT copy it) and bring up the edge.
gcloud compute scp deploy/docker-compose.prod.yml ember-prod:/tmp/dcp.yml \
  --zone us-central1-a --tunnel-through-iap
gcloud compute ssh ember-prod --zone us-central1-a --tunnel-through-iap --command '
  sudo cp /opt/ember/docker-compose.prod.yml /opt/ember/docker-compose.prod.yml.bak
  sudo mv /tmp/dcp.yml /opt/ember/docker-compose.prod.yml
  cd /opt/ember
  sudo docker compose -f docker-compose.prod.yml config >/dev/null
  sudo docker compose -f docker-compose.prod.yml pull caddy
  sudo docker compose -f docker-compose.prod.yml up -d --remove-orphans
'

# 5. Cloudflare → vanter.net → SSL/TLS: mode "Full (strict)" — zone-wide, OR a
#    Configuration Rule scoped to hostname eq api.ember.vanter.net if other proxied
#    origins in the zone can't take strict.

# 6. Smoke.
curl -s https://api.ember.vanter.net/v1/public/ping        # -> pong
```

`TUNNEL_TOKEN` in `ember-prod-env` is now dead (the new compose never reads it) —
drop it on the next secret edit.

## Routine deploy

`./deploy/deploy.sh <image-tag>` from a machine with `gcloud` auth + IAP access.

The script pushes the current `/opt/ember/.env` from Secret Manager
(`ember-prod-env`), appends `EMBER_IMAGE_TAG=<tag>`, then over IAP SSH runs
`docker compose -f docker-compose.prod.yml pull && up -d` and polls
`app`'s `http://localhost:8081/actuator/health` (the prod management port) for up to
120s. Non-zero exit + last 80 log lines if it never reports `UP`.

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

Edge-specific after a rebuild:

- The snapshot carries `/opt/ember/caddy/` (Caddyfile + `origin.pem`/`origin.key`)
  and `/opt/ember/docker-compose.prod.yml`; if the restore predates HPD-17,
  re-stage them per "HPD-17 — executed" above.
- **External IP:** reserved as the static address `ember-prod-ip`
  (`34.44.144.220`, `us-central1`, promoted in place 2026-09-01), so a rebuilt
  instance keeps it as long as the reservation is re-attached
  (`--address ember-prod-ip` on create, or `gcloud compute instances
  add-access-config`). Only if the reservation is ever released does the
  `api.ember.vanter.net` A record in Cloudflare (zone `vanter.net`) need updating.
- Nothing to reconnect — Caddy serves as soon as the container is up and 443 is
  reachable from Cloudflare's ranges (`allow-cf-https` is a project firewall
  rule, unaffected by the VM rebuild).

## Secret rotation

The whole `.env` body lives as a single Secret Manager secret, `ember-prod-env`.
`deploy.sh` reads `versions access latest` every run, so rotating a value is: add a
new secret version, then redeploy.

```bash
# 1. Fetch the current body, edit the one line, add it back as a new version.
gcloud secrets versions access latest --secret=ember-prod-env > /tmp/prod.env   # umask 077
$EDITOR /tmp/prod.env
gcloud secrets versions add ember-prod-env --data-file=/tmp/prod.env
shred -u /tmp/prod.env

# 2. Push it to the VM and restart the stack.
./deploy/deploy.sh latest
```

Pass `latest` (or a bare `0.1.0`), **not** `v0.1.0` — CI publishes
`ghcr.io/vanter-tech/ember-backend:<tag-without-v>` + `:latest`; there is no
`:v0.1.0` image, so `git describe --tags` would set an `EMBER_IMAGE_TAG` that
fails `compose pull`.

- This is how the remaining `MINIO_*` placeholders get swapped for real R2
  credentials (HPD-16/20). `TUNNEL_TOKEN` is obsolete — delete the line.
- Rotating the **Caddy Origin Certificate**: create a new one in Cloudflare
  (SSL/TLS → Origin Server), replace `/opt/ember/caddy/origin.pem` +
  `origin.key` on the VM, then
  `sudo docker compose -f /opt/ember/docker-compose.prod.yml restart caddy`.
  No secret-rotation round-trip (the cert is not in `.env`).
- Rotating `JWT_SECRET` / `PLATFORM_JWT_SECRET` invalidates every live session by
  design.
- Rotating `SPRING_DATASOURCE_PASSWORD` does **not** re-key the running Postgres —
  the password is only read from `POSTGRES_PASSWORD` on the *first* init of the
  `ember_pgdata` volume. To change it on an existing DB, run
  `ALTER USER ember WITH PASSWORD '…'` inside the `postgres` container first, then
  rotate the secret to match.
- Rotating `HUB_LICENSE_PRIVATE_KEY` invalidates every previously issued Hub
  `license.key`; ship the matching public key in the Hub build.

## Restore test (run quarterly)

(filled in by HPD-21) Restore the latest GCS dump into a throwaway Postgres
container, boot the app against it read-only, confirm row counts and a login.
Record the run date here.
