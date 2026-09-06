# Report 381 — fix(hub): installer secrets, prod URLs, committed public key

## Identification
- **Report:** 381
- **Task:** HUB-03 follow-up — installer defects found during first real customer install
- **Predecessor:** report 366 (prod SPA env-config hotfix); on branch `spec/hub-installer` the
  predecessor task is T9 (`ember-hub/README.md`).

## Objective
A first install on a customer PC could not boot the Hub. Three independent installer/packaging
defects, all reproducible on any fresh install:

1. **License verification failed** — `ember-hub/keys/hub-public-key.der` in the repo was a
   throwaway dev key, not the public half of prod's `HUB_LICENSE_PRIVATE_KEY`. Every
   prod-issued `license.key` failed signature verification (`LicenseKeyParser.parseAndVerify` →
   "La firma de license.key no es válida"). Already flagged as pending in `deploy/RUNBOOK.md`
   (the fresh prod keypair's public key was uploaded to
   `gs://ember-backups-ember-prod-vanter/keys/hub-public-key.der` but never landed in the build).
2. **Spring context could not start** — `application.yml` requires `JWT_SECRET` and
   `PLATFORM_JWT_SECRET` (no defaults). `application-hub.yml` does not set them and the
   installer's generated `hub.env` never contained them → `PlaceholderResolutionException` →
   `ApplicationContextException: Unable to start web server`.
3. **First-boot activation pointed at the wrong host** — baked
   `EMBER_HUB_ACTIVATION_URL=https://api.vanter.com/hub-activations`. Prod is
   `https://api.ember.vanter.net` and serves the tenant API under the `/v1` context path, so
   `HubProvisioningRunner` (which uses the URL verbatim) could never reach `/hub-activations`.

Also fixed a cosmetic trap that cost real debugging time: the bare host `http://<hub-ip>:<port>/`
returned a raw 401 (the SPA is served at `/app/`), and `application-hub.yml`'s comment about
"serves everything at /" was stale.

## Modified Files
- `.gitignore`
- `ember-hub/keys/hub-public-key.der` (now tracked; prod key, SHA256 `6ce631e5…848b`)
- `ember-hub/installer/EmberHub.iss`
- `ember-hub/build.env.example`
- `ember-hub/README.md`
- `docs/superpowers/plans/2026-09-05-hub-installer.md`
- `backend/src/main/resources/application-hub.yml`
- `backend/src/main/java/com/vanter/ember/hub/config/HubSpaRootController.java`
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`

## What Changed?
- **`.gitignore`**: `ember-hub/keys/` → `ember-hub/keys/*` + `!ember-hub/keys/hub-public-key.der`.
  The **public** key is not a secret; committing it makes every build verify licenses against
  the same prod key with no per-machine step. The private key stays ignored.
- **`ember-hub/keys/hub-public-key.der`**: replaced the dev key with the prod one pulled from
  `gs://ember-backups-ember-prod-vanter/keys/hub-public-key.der`.
- **`EmberHub.iss`**:
  - `[Code]` now generates a 64-hex-char random `JWT_SECRET` and `PLATFORM_JWT_SECRET`
    (`RandomHex`, seeded by `Randomize`) and writes them into `hub.env` on a fresh install.
  - `EnsureHubEnvSecrets` appends only the missing secret line(s) when `hub.env` already exists
    (the file is otherwise preserved across updates), so re-running an installer over a
    pre-secrets install repairs it in place.
  - `#define` fallbacks for `EmberHubActivationUrl` / `EmberHubHeartbeatUrl` → the
    `https://api.ember.vanter.net/v1/...` form.
- **`build.env.example`** + **plan doc snippet**: activation/heartbeat URLs corrected to the
  `/v1/` prod host, with a note that the URL is used verbatim.
- **`application-hub.yml`**: comment rewritten to describe the actual layout (API at `/`, SPA at
  `/app/`, `/` redirects).
- **`HubSpaRootController`**: added `GET /` → `RedirectView("/app/")` (`@Profile("hub")`).
- **`SecurityConfig`**: `permitAll` for `GET /` (on the cloud profile no controller maps `/`, so
  this only turns a would-be 401 into a 404).
- **`README.md`**: dropped the manual "place the public key" step; documented the auto-generated
  secrets, the in-place `hub.env` repair, and the `/` → `/app/` redirect.

## Why It Changed?
Each defect made a fresh install unbootable, and none is covered by a test (the `.exe` has no
CI). Generating the JWT secrets in the installer keeps the change contained to `ember-hub/` and
gives every install a distinct key (a shared static secret would let anyone forge tenant tokens
for any Hub). Committing the public key trades a "never commit anything under keys/" rule —
overly broad, since the public key is safe — for reproducible builds and one less manual step
that was already skipped once.

## Verification
`cd backend && ./mvnw test` — exit 0 (full suite). The `.iss` / `.gitignore` / key changes are
not test-covered; they will be exercised by HUB-03 T10 (manual Windows install).
