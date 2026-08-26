# Report 237 — feat-hub-dashboard-launcher

## 1. Identification
- **Report number:** 237
- **Task ID:** feat-hub-dashboard-launcher
- **Predecessor task:** report 236 (HUB-01-11)

## 2. Objective
Replace Ember Hub's immediate auto-boot with a v1 Swing dashboard/launcher that the user distributes as the app's entry point: shows Postgres/server status and lets the owner start, stop, reopen the browser, and exit — instead of the app booting instantly with no controls the moment the `.exe`/jar runs. Explicitly scoped as v1: a branded web-based (Tauri) shell reusing the existing `frontend/` design is the agreed v2 follow-up, once this concept was validated live.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/EmberApplication.java`
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableDatabaseBootstrap.java`
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/HubBootstrapRunner.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java`
- `backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java`

## 4. What Changed?
- **`PortableDatabaseBootstrap.stop()`** (new, public): `pg_ctl stop -m fast`, no-op if `postmaster.pid` doesn't exist. Same file-redirect pattern as `startServer()` (report 234's fix) — no blocking pipe read.
- **`HubBootstrapRunner`** (new class): extracts the license-validate + `ensureRunning()` sequence that used to live inline in `EmberApplication.bootstrapHub()`, minus any `System.exit` call — throws `InvalidLicenseException`/`PortableDatabaseException` instead, so a failure can be shown in the dashboard and retried without killing the JVM. `startServices()` registers a JVM shutdown hook after a successful start so Postgres is always stopped cleanly on exit, however the process ends; `stopServices()` removes that hook and stops Postgres directly, without exiting the JVM, so `startServices()` can be called again later in the same process.
- **`HubProperties`** gained a `serverPort` field (`EMBER_HUB_SERVER_PORT`, default `8080`) — needed by the dashboard's "Abrir en navegador" button; previously only `application-hub.yml` read that env var.
- **`HubDashboard`** (new, plain Swing `JFrame`): two status labels (Postgres/Servidor) and four buttons (Iniciar/Detener/Abrir en navegador/Salir). "Iniciar" runs `HubBootstrapRunner.startServices()` + `SpringApplication.run()` on a background thread (keeps the Swing EDT responsive); registers the `ApplicationReadyEvent` listener via the `SpringApplication` instance API *before* calling `run()`, since a static-helper call would fire the event before a post-hoc listener could ever be attached. "Detener" closes the `ConfigurableApplicationContext` and stops Postgres without exiting. "Salir" and the window's close button both run the same stop sequence then `System.exit(0)`.
- **`EmberApplication.main()`**: the hub-profile branch now launches `HubDashboard.launch(args)` instead of auto-booting; the old `bootstrapHub()`/`failToStart()` methods are deleted (fully superseded by `HubBootstrapRunner`, no other callers existed). The non-hub boot path is unchanged.
- `HubTrayIcon` (HUB-01-10) is untouched — it still appears once `ApplicationReadyEvent` fires, as a supplementary quick-access icon; its own "Salir" is covered by the same shutdown hook.

## 5. Why It Changed?
The user's stated distribution model is a `.exe` non-technical restaurant owners double-click — instant auto-boot with zero controls and no way to stop the local database without killing the process (the exact gap the shutdown-hook fix closes: previously, no exit path ever ran `pg_ctl stop`, leaving portable Postgres processes orphaned after every session, confirmed repeatedly during HUB-01-11's manual testing). Swing was chosen deliberately over a JS/Tauri shell for this v1 pass: it's already in the JDK (`HubTrayIcon` already depends on the same `java.awt`/`java.desktop` module), needs zero new dependencies or toolchain, and was fast enough to validate the actual start/stop/status *logic* live today. A Tauri+webview shell reusing the real `frontend/` design (matching the product's branding) was discussed and deliberately deferred to a v2 task — `docs/superpowers/specs/ember_hub.md` §2.3 had already rejected Tauri for a *full backend rewrite*, which is a different, much larger proposition than a thin Rust shell that only supervises this same unchanged Java process; that distinction is worth re-reading before scoping v2.

## Verification
`cd backend && ./mvnw test` — 807/807 PASS (no new test methods; one existing `HubPropertiesTest` assertion extended for the new `serverPort` field — Swing/`ApplicationReadyEvent` wiring needs a real desktop + Postgres, same deferral pattern as `HubTrayIcon`/`PortableDatabaseBootstrap`'s real-binary paths).

**Live-verified by the user, cross-checked from the backend side after each step:**
- Iniciar → status flipped to "Postgres: en ejecución" / "Servidor: listo"; confirmed the real `EmberApplication` PID (not a stray process) owned port 8080 via `netstat`/`Get-CimInstance`.
- Abrir en navegador → opened `http://localhost:8080` correctly. **Known gap surfaced here, out of this task's scope:** the backend doesn't yet serve the compiled `frontend/` statics (spec §2.3's "el mismo Spring Boot sirve los estáticos de React"), so the opened page has nothing to show yet — bundling the built frontend into `backend/src/main/resources/static/` is a separate next task, not started.
- Detener → status returned to "detenido"; confirmed via `netstat`/`Get-Process` that port 8080 was freed and every `postgres.exe` process was gone.
- Iniciar again → restarted cleanly in the same running dashboard process, same live confirmation as the first start.
- Salir → window closed cleanly; confirmed via `netstat`/`Get-Process`/`Get-CimInstance` that nothing was left running — no orphaned Postgres processes, unlike every prior manual test session before the shutdown hook existed.

## Next steps (not started)
1. Bundle the compiled `frontend/` into the backend jar so "Abrir en navegador" has something to show.
2. v2: Tauri + webview shell reusing `frontend/`'s real design, supervising this same unchanged Java process — needs its own plan.
3. Packaging: `jpackage`/`jlink` embedded-JRE `.exe` installer (spec §2.3), still not started — this dashboard is the launcher UI that installer would wrap.
