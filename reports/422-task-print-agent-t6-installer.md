# Report 422 — EMB-PRINT-AGENT T6: Windows installer (jlink / jpackage / Inno Setup + CI)

- **Report number:** 422
- **Current Task ID:** EMB-PRINT-AGENT T6 (plan `docs/superpowers/plans/2026-09-08-print-agent-installer.md`)
- **Predecessor Task:** T5 — frontend admin (report 421)

## Objective

Turn `printing-agent/` from a `java -jar` + hand-written `agent.properties` deployment into a
one-`.exe` Windows install packaged exactly like `ember-hub/`: jlink JRE → jpackage app-image
→ Inno Setup `.exe`, auto-start via a `{commonstartup}` shortcut, on-disk state under
`%ProgramData%\EmberAgent\`. No secrets or URLs are baked in — the backend URL arrives from
`POST /printing/agents/pair`.

## Modified Files

Created:
- `printing-agent/build-installer.ps1`
- `printing-agent/jlink-modules.txt`
- `printing-agent/build.env.example`
- `printing-agent/installer/EmberAgent.iss`
- `printing-agent/installer/Iniciar Ember Agent.cmd`
- `printing-agent/installer/make-icon.ps1`
- `printing-agent/installer/ember-agent.ico` (binary, generated once by `make-icon.ps1`)
- `reports/422-task-print-agent-t6-installer.md`

Modified:
- `printing-agent/.gitignore` (+ `dist/`, `build.env`)
- `.github/workflows/lint.yml` (+ `build-print-agent` job)
- `printing-agent/README.md` (rewritten to the installer flow)
- `PROGRESS.md`

## What Changed?

**`build-installer.ps1`** — cloned from `ember-hub/build-installer.ps1`, stripped of everything
Hub-specific (Postgres/MinIO vendor fetch, `build-frontend.ps1`, `build.env` reading, firewall
rule). Three `-Stage` values:
- `runtime` — `jlink --add-modules <jlink-modules.txt> --strip-debug --no-header-files
  --no-man-pages --compress=2 --include-locales=en,es --output dist/runtime`, then asserts
  `dist/runtime/bin/java.exe --version`.
- `appimage` — `mvn -f printing-agent/pom.xml -q -DskipTests package`, picks the shaded
  `target/printing-agent-*.jar` (excludes `original-`), copies it to
  `dist/jpackage-input/printing-agent.jar`, then `jpackage --type app-image --name "Ember
  Agent" --app-version <pom version, -SNAPSHOT stripped> --main-class com.vanter.emberagent.Main
  --runtime-image dist/runtime --icon installer/ember-agent.ico`. Copies `Iniciar Ember
  Agent.cmd` next to the launcher; asserts `dist/app-image/Ember Agent/Ember Agent.exe`.
- `installer` — locates `ISCC.exe` (PATH + the three standard install dirs), runs
  `iscc /DAppVersion=<v> EmberAgent.iss`, asserts `dist/EmberAgentSetup-<v>.exe`.

`Get-AgentVersion` reads `<version>` from `printing-agent/pom.xml` (not `backend/pom.xml`) —
the agent versions itself (`0.1.0-SNAPSHOT` → `0.1.0`).

**`jlink-modules.txt`** — `java.se` + `jdk.crypto.ec` / `jdk.crypto.cryptoki` /
`jdk.unsupported` (JNA native access) / `jdk.management` / `jdk.zipfs` / `jdk.localedata` /
`jdk.charsets`. No `jdk.jdwp.agent` / `jdk.management.agent` (Hub had them; the agent has no
remote-debug/JMX need).

**`EmberAgent.iss`** — from `EmberHub.iss`, dropped: the `#define`s for ports/URLs, the
per-install `hub.env` secret generation (`[Code]` LCG/RandomHex), the `[Run]`/`[UninstallRun]`
`netsh` firewall rules (the agent is outbound-only, never listens). Kept: `PrivilegesRequired=admin`,
`{commonappdata}\EmberAgent` + `\logs` dirs, three shortcuts (`{group}`, `{commondesktop}`
plain; `{commonstartup}` with `Parameters: "--tray"`), and a `CurUninstallStepChanged` prompt
that asks before `DelTree`-ing `%ProgramData%\EmberAgent` so a reinstall keeps the credential.
Shortcuts point straight at `Ember Agent.exe` (no `.cmd` shim needed — the agent has no env
file to pre-load, unlike the Hub).

**`Iniciar Ember Agent.cmd`** — a thin `start "" "%~dp0Ember Agent.exe" %*` passthrough,
shipped next to the launcher for parity with the Hub layout.

**`make-icon.ps1` / `ember-agent.ico`** — identical PNG-in-ICO wrapper as the Hub, sourcing
`frontend/src/assets/ember.png` at 256×256. Ran once; the 9.7 KB `.ico` is committed.

**`.github/workflows/lint.yml`** — new `build-print-agent` job (`windows-latest`, Temurin 17):
`mvn -f printing-agent/pom.xml -B -DskipTests package` → `pwsh build-installer.ps1 -Stage
appimage` → assert the launcher exists. The Inno `.exe` stage stays manual (`iscc` is not on
the GH runner) — same call the Hub made.

**`README.md`** — §1–§6 replaced with: download `EmberAgentSetup-x.y.z.exe` from the admin,
run it (no Java), paste the pairing code, done (auto-starts on log-on). Printer field is now a
dropdown. Task Scheduler section removed (automatic now); `agent.properties` demoted to a
one-line "Advanced / non-Windows" note. Troubleshooting kept, `conexión perdida` line reworded
to the dashboard's `Reintentando` state.

## Why It Changed?

Spec §2.5: the agent must install and update like the Hub — embedded runtime (no JDK on the
till PC), `%ProgramFiles%` refreshed wholesale on update while `%ProgramData%` (credential,
logs) is preserved, auto-start with no Windows service or control socket (spec §6 decision 1 —
a single process is the whole app). The `{commonstartup}` shortcut passing `--tray` is the
exact Hub pattern (`EmberHub.iss` line 46). No firewall rule because, unlike the Hub which
listens for LAN terminals, the agent only dials out over WebSocket. CI gains an app-image
smoke so a broken pom or script is caught before T7's manual pass on real hardware.

## Verification

- `pwsh printing-agent/build-installer.ps1 -Stage appimage` on this Windows 10 box (Temurin
  JDK 17): **success**. `jlink` runtime built and `java.exe --version` ran; `mvn package`
  produced the shaded jar; `jpackage` produced `dist/app-image/Ember Agent/` containing
  `Ember Agent.exe` (431 KB), `runtime\bin\java.exe`, `app\printing-agent.jar`
  (`Main-Class: com.vanter.emberagent.Main`), `Iniciar Ember Agent.cmd`, `Ember Agent.ico`.
- `-Stage installer` **not run here** — Inno Setup (`iscc`) is not installed on this machine.
  Producing `EmberAgentSetup-0.1.0.exe` and exercising install/pair/update/uninstall is
  exactly T7 (manual verification on a clean Windows PC).
- No Java or `pom.xml` changed; `mvn -f printing-agent/pom.xml test` remains **41/41**
  (the `package` run inside the build compiled the module cleanly).
