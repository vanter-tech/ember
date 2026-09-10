# Ember Agent — manual installer verification

Run this on a **clean Windows 10/11 machine (or VM) with no JDK/JRE installed**. It verifies
the packaged `EmberAgentSetup-x.y.z.exe` (built by `build-installer.ps1 -Stage installer`,
needs Inno Setup 6 / `iscc`), not the raw `java -jar` path.

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
  prompt, finish the wizard. Result: `%ProgramFiles%\Ember Agent\Ember Agent.exe` +
  `runtime\bin\java.exe` + `app\printing-agent.jar` all present; `Ember Agent.exe` launches;
  dashboard shows **"Sin emparejar"**; tray icon present. `where java` still finds nothing
  (runtime is embedded). Startup shortcut `%ProgramData%\Microsoft\Windows\Start Menu\Programs\Startup\Ember Agent`
  (or `shell:common startup`) exists and carries `--tray`.

- [ ] **2. Pair by code.**
  In the admin (**Configuración → Impresión**) create an agent (e.g. `Caja 1`) → copy the
  pairing code (valid ~15 min). In Ember Agent paste it → **Emparejar**. Dashboard flips
  **Sin emparejar → Conectando… → Conectado**. `%ProgramData%\EmberAgent\credential.bin`
  exists and opens as binary, **not** readable text (DPAPI machine-scope). No
  `credential.json` was written.

- [ ] **3. Printer dropdown.**
  In the admin's **Agregar impresora**, connection type **Cola de impresión de Windows** →
  the field is a `<select>` listing the PC's real queues; the list matches `Get-Printer`
  output exactly. Pick an inkjet queue (e.g. `EPSON L3210 Series`) → **Modo de impresión**
  auto-selects **Por driver de Windows**.

- [ ] **4. Test page.**
  Ember Agent dashboard → select a reported queue → **Imprimir página de prueba** → paper
  comes out of that printer. The "Actividad" table shows the test job as `OK`.

- [ ] **5. Real job.**
  From the app, trigger a kitchen ticket and a bill receipt routed to a printer registered
  on this agent → both print. Dashboard "Actividad" shows each job as `OK` with the right
  role (`Cocina` / `Recibo`) and queue.

- [ ] **6. Auto-start on log-on.**
  Reboot the PC, log in, do nothing else. Within ~30 s Ember Agent is running in the tray
  and the dashboard reaches **Conectado** again — **without** any re-pair prompt.

- [ ] **7. Credential survives an update.**
  Build/obtain `x.y.(z+1)`, run its `EmberAgentSetup` over the existing install. No re-pair
  prompt; agent reconnects. `%ProgramFiles%\Ember Agent\` files are refreshed (new version
  string) while `%ProgramData%\EmberAgent\credential.bin` is byte-identical (check timestamp
  / hash before and after).

- [ ] **8. Uninstall — keep data.**
  Uninstall via *Agregar o quitar programas*. At the "¿Eliminar también la credencial y los
  registros…?" prompt answer **No**. Result: `%ProgramFiles%\Ember Agent\` removed;
  `%ProgramData%\EmberAgent\` (with `credential.bin`) remains. Reinstall → connects with
  **no** pairing step.

- [ ] **9. Uninstall — remove data.**
  Uninstall again, this time answer **Yes** to the same prompt. Result:
  `%ProgramData%\EmberAgent\` is gone entirely.

- [ ] **10. No inbound firewall prompt.**
  At no point during install, first run, pairing, printing, or reboot does Windows show a
  "Windows Defender Firewall has blocked some features… Allow access?" dialog. The agent is
  outbound-only (WebSocket to the backend); the installer adds no `[Run]` firewall rule.

---

## Result

**Go / No-Go:** ____

Blocking failures (if any), each to be filed as its own follow-up task:

1.
2.
