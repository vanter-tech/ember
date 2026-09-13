# Ember Hub — manual installer verification

Run this on a **clean Windows 10/11 machine (or VM) with no JDK/JRE installed and no prior Ember
Hub install**. It verifies the packaged `EmberHubSetup-x.y.z.exe` (built by
`build-installer.ps1 -Stage installer`, needs `cargo tauri build` / the NSIS bundler), not the
raw `java -jar` path.

Fill in `[ ]` → `[x]` pass / `[F]` fail, one line per check. Record any deviation inline.
Bugs found here become their own follow-up tasks — do **not** fix them during verification.

| Field | Value |
| --- | --- |
| Tester | |
| Date | |
| Installer file | `EmberHubSetup-________.exe` |
| Hub version (`backend/pom.xml`, `-SNAPSHOT` stripped) | |
| Windows build | |
| `license.key` used | |

---

## Checklist

- [ ] **1. Clean install, no Java, `hub.env` generated on first launch.**
  `where java` before install finds nothing. Run `EmberHubSetup-x.y.z.exe`, accept the UAC
  prompt, finish the wizard. `%ProgramData%\EmberHub\{data\postgres,data\minio,logs,backups}`
  exist immediately after install (created by the NSIS post-install hook), but
  `%ProgramData%\EmberHub\hub.env` does **not** exist yet. Launch `Ember Hub.exe` from the Start
  Menu/desktop shortcut: window shows "Iniciando Ember Hub…", then `hub.env` appears (open it —
  confirm `JWT_SECRET`/`PLATFORM_JWT_SECRET` are each 64 distinct hex characters, not the same
  value twice), then the dashboard renders with 3 service cards auto-expanding one at a time with
  simulated logs (PostgreSQL → MinIO → Servidor order) before settling on "Detenido" or
  "En ejecución" depending on whether a `license.key` is already present.

- [ ] **2. No license yet — install one, services auto-start.**
  On first launch with no `license.key`, the license card shows "Sin licencia"; the 3 service
  cards show "Error" once startup is attempted (Postgres card carries the invalid-license
  message). Click **Seleccionar license.key…**, pick a valid file via the native dialog. License
  card flips to "OK", and — without clicking **Iniciar servicios** separately — PostgreSQL →
  MinIO → Servidor auto-expand in sequence with their simulated logs and settle on "En
  ejecución" (mirrors the old Swing dashboard's auto-start-on-license behavior).

- [ ] **3. Abrir en navegador.**
  Once "Servidor: En ejecución", **Abrir en navegador** is enabled; clicking it opens the SPA at
  `http://localhost:8080/app/` in the system's default browser.

- [ ] **4. Detener.**
  Click **Detener**. All 3 cards auto-expand with their short simulated shutdown log
  ("Cerrando conexiones…"-style) and settle back on "Detenido" within a few seconds. **Iniciar
  servicios** re-enables.

- [ ] **5. Heartbeat badge reflects real state.**
  With services running and a valid license, confirm the license card's "último contacto"
  timestamp advances over time (driven by the real `HeartbeatScheduler`, unchanged) without any
  manual refresh — the window is polling `/api/status` every ~1.5s.

- [ ] **6. Auto-start on log-on.**
  Reboot, log in, do nothing else. Within ~30s the window/tray shows the dashboard again with
  services already running (Tauri autostart + `hub.env` already present from run 1).

- [ ] **7. `hub.env` survives an update.**
  Install `x.y.(z+1)` over the existing install. `hub.env` is untouched (same `JWT_SECRET` —
  compare timestamp/hash before/after); services still start without re-entering the license.

- [ ] **8. Uninstall — keep data.**
  Uninstall via *Agregar o quitar programas* → the "¿Eliminar tambien los datos…?" prompt
  appears → choose **No**. `%ProgramData%\EmberHub\` remains (including `hub.env`,
  `license.key`, `data\`). Reinstall → launches straight into "En ejecución" with no re-licensing.

- [ ] **9. Uninstall — remove data.**
  Uninstall again, choose **Sí** at the prompt. `%ProgramData%\EmberHub\` is gone entirely.

- [ ] **10. Firewall rule present, no inbound prompt.**
  After install, `netsh advfirewall firewall show rule name="Ember Hub 8080"` shows the rule
  (private+domain only). At no point does Windows itself show a firewall "Allow access?" dialog
  during normal use — the rule is pre-authorized by the installer hook.

- [ ] **11. Sidecar crash recovery.**
  While "En ejecución", kill `Ember Hub.exe`'s underlying `java.exe` child directly in Task
  Manager (**not** the Tauri parent). Within ~2s the window shows "Ember Hub no pudo iniciar." +
  **Reintentar**. Click it → the sidecar respawns (`hub.env` already exists, so this is fast),
  window reaches "En ejecución" again without a reinstall.

- [ ] **12. Closing the window stops the services (unlike printer-agent's close-to-tray).**
  Click the window's close (X) button → the window and the `Ember Hub.exe`/`java.exe` child
  process both exit (this shell deliberately does **not** hide-to-tray for Hub, unlike
  printer-agent — see the comment in `src-tauri/src/main.rs`). Confirm no orphaned `java.exe`
  remains in Task Manager afterward.

- [ ] **13. Tray "Mostrar" reopens a still-running window; "Salir" stops everything.**
  While the window is visible and services are running, use the tray icon's **Mostrar** — no
  behavior change expected (window is already shown), just confirms the tray menu responds. Then
  use tray **Salir**: both the Tauri process and the `java.exe` child disappear from Task Manager.

---

## Result

**Go / No-Go:** ____

Blocking failures (if any), each to be filed as its own follow-up task:

1.
2.
