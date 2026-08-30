# Report 286 — HPD-14: Ops Agent, Prometheus scrape, alert policies (partial)

## 1. Identification
- **Report number:** 286
- **Current Task ID:** HPD-14 (GCP-side scope; uptime check deferred to HPD-20)
- **Predecessor Task:** HPD-13 (report 285 — secret store + first stack bring-up)
- **Branch:** `feat/hpd-14-monitoring` (stacked on `feat/hpd-13-secrets-bringup` → hpd-12 → hpd-11, off `main` `8fd4a9e`)

## 2. Objective
Give the single production VM observability: guest metrics + application (JVM/HTTP/DB
pool) metrics into Cloud Monitoring, and alert policies that email the maintainer on
resource exhaustion. The public-endpoint uptime check needs the Cloudflare Tunnel and
is folded into HPD-20.

## 3. Modified Files
- `deploy/monitoring/ops-agent-config.yaml` — **new**, the VM's
  `/etc/google-cloud-ops-agent/config.yaml`.
- `deploy/monitoring/alert-cpu.json`, `alert-mem.json`, `alert-disk.json` — **new**,
  alert-policy definitions (`${CHANNEL}` placeholder for the notification channel).
- `deploy/monitoring/README.md` — **new**, how to apply + what HPD-20 still owes.
- `deploy/RUNBOOK.md` — `#### HPD-14 — executed 2026-08-29 (partial …)` block + the
  OS-Login `cd /tmp` quirk note.
- `PROGRESS.md` — Last Completed Task (report 286), Current Active Task, HPD-14
  checkbox ticked for the GCP scope, HPD-20 extended to own the uptime check.
- `reports/286-hpd-14-monitoring.md` — this report.

No application code touched.

## 4. What Changed?

### Infrastructure (executed live by the operator, project `ember-prod-vanter`)
- **Ops Agent 2.70.0 installed.** The plan's bootstrap URL
  `https://dl.google.com/cloudagents/add-google-cloud-ops-agent.sh` now returns
  **404** (verified: real 404 from Google's `server: downloads`, not a PGA/DNS
  intercept — `dl.google.com` resolves to public Google IPs). Installed instead from
  the apt repo: key from `packages.cloud.google.com/apt/doc/apt-key.gpg`, suite
  `google-cloud-ops-agent-noble-2` (Ubuntu 24.04), `apt-get install
  google-cloud-ops-agent`. Service enabled + `active`.
- **`/etc/google-cloud-ops-agent/config.yaml`** (= `deploy/monitoring/ops-agent-config.yaml`):
  - `hostmetrics` receiver → guest CPU / memory / disk / swap / load
    (`agent.googleapis.com/*`).
  - `ember_app` `prometheus` receiver → scrapes `http://localhost:8081/actuator/prometheus`
    every 30s. The app exposes it (56 `jvm_*` series confirmed via `curl`;
    `micrometer-registry-prometheus` is already a backend dependency and
    `management.endpoints.web.exposure.include` lists `prometheus`). Port 8081 is the
    loopback-only management port (HPD-03/05) — not reachable off the VM.
  - Both wired into `service.pipelines`; agent restarted and stayed `active` (a bad
    config would abort startup).
- **Notification channel:** `gcloud beta monitoring channels create --type=email` →
  `projects/ember-prod-vanter/notificationChannels/1594227198064578240`.
- **Alert policies** (`gcloud alpha monitoring policies create --policy-from-file`),
  all enabled, all `autoClose` 1800s, all notifying the channel above:
  - `ember-prod CPU > 90% (10m)` — `compute.googleapis.com/instance/cpu/utilization`
    > 0.9 for 600s (built-in hypervisor metric, no agent needed).
  - `ember-prod memory > 90%` — `agent.googleapis.com/memory/percent_used`
    `{state=used}` > 90 for 600s.
  - `ember-prod disk > 85%` — `agent.googleapis.com/disk/percent_used` `{state=used}`
    > 85 for 300s, reduced `MAX` per instance.

### Deferred to HPD-20 (documented, not executed)
- Uptime check `gcloud monitoring uptime create ember-api-ping` on
  `https://api.ember.vanter.net/v1/public/ping`, period 5m — the hostname does not
  resolve until the Cloudflare Tunnel is live.
- An alert policy on `monitoring.googleapis.com/uptime_check/check_passed`.
- The end-to-end test: `docker compose stop app` for ~6 min → alert email → `start`.

### RUNBOOK
- HPD-14 block with the apt-repo install, the full config YAML, and the channel +
  policy commands.
- Recorded the OS-Login quirk: VM SSH lands in a home dir the login user cannot write
  to; commands that write to CWD (`curl -O`) must `cd /tmp` first.

## 5. Why It Changed?
- **Ops Agent, not raw Prometheus infra:** GCP's free hypervisor metrics only cover
  CPU / network / disk I/O — not in-guest memory or filesystem fill, and nothing
  about the JVM. The Ops Agent's `hostmetrics` covers the former; its `prometheus`
  receiver ships the app's own `/actuator/prometheus` (heap, GC, Hikari pool, HTTP
  latency/error counts) so a degrading deploy is visible before it becomes an outage.
  On a single-VM prod with no HA, that early warning is the whole point.
- **apt repo instead of the bootstrap script:** the documented script 404s; the apt
  repo is the same mechanism the script would have configured, and
  `packages.cloud.google.com` is stable and PGA-compatible.
- **Config + policy JSON committed under `deploy/monitoring/`:** the monitoring setup
  is now reproducible from the repo rather than living only as console state — the
  same principle as `deploy/docker-compose.prod.yml` and `deploy/backup/`.
- **Uptime check deferred, not skipped:** it is a real HPD-14 deliverable, but it
  cannot be created against a hostname that does not resolve. HPD-20 already
  integrates the Cloudflare deliverables and runs the full smoke, so the uptime check
  and its E2E test belong there; `PROGRESS.md` and `deploy/monitoring/README.md` both
  record the hand-off so it is not lost.
