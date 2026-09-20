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

- [ ] **14. Backup modal + "Esta máquina".**
  Dashboard → **Respaldos** → **Respaldar ahora**. A modal offers **USB o disco externo
  (Recomendado)** and **Esta máquina**. Pick **Esta máquina**: `ember-backup-<fecha>.zip` appears in
  `%ProgramData%\EmberHub\backups\` and in the card's list, with the "copia el archivo a una USB" notice.
  Open the zip: it has `manifest.json` (its `appVersion` is a real version, **not** `unknown`),
  `postgres.dump` and `minio/`.

- [ ] **15. Backup to a USB, and a missing USB.**
  **Respaldar ahora → USB o disco externo** opens the native folder picker; the zip lands on that
  drive. Unplug it and repeat: the card shows a red banner "No se pudo escribir en la carpeta de
  respaldo… Si es una USB, revisa que esté conectada." and nothing crashes.

- [ ] **16. Settings + images round trip via an uploaded file.**
  In the app change a restaurant Setting (e.g. tax rate) and upload a branding logo → **Respaldar
  ahora**. Change the setting again and replace the logo. Copy the zip to the Desktop →
  **Restaurar desde archivo…**, pick it, confirm. The Hub restarts by itself; the *original* setting
  and logo are back, and `backups\` now also holds an `…-pre-restore.zip`.

- [ ] **17. Recover from a corrupted database.**
  Stop the Hub. Overwrite `%ProgramData%\EmberHub\data\postgres\PG_VERSION` with `99`. Start: the
  PostgreSQL card shows an error. **Restaurar desde archivo…** with a good zip → "No se pudo guardar
  la copia previa" → **Restaurar sin copia previa**. The Hub comes up with the backup's data, and
  `%ProgramData%\EmberHub\data\postgres.corrupt-<fecha>\` exists next to the new `postgres\`.

- [ ] **18. Newer-version guard.**
  Edit a copy of a backup's `manifest.json` so `appVersion` is `99.0.0`, re-zip, pick it: the card
  shows "Este respaldo es de una versión más reciente de Ember (99.0.0)…", no confirmation opens, no
  data changes.

- [ ] **19. Automatic backup.**
  On a Hub with no backups leave it running ~20 min: a backup appears in the automatic folder and
  "Próximo automático" shows ~24 h later. **Cambiar carpeta automática…** points future automatic
  backups at a USB.

- [ ] **20. No license → backup/restore disabled, and nothing hangs.**
  With no `license.key`: **Respaldar ahora**, **Restaurar desde archivo…** and every row's
  **Restaurar** are disabled, with the hint "Instala la licencia (license.key)…". Do this on a PC
  that also has another (password-protected) Postgres on port 5432 — nothing may hang or need a
  restart. With a license but **Detener**ed services only **Respaldar ahora** is disabled ("Inicia
  los servicios…").

- [ ] **21. Progress bar.**
  **Respaldar ahora → Esta máquina** on a Hub with some images: a bar appears with the phase
  ("Exportando la base de datos…" animated, then "Comprimiendo archivos… N%" filling) and
  disappears when done. **Restaurar** shows the phases (copia previa, deteniendo servicios,
  restaurando base de datos, restaurando las imágenes N%, iniciando servicios).

- [ ] **22. Small laptop + wide window.**
  On a 1366×768 laptop (or 1080p at 125–150% scaling) the window opens fully inside the screen (no
  part hanging off the bottom) and can be shrunk to ~420×360 with the content scrolling. Widening
  it past ~768px lays the cards out in two columns (Respaldos spans the full width).

---

## Result

**Go / No-Go:** ____

Blocking failures (if any), each to be filed as its own follow-up task:

1.
2.
