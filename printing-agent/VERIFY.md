# Ember Agent — manual installer verification

Run this on a **clean Windows 10/11 machine (or VM) with no JDK/JRE installed**. It verifies
the packaged `EmberAgentSetup-x.y.z.exe` (built by `build-installer.ps1 -Stage installer`,
needs `cargo tauri build` / the NSIS bundler), not the raw `java -jar` path.

Fill in `[ ]` → `[x]` pass / `[F]` fail, one line per check. Record any deviation inline.
Bugs found here become their own follow-up tasks — do **not** fix them during verification.

| Field | Value |
| --- | --- |
| Tester | |
| Date | |
| Installer file | `EmberAgentSetup-________.exe` |
| Agent version (`printing-agent/pom.xml`, `-SNAPSHOT` stripped) | |
| Windows build | |
| Backend / tenant used | |

---

## Checklist

- [ ] **1. Clean install, no Java.**
  `where java` before install finds nothing. Run `EmberAgentSetup-x.y.z.exe`, accept the UAC
  prompt, finish the wizard. Result: `%ProgramFiles%\Ember Agent\Ember Agent.exe` (Tauri shell)
  launches; window shows "Iniciando Ember Agent…" then **"Sin emparejar"**; tray icon present.
  Task Manager shows a `java.exe` (or `Ember Agent.exe` from the bundled app-image) child process
  under the Tauri process. `where java` still finds nothing (runtime is embedded inside the
  bundled resources). Startup entry exists (Tauri autostart) with no extra CLI flags.

- [ ] **2. Pair by code.**
  In the admin (**Configuración → Impresión**) create an agent (e.g. `Caja 1`) → copy the
  pairing code (valid ~15 min). In the window paste it → **Emparejar**. "Sin emparejar" →
  "Conectando…" → "Conectado". `%ProgramData%\EmberAgent\credential.bin` exists and opens as
  binary, **not** readable text (DPAPI machine-scope, unchanged). No `credential.json` was written.

- [ ] **3. Printer dropdown.**
  In the admin's **Agregar impresora**, the field lists the PC's real queues; matches
  `Get-Printer` output exactly (unchanged `WindowsPrinterEnumerator`).

- [ ] **4. Test page.**
  Window → select a reported queue → **Imprimir página de prueba** → paper comes out of that
  printer, success message shown. The "Actividad" table does **not** gain a row for it (matches
  today's behavior — manual test prints aren't recorded jobs).

- [ ] **5. Real job.**
  Trigger a kitchen ticket and a bill receipt from the app → both print. "Actividad" shows each
  job as `OK` with the right role/queue, refreshed within ~1.5s of completion (polling).

- [ ] **6. Auto-start on log-on.**
  Reboot, log in, do nothing else. Within ~30s the window/tray reaches "Conectado" again —
  **without** any re-pair prompt.

- [ ] **7. Credential survives an update.**
  Install `x.y.(z+1)` over the existing install. No re-pair prompt; agent reconnects.
  `credential.bin` is byte-identical before/after (check timestamp/hash).

- [ ] **8. Uninstall — keep data.**
  Uninstall via *Agregar o quitar programas*, keep data when prompted (or the NSIS-equivalent
  prompt if the wording changed — note the exact text seen). `%ProgramData%\EmberAgent\` remains.
  Reinstall → connects with **no** pairing step.

- [ ] **9. Uninstall — remove data.**
  Uninstall again, choose to remove data. `%ProgramData%\EmberAgent\` is gone entirely.

- [ ] **10. No inbound firewall prompt.**
  At no point does Windows show a firewall "Allow access?" dialog — `LocalControlServer` only
  binds `127.0.0.1`, which Windows Firewall never filters regardless of inbound rules.

- [ ] **11. Sidecar crash recovery (new).**
  While "Conectado", kill the `java.exe`/`Ember Agent.exe` child process directly in Task Manager
  (**not** the Tauri parent). Within ~2s the window shows "El agente no pudo iniciar." +
  **Reintentar**. Click it → the sidecar respawns, window reaches "Conectado" again without a
  reinstall or a Tauri restart.

- [ ] **12. Close-to-tray, no orphaned process.**
  Click the window's close (X) button → window hides, tray icon remains, `java.exe` child process
  **still running** (mirrors old Swing "minimize to tray, never exit"). Then quit via the tray's
  **Salir** → both the Tauri process and the `java.exe` child process disappear from Task
  Manager — no orphan left behind.

- [ ] **13. Force-kill the shell, no orphan.**
  With the agent "Conectado", kill the Tauri parent process itself (not via **Salir**) in Task
  Manager. Confirm the `java.exe` child either exits with it or is cleanly killable afterward —
  record which happened; if the child survives the parent's forced kill, file it as a follow-up
  (the spec §7 orphan-process risk was flagged as accepted-but-unverified, not fully closed by
  Task 4's normal-exit kill path).

- [ ] **14. Ticket logo (new, needs a real thermal printer).**
  In the web app: Ajustes → Ticket → **Subir logo** (PNG/JPG ≤ 2 MB) on a plan with branding, with the
  paper width matching the printer. Print a bill receipt from a table. The logo prints **centered
  above the ticket text**, in black/white dots, and the text below is **left-aligned as usual**
  (not centered). Then: (a) switch paper width 58 ↔ 80 mm and reprint — the logo re-scales with no
  re-upload; (b) **Quitar logo** and reprint — text only; (c) with the backend unreachable but the
  logo printed once before, the ticket still prints (cached copy); (d) a kitchen ticket
  carries the same logo above its text; (e) on a `DRIVER`-mode inkjet queue the logo is drawn at the top of the page.
  Record the printer model — dot density other than ~203 dpi may need the width constants in the
  backend's `TicketLogoProcessor` adjusted.

---

## Result

**Go / No-Go:** ____

Blocking failures (if any), each to be filed as its own follow-up task:

1.
2.
