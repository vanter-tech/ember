# Report 324 — HPD-19: Cloudflare edge hardening

## 1. Identification
- **Report number:** 324
- **Current Task ID:** HPD-19
- **Predecessor Task:** report 323 (HPD-17/18 close-out + RUNBOOK rewrite)

## 2. Objective
Apply the Cloudflare-side hardening for the hosted deployment: force HTTPS, add
HSTS, put a managed WAF in front, edge-rate-limit the auth endpoints, and block
`/v1/actuator/*` at the edge. Plus pin `api.ember.vanter.net` to SSL
`Full (strict)` without changing the zone default. All dashboard work; this
commit is the documentation of what was applied.

## 3. Modified Files
- `deploy/RUNBOOK.md`
- `PROGRESS.md`

## 4. What Changed?

### Cloudflare (zone `vanter.net`, Free plan) — applied by the operator
- **Always Use HTTPS:** On (zone-wide) — SSL/TLS → Edge Certificates.
- **HSTS:** enabled — `max-age` 6 months, `includeSubDomains` on, **preload
  off**, No-Sniff header (`X-Content-Type-Options: nosniff`) on. The backend
  already emits HSTS for `api.` responses; the edge setting also covers the
  Worker hostnames (`ember`, `ember-app`).
- **Managed WAF:** Free plan → the "Cloudflare Free Managed Ruleset" is applied
  automatically, no UI, nothing to deploy.
- **Rate limiting rule** `api auth rate limit` (Security → Security rules):
  URI Path starts with `/v1/auth/` → 10 requests / 1 minute per IP → Block for
  1 minute. Edge companion to the in-app `AuthRateLimiterFilter`.
- **Custom rule** `block api actuator`: URI Path starts with `/v1/actuator` AND
  Hostname eq `api.ember.vanter.net` → Block. Defence in depth — actuator runs
  on the loopback-only `:8081` management port and never reaches Caddy, so `api.`
  already 404s that path.
- **Configuration Rule** `api ssl full strict`: When
  `http.host eq "api.ember.vanter.net"` → SSL = `Full (strict)`. The zone default
  stays `Full` (it carries other proxied origins). Verified:
  `curl -s -o /dev/null -w '%{http_code}' https://api.ember.vanter.net/v1/public/ping`
  → `200` (a `526` would mean the origin cert doesn't validate).
- Console admin password rotated off the seed `ChangeMe123!`.

### `deploy/RUNBOOK.md`
- New "HPD-19 — executed 2026-09-01" block listing everything above.
- The HPD-17 executed block's step 5 (SSL mode) rewritten from "set the zone to
  Full (strict)" to the scoped Configuration Rule that was actually used, plus a
  `%{http_code}` smoke line (200 vs 526).
- Phase-4 index: HPD-19 bullet flipped from "Still pending" to done.

### `PROGRESS.md`
- "Last Completed Task (report 324)" entry; HPD-19 removed from the remaining
  list; operator-TODO items (console password, SSL strict) recorded as done.
- Task Queue: **HPD-19 checked `[x]`**.

## 5. Why It Changed?
HPD-19 is the security baseline for exposing `api.ember.vanter.net` directly (the
edge is now the only thing between the internet and the VM). Rate-limiting auth at
the edge stops credential-stuffing volume before it reaches the app; the actuator
block and `Full (strict)` close the two paths most likely to leak internals or be
MITM'd between Cloudflare and the origin. Scoping strict to a Configuration Rule
avoids breaking the zone's other proxied origins that can't take strict yet.

## 6. Verification
- Docs/tracker only — no build. `grep` of `RUNBOOK.md` for stale HPD-19 "pending"
  wording: none left.
- Live (operator, in-session): `api.ember.vanter.net/v1/public/ping` → `200`
  after the Configuration Rule deploy; both Security rules show as deployed.

## 7. Follow-up
- HPD-16: R2 `ember-media-prod` + `cdn.ember.vanter.net`.
- HPD-20: `ember-prod-env` — drop the dead `TUNNEL_TOKEN`, add real `MINIO_*`;
  create the Cloud Monitoring uptime check + alert on
  `api.ember.vanter.net/v1/public/ping`.
- HPD-21 restore test; HPD-22 2-device E2E walkthrough + close-out.
- Optional: Bot Fight Mode (Security → Bots), free.
