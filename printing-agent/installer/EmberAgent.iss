; Ember Agent installer. Compiled by build-installer.ps1 via:
;   iscc /DAppVersion=<v> EmberAgent.iss
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif

[Setup]
AppName=Ember Agent
AppVersion={#AppVersion}
AppPublisher=Vanter
DefaultDirName={autopf}\Ember Agent
DefaultGroupName=Ember Agent
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\Ember Agent.exe
OutputDir=..\dist
OutputBaseFilename=EmberAgentSetup-{#AppVersion}
SetupIconFile=ember-agent.ico
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64compatible
WizardStyle=modern
CloseApplications=yes
CloseApplicationsFilter=Ember Agent.exe,*.cmd

[Files]
Source: "..\dist\app-image\Ember Agent\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Dirs]
Name: "{commonappdata}\EmberAgent"
Name: "{commonappdata}\EmberAgent\logs"

[Icons]
Name: "{group}\Ember Agent";         Filename: "{app}\Ember Agent.exe"; WorkingDir: "{app}"
Name: "{commondesktop}\Ember Agent";  Filename: "{app}\Ember Agent.exe"; WorkingDir: "{app}"
Name: "{commonstartup}\Ember Agent";  Filename: "{app}\Ember Agent.exe"; Parameters: "--tray"; WorkingDir: "{app}"

; No [Run] firewall rule - the agent only makes OUTBOUND WebSocket connections, it never listens.

[Code]
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
    if MsgBox('Eliminar tambien la credencial y los registros de Ember Agent en ' +
              ExpandConstant('{commonappdata}\EmberAgent') + '?  Elige "No" para conservarlos ' +
              '(asi no habra que volver a emparejar si reinstalas).',
              mbConfirmation, MB_YESNO or MB_DEFBUTTON2) = IDYES then
      DelTree(ExpandConstant('{commonappdata}\EmberAgent'), True, True, True);
end;
