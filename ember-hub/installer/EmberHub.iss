; Ember Hub installer. Compiled by build-installer.ps1 via:
;   iscc /DAppVersion=<v> /DServerPort=<p> /DEmberHubActivationUrl=<u> /DEmberHubHeartbeatUrl=<u> EmberHub.iss
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef ServerPort
  #define ServerPort "8080"
#endif
#ifndef EmberHubActivationUrl
  #define EmberHubActivationUrl "https://api.vanter.com/hub-activations"
#endif
#ifndef EmberHubHeartbeatUrl
  #define EmberHubHeartbeatUrl "https://api.vanter.com/hub-heartbeat"
#endif

[Setup]
AppName=Ember Hub
AppVersion={#AppVersion}
AppPublisher=Vanter
DefaultDirName={autopf}\Ember Hub
DefaultGroupName=Ember Hub
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\Ember Hub.exe
OutputDir=..\dist
OutputBaseFilename=EmberHubSetup-{#AppVersion}
SetupIconFile=ember-hub.ico
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64
WizardStyle=modern
CloseApplications=yes
CloseApplicationsFilter=Ember Hub.exe,*.cmd

[Files]
Source: "..\dist\app-image\Ember Hub\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Dirs]
Name: "{commonappdata}\EmberHub"
Name: "{commonappdata}\EmberHub\data\postgres"
Name: "{commonappdata}\EmberHub\data\minio"
Name: "{commonappdata}\EmberHub\logs"
Name: "{commonappdata}\EmberHub\backups"

[Icons]
Name: "{group}\Ember Hub";        Filename: "{app}\Iniciar Ember Hub.cmd"; IconFilename: "{app}\Ember Hub.exe"; WorkingDir: "{app}"
Name: "{commondesktop}\Ember Hub"; Filename: "{app}\Iniciar Ember Hub.cmd"; IconFilename: "{app}\Ember Hub.exe"; WorkingDir: "{app}"
Name: "{commonstartup}\Ember Hub"; Filename: "{app}\Iniciar Ember Hub.cmd"; IconFilename: "{app}\Ember Hub.exe"; WorkingDir: "{app}"

[Run]
; Inbound firewall rule for LAN terminals - private + domain only, never public.
; delete-then-add so a re-install does not stack duplicate rules; cmd's exit code is the add's.
Filename: "{sys}\cmd.exe"; \
  Parameters: "/c netsh advfirewall firewall delete rule name=""Ember Hub {#ServerPort}"" >nul 2>&1 & netsh advfirewall firewall add rule name=""Ember Hub {#ServerPort}"" dir=in action=allow protocol=TCP localport={#ServerPort} profile=private,domain"; \
  Flags: runhidden

[UninstallRun]
Filename: "{sys}\netsh.exe"; \
  Parameters: "advfirewall firewall delete rule name=""Ember Hub {#ServerPort}"""; \
  Flags: runhidden; RunOnceId: "DelFwRule"

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  EnvPath, Lines: string;
begin
  if CurStep = ssPostInstall then
  begin
    EnvPath := ExpandConstant('{commonappdata}\EmberHub\hub.env');
    if not FileExists(EnvPath) then
    begin
      Lines :=
        '# Generado por el instalador de Ember Hub. No editar salvo el puerto.' + #13#10 +
        'EMBER_HUB_DATA_DIR=' + ExpandConstant('{commonappdata}\EmberHub\data\postgres') + #13#10 +
        'EMBER_HUB_MINIO_DATA_DIR=' + ExpandConstant('{commonappdata}\EmberHub\data\minio') + #13#10 +
        'EMBER_HUB_POSTGRES_BIN_DIR=' + ExpandConstant('{app}\pgsql\bin') + #13#10 +
        'EMBER_HUB_MINIO_BIN_DIR=' + ExpandConstant('{app}\minio') + #13#10 +
        'EMBER_HUB_LICENSE_FILE=' + ExpandConstant('{commonappdata}\EmberHub\license.key') + #13#10 +
        'EMBER_HUB_PUBLIC_KEY_FILE=' + ExpandConstant('{app}\hub-public-key.der') + #13#10 +
        'EMBER_HUB_STATE_FILE=' + ExpandConstant('{commonappdata}\EmberHub\hub-state.json') + #13#10 +
        'EMBER_HUB_POSTGRES_PORT=5432' + #13#10 +
        'EMBER_HUB_MINIO_PORT=9000' + #13#10 +
        'EMBER_HUB_SERVER_PORT={#ServerPort}' + #13#10 +
        'EMBER_HUB_ACTIVATION_URL={#EmberHubActivationUrl}' + #13#10 +
        'EMBER_HUB_HEARTBEAT_URL={#EmberHubHeartbeatUrl}' + #13#10;
      SaveStringToFile(EnvPath, Lines, False);
    end;
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
    if MsgBox('Eliminar tambien los datos de Ember Hub (base de datos, licencia, respaldos) en ' +
              ExpandConstant('{commonappdata}\EmberHub') + '?  Elige "No" para conservarlos.',
              mbConfirmation, MB_YESNO or MB_DEFBUTTON2) = IDYES then
      DelTree(ExpandConstant('{commonappdata}\EmberHub'), True, True, True);
end;
