# Report 416 — Print agent: desktop app + Windows installer — implementation plan

## 1. Identification
- **Report:** 416
- **Task:** EMB-PRINT-AGENT — write the implementation plan for the cloud print agent's install/operation redesign, and load its task queue into `PROGRESS.md`
- **Predecessor:** 415 (SaaS favicon distinct from landing)

## 2. Objective
Turn the spec `docs/superpowers/specs/2026-09-08-print-agent-installer-design.md` (status: in review) into an executable, task-by-task implementation plan, and register every task in `PROGRESS.md` so the work proceeds one context at a time with `/clear` between (CLAUDE.md §7). No code in this task — planning + memory only.

## 3. Modified Files
- `docs/superpowers/plans/2026-09-08-print-agent-installer.md` — created (the plan)
- `PROGRESS.md` — modified (Current Active Task; new `### EMB-PRINT-AGENT` task-queue section with T1–T7; F-24 security-debt line annotated)
- `reports/416-print-agent-installer-implementation-plan.md` — this report

## 4. What Changed?
Wrote a 7-task plan following `superpowers:writing-plans` (header + Global Constraints + per-task Files/Interfaces/bite-sized steps with real code):

- **T1 backend** — `V10` migration (`pairing_codes` table + `discovered_printers` jsonb + `paired_at` on `print_agents`, all idempotent). `PrintAgentService.createPairingCode` (mints + rotates the API key, stashes the plaintext on the single-use, 15-min code row), `redeemPairingCode` (`POST /printing/agents/pair`, `permitAll`, `PairAttemptGuard` IP throttle cloned from `PinAttemptGuard`), `saveDiscoveredPrinters` (`POST /printing/agents/me/discovered-printers`). `PrintAgentResponse` gains `paired` + `discoveredPrinters`. `PrintAgentPairingController` new; `SecurityConfig` += `POST /printing/agents/pair`; `SecurityAuditTest` rows; `pnpm run openapi` regen.
- **T2 agent** — `StatusHub` (observable `Phase`/`Snapshot`/`JobRecord`, no Swing dep), `AgentPaths` (`%ProgramData%\EmberAgent`), `AgentCredential`, `CredentialStore` + `DpapiCredentialStore` (JNA `Crypt32Util` machine scope, `credential.bin`) + `PlaintextCredentialStore` fallback + `CredentialStores.forThisMachine()`. `jna-platform:5.14.0` added to `printing-agent/pom.xml`. `AgentConfig.resolve` (credential store first, `agent.properties` fallback, empty Optional otherwise). Closes **F-24**.
- **T3 agent** — `PairingClient.redeem` (saves credential), `WindowsPrinterEnumerator` (`Get-Printer … | ConvertTo-Json`, single-object + array parsing, `looksLikeInkjet` regex), `DiscoveredPrintersClient` (best-effort report). Fixture + mockwebserver3 tests.
- **T4 agent** — `AgentRunner` (the current `Main` loop, behavior-identical, now feeding `StatusHub` + reporting discovery on connect), Swing `AgentDashboard` / `AgentTrayIcon` (clone of `HubTrayIcon`) / `PairDialog`, `DiagnosticsReport`, `Main` rewired with `--headless` / `--tray`. `PrintJobHandler` gets a nullable `StatusHub` param (no dispatch-logic change). Documented v1 deviation: dashboard "Impresora" zone is read-only + local test print, not a "Guardar" (registration stays in admin per spec §5).
- **T5 frontend** — `CreateAgentModal` leads with the pairing code (raw key under `<details>`), `PrintingSettings` "Nuevo código" button + Emparejado/Sin emparejar badge + `.exe` download link, `AddPrinterModal` `windowsQueueName` → shadcn `<Select>` from `agent.discoveredPrinters` (+ "Otra…" escape, auto-`DRIVER` on inkjet). i18n ES/EN table. Test extensions.
- **T6 installer** — `printing-agent/build-installer.ps1` (`runtime`/`appimage`/`installer` stages), `installer/EmberAgent.iss`, `jlink-modules.txt`, `Iniciar Ember Agent.cmd`, `make-icon.ps1`, `build.env.example` — cloned from `ember-hub/`. `{commonstartup}` shortcut → `Ember Agent.exe --tray`; `%ProgramData%\EmberAgent\` kept across updates, uninstaller asks before delete; no firewall rule (outbound-only). CI `build-print-agent` job on `windows-latest` (fat jar + `jpackage` app-image smoke). README rewritten to the installer flow.
- **T7** — `printing-agent/VERIFY.md`: 10-line manual checklist on a clean Windows VM without Java (install, pair by code, printer dropdown, test page, real job, reboot autostart, update keeps credential, uninstall keep/remove data, no inbound firewall prompt) + results report.

Global constraints captured verbatim from the spec §6 decisions: startup shortcut not a service; one `printing-agent/` module; `/pair` does not rotate on the agent's side (design note explains the plaintext-on-code-row model); download link in the admin; `discovered_printers` = one overwritten JSON column; agent versions itself via `printing-agent/pom.xml`. Also recorded: `printing-agent` has no Maven wrapper → its verification command is `mvn -f printing-agent/pom.xml test`.

`PROGRESS.md`: `Current Active Task` now points at the plan with T1 next; new `### EMB-PRINT-AGENT` section lists T1–T7 as unchecked boxes; F-24 line notes T2 fixes it. File at 118 lines (< 180).

## 5. Why It Changed?
The spec's closing section calls for a plan in `docs/superpowers/plans/` before implementation. The user asked to continue from that point, put all tasks in `PROGRESS.md`, and execute them one at a time respecting `/clear`. Splitting into 7 reviewer-gateable tasks (backend / credential+status core / network clients / Swing UI / frontend admin / installer / manual verification) matches the spec's own phase list and this repo's 1-Task-1-Context lifecycle. Spec §4.1 (Hub detects local printers in-process) is left as a separate future plan and T3 builds `WindowsPrinterEnumerator` standalone so that plan can reuse it.

## 6. Verification
Doc-only task — no build command applies. `docs/superpowers/plans/2026-09-08-print-agent-installer.md` written; `PROGRESS.md` updated and under the 180-line cap; self-review section in the plan maps every spec section/decision to a task.

## 7. System Health
Unchanged from report 415 — no code touched. Backend `./mvnw test` 1199/1199; frontend `build` clean, `test:run` 118/118; `landing` `pnpm build` 26 pages, i18n parity 445/445. Backend prod on `v0.2.2`.
