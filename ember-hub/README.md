# Ember Hub — Windows installer build

Ember Hub is `backend/`'s jar run with `SPRING_PROFILES_ACTIVE=hub` (boots the
Swing `HubDashboard` instead of a headless server). This folder packages it into
a Windows installer. Design: `docs/superpowers/specs/2026-09-05-hub-installer-design.md`.
Plan: `docs/superpowers/plans/2026-09-05-hub-installer.md`.

## Prerequisites (build machine, Windows x64)

- JDK 17 on `PATH` (`java`, `jlink`, `jpackage`)
- `pnpm`, and the repo's `backend/mvnw.cmd`
- [Inno Setup 6](https://jrsoftware.org/isdl.php) (`iscc.exe`) — for the `installer` stage only
- Internet access (one-time, to fetch the portable Postgres + MinIO binaries)

## One-time setup

```powershell
Copy-Item ember-hub\build.env.example ember-hub\build.env   # defaults are prod; edit only to override
powershell -ExecutionPolicy Bypass -File ember-hub\fetch-vendor-binaries.ps1
```

The prod license **public** key (`ember-hub\keys\hub-public-key.der`, X.509
SubjectPublicKeyInfo DER) is committed, so every build verifies `license.key`
files against the same key with no per-machine step. It is the public half of the
cloud's `HUB_LICENSE_PRIVATE_KEY`; if that key is ever rotated, re-export it from
`gs://ember-backups-ember-prod-vanter/keys/hub-public-key.der` and commit the new
one. The private key is never in this repo.

`fetch-vendor-binaries.ps1` downloads PostgreSQL 16.6-1 (EDB "binaries only") and
MinIO `RELEASE.2025-04-22`, verifies their pinned SHA256, prunes pgAdmin/docs/etc.
from the Postgres tree, and stages `bin/lib/share` + `minio.exe` under
`ember-hub\.vendor-cache\staging\`. If a hash constant is `REPLACE_ME` it prints
the computed value and stops — paste it in and re-run.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File ember-hub\build-installer.ps1
# stages: runtime | appimage | installer   (default: all)
#   runtime   -> ember-hub\dist\runtime            (jlink JRE, ~48 MB)
#   appimage  -> ember-hub\dist\app-image\Ember Hub (jpackage + binaries, ~380 MB)
#   installer -> ember-hub\dist\EmberHubSetup-<version>.exe
```

`<version>` comes from `backend/pom.xml`'s `<version>` (minus `-SNAPSHOT`).
Output and `.vendor-cache/` are gitignored.

## Install layout (on the customer PC)

| Path | Contents | Lifecycle |
|---|---|---|
| `%ProgramFiles%\Ember Hub\` | `Ember Hub.exe` (jpackage launcher), `Iniciar Ember Hub.cmd` (shim), `runtime\`, `app\ember-hub.jar`, `pgsql\`, `minio\`, `hub-public-key.der` | replaced on every update |
| `%ProgramData%\EmberHub\` | `data\`, `logs\`, `backups\`, `license.key`, `hub-state.json`, `hub.env` | survives updates; uninstall asks before deleting |

`hub.env` is written once by the installer (kept as-is across updates) and holds
the paths, ports, cloud URLs, and a per-install random `JWT_SECRET` /
`PLATFORM_JWT_SECRET` (the Spring context won't boot without them). Re-running an
installer over a pre-secrets `hub.env` appends the two missing lines in place.

Auto-start: a shortcut in the common Startup folder runs `Iniciar Ember Hub.cmd`,
which loads `hub.env`, sets `SPRING_PROFILES_ACTIVE=hub`, and launches the app
with `--autostart`. The installer also adds an inbound firewall rule for
`EMBER_HUB_SERVER_PORT` (private + domain profiles only) so other PCs on the LAN
can reach `http://<hub-ip>:<port>/` — which redirects to the SPA at
`http://<hub-ip>:<port>/app/`.

The `license.key` is **not** in the installer — the customer receives it after
purchase and installs it via the dashboard's "Seleccionar license.key…" button
(or by dropping it at `%ProgramData%\EmberHub\license.key`).

## Manual verification checklist (no CI covers the .exe)

1. Install on a clean Windows PC with **no Java**. Reboot / re-login → the
   dashboard opens and starts the services by itself.
2. From a second PC on the same LAN, open `http://<hub-ip>:<port>/` → it
   redirects to `/app/` and the SPA loads; a waiter can take an order and the
   kitchen screen sees it.
3. With no `license.key` present, start → dashboard shows the license error →
   "Seleccionar license.key…" → pick the file → it activates and writes
   `hub-state.json`.
4. Build a higher `<version>`, run its installer over the existing one → app
   files replaced, `%ProgramData%` intact, Flyway migrates on next start.
5. Uninstall, choose **No** to keep data → reinstall → history intact.
6. Re-confirm the five boot-error paths from report 236 (port in use, missing
   license, wrong-hardware fingerprint, corrupt PGDATA, non-empty initdb dir)
   still show actionable Spanish messages.
