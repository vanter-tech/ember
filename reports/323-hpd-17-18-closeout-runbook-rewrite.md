# Report 323 — HPD-17/18 close-out + RUNBOOK rewrite

## 1. Identification
- **Report number:** 323
- **Current Task ID:** HPD-17 / HPD-18 (close-out)
- **Predecessor Task:** report 322 (HPD-17 repo side — Caddy edge)

## 2. Objective
The hosted SaaS is now live end-to-end (`app.ember.vanter.net`,
`app.ember.vanter.net/console`, `api.ember.vanter.net`). This task brings the docs
and the task tracker in line with what was actually deployed: rewrite the
`deploy/RUNBOOK.md` sections that still described the dropped Cloudflare Tunnel,
and close HPD-17/HPD-18 in `PROGRESS.md`.

## 3. Modified Files
- `deploy/RUNBOOK.md`
- `PROGRESS.md`

## 4. What Changed?

### `deploy/RUNBOOK.md`
- **Header:** added a dated "edge reworked" note — Tunnel dropped, `api.` is a
  proxied A record → VM external IP, `caddy` on the VM terminates a Cloudflare
  Origin Cert on `:443`, inbound `:443` restricted to Cloudflare ranges via
  `allow-cf-https`. Noted there is no delegated `ember.vanter.net` zone
  (everything in `vanter.net`) and that landing/SPA are Workers, not Pages.
- **Topology diagram:** redrawn — `vanter.net` zone; `ember` / `ember-app`
  Workers; `api.` as a proxied A record to `34.44.144.220`; the VM box now shows
  `caddy :443 → app:8080` with the cert paths instead of `cloudflared`; firewall
  line shows `IAP SSH + allow-cf-https`.
- **Login request flow:** rewritten for the two-hop TLS path (edge public cert →
  Caddy Origin Cert → `app:8080`), WebSocket note, and the `CF-Connecting-IP` /
  `EMBER_RATELIMIT_TRUSTED_PROXIES` behaviour.
- **Phase 4 index:** HPD-15 struck (not done / not needed); HPD-17 struck
  (superseded); HPD-18 marked done; HPD-16/19 flagged still pending. Added two
  new executed blocks:
  - **"HPD-18 — executed 2026-08-31 → 09-01":** Worker `ember-app` settings (root
    `frontend`, `pnpm run build:pages`, `npx wrangler deploy`, the three build
    vars), the custom-domain move, the `/console` = same-bundle note, and the
    seed platform operator (`platform-admin@ember.local` / `ChangeMe123!`,
    change on first login).
  - **"HPD-17 — executed 2026-09-01":** the full runnable sequence — DNS A
    record, the `allow-cf-https` `gcloud` command with the Cloudflare IPv4
    ranges, Origin Cert creation + staging to `/opt/ember/caddy/`, shipping the
    new compose file (note that `deploy.sh` does not copy it) and
    `up -d --remove-orphans`, the `Full (strict)` / Configuration Rule step, and
    the `curl …/v1/public/ping` smoke.
- **"VM lost" recovery:** replaced the "`cloudflared` reconnects the Tunnel"
  line with edge-specific steps — snapshot carries `/opt/ember/caddy/`; the
  external IP changes on rebuild unless static, so the `api.` A record must be
  updated; nothing to reconnect once Caddy is up.
- **Secret rotation:** `./deploy/deploy.sh latest` (not `v0.1.0` — there is no
  `:v0.1.0` image); `TUNNEL_TOKEN` obsolete, delete it; added an Origin-Cert
  rotation procedure (`restart caddy`, no secret round-trip).
- **HPD-11 external-IP deviation / HPD-14 uptime-check notes:** bracketed updates
  — inbound `:443` is now open to Cloudflare ranges; the uptime check is
  unblocked because `api.ember.vanter.net` resolves.

### `PROGRESS.md`
- New "Last Completed Task (report 323)" entry; "Current Active Task" set to
  none with the remaining hosted-deploy items (HPD-16, HPD-19, HPD-20, HPD-21,
  HPD-22) and the operator TODO list (console password, SSL mode, static IP).
- System Health rewritten: branch `main`, reports 321–323 are infra/docs only,
  no application code touched, edge verified live 2026-09-01.
- Task Queue: **HPD-17 and HPD-18 checked `[x]`**; HPD-15 marked `[~]` (not
  needed); HPD-16/HPD-19/HPD-20 descriptions updated (no more `TUNNEL_TOKEN`).

## 5. Why It Changed?
An operations runbook that describes infrastructure that no longer exists is a
liability during an incident — someone would look for a `cloudflared` container
or a `TUNNEL_TOKEN` that isn't there. The task tracker likewise needs to reflect
that `app.`/`api.` are live so the remaining Cloudflare hardening (HPD-19) and
the media/CDN work (HPD-16) are what's actually next, not a re-run of HPD-17/18.

## 6. Verification
- Docs/tracker only — no build. Cross-checked every `Tunnel`/`cloudflared`/
  `TUNNEL_TOKEN` occurrence in `RUNBOOK.md`; the remaining hits are either
  `--tunnel-through-iap` (unrelated `gcloud` flag) or explicitly-labelled
  historical text inside dated "executed" blocks.
- Live edge (done by the operator in-session): `curl -s https://api.ember.vanter.net/v1/public/ping`
  → `pong` (`HTTP/2 200`, `via: 1.1 Caddy`, `server: cloudflare`);
  `curl -s https://app.ember.vanter.net/env-config.js` → correct `https://` +
  `wss://` values; console login succeeded.

## 7. Follow-up (operator / later tasks)
- HPD-19: Cloudflare WAF + Always-Use-HTTPS + rate-limit `/v1/auth/*` + block
  `/v1/actuator/*`.
- HPD-16: R2 `ember-media-prod` + `cdn.ember.vanter.net`; then real `MINIO_*` in
  `ember-prod-env` (HPD-20) and drop the dead `TUNNEL_TOKEN` line.
- HPD-20: create the Cloud Monitoring uptime check + alert on
  `api.ember.vanter.net/v1/public/ping` (now resolvable).
- Confirm the console admin password is off `ChangeMe123!`.
- Confirm Cloudflare SSL mode is `Full (strict)` for `api.ember.vanter.net`.
- Promote the VM external IP to a static address so the `api.` A record survives
  a VM rebuild.
