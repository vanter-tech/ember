# Report 322 — HPD-17 (repo side): Caddy edge replaces the Cloudflare Tunnel

## 1. Identification
- **Report number:** 322
- **Current Task ID:** HPD-17 (Cloudflare track — approach change)
- **Predecessor Task:** report 321 (HPD-18 repo side — `ember-app` Worker config)

## 2. Objective
Serve `api.ember.vanter.net` through a **proxied Cloudflare A record → the VM's
external IP** instead of a Cloudflare Tunnel. That requires something on the VM
listening on a public port and terminating TLS; this change swaps the
`cloudflared` container for a `caddy` reverse-proxy container and adds its config.
The DNS record, the GCP firewall rule, the Cloudflare Origin Certificate, and the
`Full (strict)` SSL mode are operator steps, not in this commit.

## 3. Modified Files
- `deploy/docker-compose.prod.yml` — modified (drop `cloudflared`, add `caddy` + `caddy_data` volume)
- `deploy/caddy/Caddyfile` — new
- `deploy/.env.prod.example` — modified (drop `TUNNEL_TOKEN`, note the edge)

## 4. What Changed?
- **`deploy/docker-compose.prod.yml`:** the `cloudflared` service (outbound tunnel
  dial-out, `TUNNEL_TOKEN`) is replaced by a `caddy:2` service that publishes
  `443:443`, bind-mounts `/opt/ember/caddy/Caddyfile` and the Origin Cert PEMs
  (`origin.pem` / `origin.key`) read-only, and keeps a `caddy_data` named volume.
  `depends_on: app` unchanged. `app` still publishes only `127.0.0.1:8081:8081`
  (actuator) — Caddy reaches the HTTP port as `app:8080` on the compose network.
- **`deploy/caddy/Caddyfile` (new):** one site block for `api.ember.vanter.net` —
  `tls /certs/origin.pem /certs/origin.key` (explicit cert, so Caddy does no ACME),
  `reverse_proxy app:8080`, `encode gzip`. Comments record that the PEMs are a
  Cloudflare Origin Certificate and that Cloudflare must be on `Full (strict)`.
- **`deploy/.env.prod.example`:** `TUNNEL_TOKEN` line removed; the rate-limiter
  comment now says "the caddy edge"; a new `# --- Edge ---` note explains the
  proxied-A-record + Origin-Cert path. `EMBER_RATELIMIT_TRUSTED_PROXIES=172.16.0.0/12`
  is unchanged and still correct — Caddy sits on the docker bridge and forwards
  Cloudflare's `CF-Connecting-IP` untouched, which `AuthRateLimiterFilter` keys on.

## 5. Why It Changed?
The operator opted out of the Cloudflare Tunnel since the VM already has an
external IP. A Tunnel needs no inbound port; a proxied A record does, so the stack
now needs a public listener that terminates the Cloudflare-to-origin TLS hop.
Caddy is the smallest correct option: it sets `X-Forwarded-*` sanely by default
(nginx would need ~10 `proxy_set_header` lines) and takes an explicit cert in one
directive. Keeping `app` off any host port preserves the "only the edge is
reachable, Postgres never leaves the VM" property the Tunnel gave.

## 6. Verification
- `docker compose -f deploy/docker-compose.prod.yml config` parses the structure
  and fails only at `SPRING_DATASOURCE_PASSWORD` interpolation (populated on the VM
  from Secret Manager) — i.e. the compose schema and the new `caddy` block are
  valid. Full render is not runnable on the Windows host (the absolute POSIX
  `env_file: /opt/ember/.env` path), same as before this change.
- No application code touched; backend/frontend test suites unaffected.

## 7. Operator follow-up (not in this commit)
1. **DNS:** Cloudflare → DNS → add `A  api  34.44.144.220`, **Proxied**.
2. **GCP firewall:** `allow-cf-https` — `INGRESS ALLOW tcp:443` from the Cloudflare
   IPv4 ranges only (`cloudflare.com/ips-v4`).
3. **Origin Cert:** Cloudflare → SSL/TLS → Origin Server → Create Certificate for
   `api.ember.vanter.net`; put cert/key at `/opt/ember/caddy/origin.pem` /
   `origin.key` (key `chmod 600`); `mkdir -p /opt/ember/caddy` and drop the
   `Caddyfile` there.
4. **Push the compose file** to the VM (`deploy.sh` does not copy it):
   `gcloud compute scp deploy/docker-compose.prod.yml ember-prod:/tmp/...` →
   `sudo mv` into `/opt/ember/`.
5. **Secret:** edit `ember-prod-env` to drop `TUNNEL_TOKEN`; `./deploy/deploy.sh latest`.
6. **Cloudflare SSL mode:** `Full (strict)`.
7. **Smoke:** `curl -s https://api.ember.vanter.net/v1/public/ping` → `pong`; real
   login on `https://app.ember.vanter.net`.
8. **RUNBOOK.md** still describes the Tunnel topology (diagram, provisioning,
   recovery, secret rotation) — rewrite that section to the Caddy edge as a
   separate doc task.
