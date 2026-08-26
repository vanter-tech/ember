# Report 235 — bugfix-hub-tray-icon-headless-mode

## 1. Identification
- **Report number:** 235
- **Task ID:** bugfix-hub-tray-icon-headless-mode (found during HUB-01-11's manual verification, spun out as its own atomic task)
- **Predecessor task:** report 234 (bugfix-hub-portable-db-pgctl-blocking-read)

## 2. Objective
Fix why HUB-01-10's system tray icon never appeared, even after reports 233 and 234 got Ember Hub fully booting (Postgres up, `ember` database created, Spring context started, port 8080 serving — all independently confirmed).

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/EmberApplication.java`

## 4. What Changed?
`EmberApplication.main()`'s existing `if (isHubProfile())` branch now calls `System.setProperty("java.awt.headless", "false")` before `bootstrapHub()` (and therefore before `SpringApplication.run()`). The non-hub boot path is untouched — it stays headless, which is correct for the normal server.

## 5. Why It Changed?
The app's own log confirmed the mechanism directly: `HubTrayIcon` logged its designed fallback — `"System tray no soportado en este sistema; Ember Hub sigue corriendo sin ícono."` — meaning `SystemTray.isSupported()` correctly returned `false`. `HubTrayIcon` itself was never buggy. The actual cause is a well-known Spring Boot default: `SpringApplication` forces `java.awt.headless=true` via `configureHeadlessProperty()`, called at the very start of `run()` — before the `Environment` (and therefore `application-hub.yml`) is even prepared. A profile-scoped `spring.main.headless=false` property can't win that race, since Spring Boot only sets the JVM system property from its *own* default (`true`) if nothing has already set it; by the time property binding would apply `spring.main.headless=false`, the system property is already locked in. Setting the raw `java.awt.headless` JVM property directly, before `SpringApplication.run()` is ever called, is the only reliable fix — it bypasses Spring's own headless machinery entirely and controls the actual property AWT/`SystemTray` reads.

**Verification:** `cd backend && ./mvnw test` — 807/807 PASS. Live-reboot with the fix showed no `"System tray no soportado"` warning in the logs, and **the user directly confirmed the tray icon is now visible** (red "Ember Hub" icon, previously invisible across two prior boot attempts even after the app was genuinely fully running).

## HUB-01-11 status
Steps 1–3 of the plan's manual verification are now fully confirmed end-to-end: real portable Postgres 16.6 boots, the `ember` database is created, the full Spring context starts, and the tray icon renders. Three real bugs were found and fixed along the way (reports 233, 234, 235) — exactly the outcome Task 11 exists for, per the plan's own note that this path has no CI coverage. Steps 4–6 (grace-period enforcement, the three boot-error paths, recording results in `PROGRESS.md`) are still pending.
