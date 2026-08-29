# Report 279 — HPD-07: deploy script + runbook skeleton

## 1. Identification
- **Report number:** 279 (278 is already taken on `feat/hub-packaging-installer` —
  `reports/278-chore-remove-prometheus-grafana-mongo-orphan.md`; skipped here to avoid a
  merge-time collision, per the divergence flagged in PROGRESS.md)
- **Task ID:** HPD-07 (Hosted Production Deployment plan, Phase 2 — deployment artifacts)
- **Predecessor task:** HPD-06 (report 277 — nightly `pg_dump` → GCS backup container)

## 2. Objective
Add the one-command redeploy path for the hosted stack and the operational runbook it belongs
to: `deploy/deploy.sh` (refresh `/opt/ember/.env` from Secret Manager, `docker compose pull`/
`up -d` over IAP SSH, then gate on `app` health) and `deploy/RUNBOOK.md` (topology, provisioning
index, routine deploy, recovery, secret rotation, restore test). No app code, no unit tests.

## 3. Modified Files
- `deploy/deploy.sh` (new, executable)
- `deploy/RUNBOOK.md` (new)
- `reports/279-hpd-07-deploy-script.md` (new)
- `PROGRESS.md`

## 4. What Changed?
### `deploy/deploy.sh` (new)
- `set -euo pipefail`. Config via env with defaults: `EMBER_VM` (`ember-prod`), `EMBER_ZONE`
  (`us-central1-a`), `EMBER_ENV_SECRET` (`ember-prod-env`); image tag is `$1` (default `latest`).
- Step 1 — `gcloud secrets versions access latest --secret <SECRET>` piped through
  `gcloud compute ssh <VM> --tunnel-through-iap --command` into
  `sudo install -m 600 /dev/stdin /opt/ember/.env`, then appends `EMBER_IMAGE_TAG=<tag>` to that
  file. The full `.env` body lives only in Secret Manager; the VM copy is mode `600`.
- Step 2 — a second IAP SSH command: `cd /opt/ember`, `docker compose -f docker-compose.prod.yml
  pull` then `up -d`, then a 20-iteration / 6s-sleep loop polling
  `compose exec -T app wget -qO- http://localhost:8080/v1/actuator/health | grep -q UP`. Exits 0
  on the first `UP`; on exhaustion prints `docker compose logs --tail=80 app` and exits 1.
- Marked executable (`git update-index --chmod=+x`).

### `deploy/RUNBOOK.md` (new)
- **Topology** — the spec §3 ASCII diagram (Cloudflare zone → Pages ×2 / Tunnel / R2 → GCP
  `e2-medium` running the 4-service compose), plus the login/websocket request-flow paragraph.
  Added the `backup` service line to the compose box (spec's diagram predates HPD-06).
- **First-time provisioning** — an index of the remaining infra tasks, one bullet each:
  GCP track HPD-11..14, Cloudflare track HPD-15..19. Command bodies are filled in as those
  tasks execute (plan Task 7 Step 2).
- **Routine deploy** — how to invoke `deploy.sh <tag>`, what it does, the overridable env vars.
- **Recovery** — app container unhealthy (log check → refresh env → roll back to previous tag),
  Postgres corruption (restore latest GCS dump, detail deferred to HPD-21), VM lost (recreate
  from snapshot + `compose up -d`; `cloudflared` self-reconnects from `TUNNEL_TOKEN`).
- **Secret rotation** — add a Secret Manager version, re-run `deploy.sh`; JWT-secret rotation
  invalidates live sessions by design. Detail deferred to HPD-13 / HPD-20.
- **Restore test (quarterly)** — placeholder procedure, verified body deferred to HPD-21.

### Verification
- `shellcheck deploy/deploy.sh deploy/backup/backup.sh` via
  `docker run --rm -v <repo>:/mnt -w /mnt koalaman/shellcheck:stable` → **exit 0**, no warnings.
  (No native `shellcheck` on this host; ran it through the image the plan names as the fallback.)
- No `./mvnw` / `pnpm` gates — this task adds no application code. Backend suite stays at 900/900.

## 5. Why It Changed?
The spec's deploy pipeline (v1, §11) is deliberately minimal: no CD, a human runs one script.
That script has to exist as a repo artifact so the redeploy procedure is reviewable and
version-controlled rather than tribal knowledge. Pulling the `.env` from Secret Manager on every
run (instead of editing it in place on the VM) keeps the VM stateless w.r.t. secrets and makes
rotation a re-run. The health gate turns a bad image into a non-zero exit with logs instead of a
silently-down API. `RUNBOOK.md` is seeded now so Phases 3–5 append their real commands into a
structure that already exists, rather than each infra task inventing its own doc.
