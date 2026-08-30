# Report 283 — HPD-11: GCP project baseline + VM + firewall

## 1. Identification
- **Report number:** 283
- **Current Task ID:** HPD-11
- **Predecessor Task:** HPD-10 (report 282 — landing real contact submit + prod `site:`)
- **Branch:** `feat/hpd-11-runbook` (off `main` `8fd4a9e`, after PR #61 merged HPD-01..10)

## 2. Objective
Stand up the GCP baseline for the hosted production SKU — project, APIs, a hardened
`e2-medium` VM with SSH reachable only through IAP, swap, Docker + Compose v2, and the
prod Compose stack staged on disk — then record the exact commands in
`deploy/RUNBOOK.md` so the provisioning is reproducible.

## 3. Modified Files
- `deploy/RUNBOOK.md` — filled in the `First-time provisioning → GCP` section with the
  executed HPD-11 commands + the external-IP deviation note.
- `PROGRESS.md` — new "Last Completed Task (report 283)" entry, Current Active Task reset
  to HPD-12, System health prefix updated to the new branch, HPD-11 checkbox ticked.
- `reports/283-hpd-11-gcp-vm.md` — this report.

No application code, tests, or build artifacts touched.

## 4. What Changed?

### Infrastructure (executed live by the operator, GCP project `ember-prod-vanter`)
- **APIs enabled:** `compute`, `secretmanager`, `monitoring`, `logging`, `iap`, `storage`.
- **Billing budget alert:** created on the project (operator step, outside the plan).
- **Firewall:** created `allow-iap-ssh` — INGRESS, `tcp:22`, source `35.235.240.0/20`
  (the IAP TCP-forwarding range), network `default`. Deleted the auto-created
  `default-allow-ssh` and `default-allow-rdp` rules so no broad `0.0.0.0/0` SSH/RDP
  ingress remains. No rule exposes 80/443/8080.
- **VM `ember-prod`:** `e2-medium`, `ubuntu-2404-lts-amd64`, 30 GB `pd-standard` boot
  disk, Shielded VM (secure boot + vTPM), OS Login enabled. Internal IP `10.128.0.2`,
  external IP `34.44.144.220`.
- **Base setup on the VM (over IAP SSH):** `docker.io` 29.1.3 + `docker-compose-v2`
  2.40.3 installed and `systemctl enable --now docker`; a 2 GB `/swapfile`
  (`mkswap`/`swapon`) with a matching `/etc/fstab` line; `/opt/ember` created `755`;
  `unattended-upgrades` installed and reconfigured non-interactively.
- **Compose stack staged:** `deploy/docker-compose.prod.yml` copied to
  `/opt/ember/docker-compose.prod.yml`, and `deploy/backup/Dockerfile` +
  `deploy/backup/backup.sh` to `/opt/ember/backup/`, via
  `gcloud compute scp … --tunnel-through-iap`.
- **Verified:** `swapon --show` shows the 2 GB file active; `docker compose version`
  reports v2; `docker run --rm hello-world` succeeds (confirms outbound image pulls
  work); `gcloud compute instances describe` confirms the firewall leaves only the
  IAP SSH path open.

### Repo
- `deploy/RUNBOOK.md` gains an `#### HPD-11 — executed 2026-08-29` block under the GCP
  provisioning heading with all of the above as runnable commands, plus a **DEVIATION**
  paragraph documenting the external IP.

## 5. Why It Changed?
- **IAP-only SSH, no HTTP/S ingress:** the VM never serves traffic directly — Cloudflare
  Tunnel dials out from `cloudflared` on the box — so every inbound port except the
  IAP-scoped `tcp:22` is closed. IAP gives audited, IAM-gated SSH without a public
  bastion or a VM public-SSH surface.
- **Swap:** an `e2-medium` has 4 GB RAM; Postgres + the JVM (`-Xmx1536m`) + `cloudflared`
  leave little headroom, so a 2 GB swap file is a cheap OOM backstop.
- **Compose stack staged now:** `deploy/deploy.sh` (HPD-07) expects
  `/opt/ember/docker-compose.prod.yml` and `/opt/ember/backup/` to already exist on the
  VM; putting them there is part of first-time provisioning, not the deploy.
- **DEVIATION — external IP instead of `--no-address` + Cloud NAT:** the plan called for
  a VM with no public IP. As provisioned, `ember-prod` has an external IP so the
  containers can reach GHCR, `apt`, and Cloudflare for outbound-only traffic; no Cloud
  NAT was set up. This does **not** open any inbound path — the firewall still only
  allows IAP SSH — but a later hardening pass can add a Cloud Router + Cloud NAT in
  `us-central1` and move the instance to `--no-address`. Recorded in the RUNBOOK so the
  gap is visible.
