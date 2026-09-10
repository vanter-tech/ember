# Report 426 — Downloads hosting live + `v0.2.3` prod deploy

## 1. Identification
- **Report number:** 426
- **Current Task:** ad-hoc — stand up installer download hosting; deploy the print-agent backend to prod
- **Predecessor Task:** report 425 — landing `/info/descarga` page

## 2. Objective
Make the Windows installers reachable from the public URLs the landing / SaaS
already point at, and get the print-agent code-pairing backend (T1/V10) plus the
cancel-pending-jobs endpoint into prod.

## 3. Modified Files
- `deploy/caddy/Caddyfile` (PR #111)
- `landing/src/i18n/ui.ts` (PR #112)
- (no repo change for the infra stand-up or the deploy — those are ops actions)

## 4. What Changed?

### Downloads hosting (GCS + Cloudflare + Caddy)
- **Bucket** `gs://ember-downloads-prod` — `us-central1`, uniform bucket-level
  access, `allUsers:objectViewer` (public read). `EmberAgentSetup-0.1.0.exe` +
  `EmberAgentSetup-latest.exe` uploaded (`Cache-Control` long on the versioned
  object, 300 s on the alias — Cloudflare overrides to a ~4 h edge TTL, so each
  release must purge the CF cache for the `-latest` URL).
- **Caddy** (PR #111) — new `downloads.ember.vanter.net` site block:
  `GET`/`HEAD` only, `rewrite` prepends `/ember-downloads-prod`, `reverse_proxy`
  to `https://storage.googleapis.com` with the `Host` header rewritten. Deployed
  to the VM by hand (scp + `docker compose restart caddy`).
- **DNS** — proxied `A` record `downloads` in the `vanter.net` Cloudflare zone →
  the `ember-prod` VM external IP.
- **Origin cert** — the Cloudflare Origin Certificate on the VM was `api.`-only;
  replaced with one scoped to `api.ember.vanter.net` + `downloads.ember.vanter.net`
  (no wildcard). Old `api.`-only and a briefly-used `*.vanter.net` cert deleted.
- Verified: `curl -I https://downloads.ember.vanter.net/EmberAgentSetup-latest.exe`
  → 200, `content-length: 48499028`; `api.` still 200.

### Landing copy (PR #112)
- Removed "Java" from public copy (`/info/local`, `/info/descarga`) — internal
  stack detail. Download buttons `Descargar .exe` / `Download .exe` →
  `Descargar` / `Download`. ES/EN parity 467/467.

### Prod deploy `v0.2.3`
- Tagged `v0.2.3` on `main` (`9b52527b`) → `backend-image.yml` built
  `ember-backend:0.2.3`. `./deploy/deploy.sh 0.2.3` from Cloud Shell (arg without
  the `v` — CI strips it, compose reads `EMBER_IMAGE_TAG`).
- Flyway ran **V10** clean (schema 9 → 10, `Successfully applied 1 migration`),
  app healthy, no downtime.
- Verified in the admin UI: "Generar código" returns a 10-char pairing code;
  "Limpiar pendientes" cancels stuck `PENDING` jobs.

## 5. Why It Changed?
The landing downloads page (report 425) linked to URLs that 404'd — nothing was
hosting the `.exe`. Cloudflare Pages/Workers cap assets at 25 MiB and the agent
installer is ~48 MB, so it had to be a bucket behind the edge. The backend half
of code pairing shipped in `main` via #109 but prod was still `v0.2.2`, so the
agent could only connect via the legacy manual API-key path and the two new
admin buttons 404'd.

## Follow-ups (not code)
- Build + publish `EmberHubSetup-latest.exe` (Hub card still 404s).
- Each release: upload versioned + overwrite `-latest`, then purge the Cloudflare
  cache for the `-latest` URL.
- Optional frontend PR: after prod verification, drop the raw API key from
  `CreateAgentModal` / `PrintingSettings`, leaving the code as the only visible
  path (keep the API-key `<details>` as a labelled fallback).
- T7: run `printing-agent/VERIFY.md` on a clean Java-less Windows VM.

## Verification
- `landing` `pnpm run build` — clean, 28 pages, i18n parity 467/467.
- Prod: Flyway at v10; `POST /printing/agents/pair` and
  `/printing/jobs/cancel-pending` no longer 404; both buttons work in the UI.
- `curl -I https://downloads.ember.vanter.net/EmberAgentSetup-latest.exe` → 200.
