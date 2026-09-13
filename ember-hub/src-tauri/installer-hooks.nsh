; Ember Hub NSIS installer hooks (replaces EmberHub.iss's [Dirs]/[Run]/[UninstallRun]/uninstall
; prompt). See docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md Task 5.

!macro NSIS_HOOK_POSTINSTALL
  CreateDirectory "$COMMONAPPDATA\EmberHub"
  CreateDirectory "$COMMONAPPDATA\EmberHub\data\postgres"
  CreateDirectory "$COMMONAPPDATA\EmberHub\data\minio"
  CreateDirectory "$COMMONAPPDATA\EmberHub\logs"
  CreateDirectory "$COMMONAPPDATA\EmberHub\backups"

  ; Inbound firewall rule for LAN terminals - private + domain only, never public.
  ; delete-then-add so a re-install does not stack duplicate rules.
  nsExec::ExecToLog 'cmd.exe /c netsh advfirewall firewall delete rule name="Ember Hub 8080" >nul 2>&1 & netsh advfirewall firewall add rule name="Ember Hub 8080" dir=in action=allow protocol=TCP localport=8080 profile=private,domain'
!macroend

!macro NSIS_HOOK_POSTUNINSTALL
  nsExec::ExecToLog 'netsh advfirewall firewall delete rule name="Ember Hub 8080"'

  MessageBox MB_YESNO|MB_DEFBUTTON2 "Eliminar tambien los datos de Ember Hub (base de datos, licencia, respaldos) en $COMMONAPPDATA\EmberHub?  Elige 'No' para conservarlos." IDNO +2
  RMDir /r "$COMMONAPPDATA\EmberHub"
!macroend
