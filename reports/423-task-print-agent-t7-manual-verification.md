# Report 423 — EMB-PRINT-AGENT T7: manual installer verification checklist

## 1. Identification
- **Report number:** 423
- **Task ID:** EMB-PRINT-AGENT T7 (manual verification on a clean Windows PC without Java)
- **Predecessor task:** T6 — installer (`build-installer.ps1` + `EmberAgent.iss` + CI `build-print-agent`), report 422
- **Plan:** `docs/superpowers/plans/2026-09-08-print-agent-installer.md` (Task 7)

## 2. Objective
Produce `printing-agent/VERIFY.md` — a 10-point pass/fail checklist for validating the
packaged `EmberAgentSetup-x.y.z.exe` on a clean Windows machine — then execute it and record
a go/no-go.

## 3. Modified Files
- `printing-agent/VERIFY.md` (created)
- `reports/423-task-print-agent-t7-manual-verification.md` (created)
- `PROGRESS.md` (T7 status note, health line unchanged)

## 4. What Changed?
- **`printing-agent/VERIFY.md`** — new. Numbered `[ ]` checklist with a tester/version header
  table and a Go/No-Go footer. The 10 checks mirror plan Task 7 Step 1 and are worded against
  the actual installer artefacts:
  1. Clean install, no Java — embedded `runtime\bin\java.exe`, `Ember Agent.exe` launches,
     dashboard "Sin emparejar", tray icon, `{commonstartup}` shortcut carries `--tray`.
  2. Pair by code — `Sin emparejar → Conectando… → Conectado`, `%ProgramData%\EmberAgent\credential.bin`
     present and binary (DPAPI machine scope), no `credential.json`.
  3. Printer dropdown — `<select>` of real `Get-Printer` queues in **Agregar impresora**;
     inkjet → **Por driver de Windows** auto-selected.
  4. Test page — dashboard **Imprimir página de prueba** prints, logged `OK`.
  5. Real job — kitchen ticket + bill receipt print, "Actividad" shows `OK` with role/queue.
  6. Auto-start on log-on — running in tray within ~30 s after reboot, reconnects, no re-pair.
  7. Update keeps credential — install `z+1` over the top, no re-pair, `credential.bin`
     byte-identical, `%ProgramFiles%\Ember Agent\` refreshed.
  8. Uninstall — answer **No** to the delete-data prompt → `%ProgramData%\EmberAgent\`
     remains, reinstall connects with no pairing.
  9. Uninstall — answer **Yes** → `%ProgramData%\EmberAgent\` gone.
  10. No inbound Windows Firewall prompt at any point (agent is outbound-only; no `[Run]`
      rule in `EmberAgent.iss`).
- **`PROGRESS.md`** — T7 line annotated: checklist authored (`VERIFY.md`); execution +
  go/no-go still pending an `EmberAgentSetup-x.y.z.exe` build and a clean Windows PC.

## 4b. Verification Execution — NOT RUN (blocked)
Plan Task 7 Step 2 (run the checklist) and Step 3's go/no-go were **not** performed:
- **`iscc` (Inno Setup 6) is not installed** on this machine and is not on the CI runner
  (`build-print-agent` stops at the jpackage app-image; the Inno `.exe` stage is explicitly
  manual — report 422, PROGRESS.md health line). Without it there is no
  `EmberAgentSetup-x.y.z.exe` to install.
- No clean Windows 10/11 VM without a JDK is available in this environment.

Both are owner/ops actions. When the `.exe` exists and a clean PC is available, run
`printing-agent/VERIFY.md` top to bottom, fill it in, and record the go/no-go there and in a
follow-up PROGRESS.md update. This is the last item of EMB-PRINT-AGENT; the code deliverables
(T1–T6) are complete and their own suites are green.

No frontend or backend source was touched, so no `pnpm run build` / `./mvnw test` run applies
to this task. Agent suite unchanged from T6: `mvn -f printing-agent/pom.xml test` **41/41**.

## 5. Why It Changed?
T7 exists so the packaged installer is proven on the same class of machine a restaurant would
use — no developer toolchain, embedded runtime, DPAPI credential, auto-start, clean
uninstall. Writing `VERIFY.md` now (independent of the `.exe` build) means the moment the
installer is compiled, verification is a mechanical pass rather than a design exercise, and
the pass/fail record lives in the repo next to the thing it validates.
