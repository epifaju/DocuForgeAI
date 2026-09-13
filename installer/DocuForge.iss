; DocuForge AI — Inno Setup 6 installer (French UI)
; Build: see installer/README.md (Prepare-Payload.ps1 → publish tray → ISCC)

#define MyAppName "DocuForge AI"
#define MyAppVersion "0.1.0"
#define MyAppPublisher "DocuForge"
; Launch-Tray.cmd starts DocuForge.Tray.exe when published, else PowerShell tray
#define MyAppExeName "Launch-Tray.cmd"

[Setup]
AppId={{A7C3E2F1-9B4D-4E6A-8C1F-2D5E7A9B0C3D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=dist
OutputBaseFilename=DocuForgeAI-Setup-{#MyAppVersion}
SetupIconFile=assets\setup.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\docuforge.ico
CloseApplications=force
VersionInfoVersion={#MyAppVersion}
LanguageDetectionMethod=locale
InfoBeforeFile=assets\welcome-fr.txt

[Languages]
Name: "french"; MessagesFile: "compiler:Languages\French.isl"

[Tasks]
Name: "desktopicon"; Description: "Créer un raccourci sur le Bureau"; GroupDescription: "Raccourcis :"; Flags: checkedonce
Name: "startupicon"; Description: "Démarrer DocuForge AI avec Windows"; GroupDescription: "Démarrage :"; Flags: unchecked

[Files]
Source: "dist\tray\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "Launch-Tray.cmd"; DestDir: "{app}"; Flags: ignoreversion
Source: "Open-DocuForge.cmd"; DestDir: "{app}"; Flags: ignoreversion
Source: "scripts\*"; DestDir: "{app}\scripts"; Flags: ignoreversion recursesubdirs
Source: "payload\compose\*"; DestDir: "{commonappdata}\DocuForgeAI\compose"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "payload\images\*"; DestDir: "{commonappdata}\DocuForgeAI\images"; Flags: ignoreversion recursesubdirs createallsubdirs skipifsourcedoesntexist
Source: "payload\prereqs\*"; DestDir: "{app}\prereqs"; Flags: ignoreversion recursesubdirs createallsubdirs skipifsourcedoesntexist
Source: "assets\docuforge.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\Ouvrir DocuForge AI (navigateur)"; Filename: "{app}\Open-DocuForge.cmd"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\Open-DocuForge.cmd"; IconFilename: "{app}\docuforge.ico"; Tasks: desktopicon
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startupicon

[Registry]
Root: HKLM; Subkey: "Software\DocuForgeAI"; ValueType: string; ValueName: "InstallRoot"; ValueData: "{app}"; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\DocuForgeAI"; ValueType: string; ValueName: "DataRoot"; ValueData: "{commonappdata}\DocuForgeAI"
Root: HKLM; Subkey: "Software\DocuForgeAI"; ValueType: string; ValueName: "UiPort"; ValueData: "{code:GetUiPort}"

[Run]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\scripts\New-DocuForgeEnv.ps1"" -CompanyName ""{code:GetCompanyName}"" -AdminEmail ""{code:GetAdminEmail}"" -AdminPassword ""{code:GetAdminPassword}"" -FrontendPort {code:GetUiPort} {code:GetAiSwitch}"; Flags: runhidden waituntilterminated; StatusMsg: "Configuration sécurisée…"
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\scripts\Install-Prereqs.ps1"" -InstallMissing -RancherInstallerPath ""{app}\prereqs\RancherDesktopSetup.exe"""; Flags: runhidden waituntilterminated; StatusMsg: "Vérification des prérequis (WSL2 / Rancher Desktop)…"
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\scripts\Start-Stack.ps1"" -WaitHealthy -TimeoutSeconds 420"; Flags: runhidden waituntilterminated; StatusMsg: "Démarrage de DocuForge AI (premier lancement)…"
Filename: "{app}\{#MyAppExeName}"; Description: "Lancer le contrôle DocuForge AI (barre système)"; Flags: nowait postinstall skipifsilent
Filename: "{app}\Open-DocuForge.cmd"; Description: "Ouvrir DocuForge AI dans le navigateur"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\scripts\Uninstall-Stack.ps1"" {code:GetUninstallVolumeFlag}"; Flags: runhidden waituntilterminated; RunOnceId: "DocuForgeUninstallStack"

[Code]
var
  ConfigPage: TInputQueryWizardPage;
  AiPage: TInputOptionWizardPage;
  CompanyNameValue, AdminEmailValue, AdminPasswordValue, UiPortValue: string;
  EnableAiValue: Boolean;
  RemoveDataOnUninstall: Boolean;
  KillResultCode: Integer;

function GetCompanyName(Param: string): string;
begin
  Result := CompanyNameValue;
end;

function GetAdminEmail(Param: string): string;
begin
  Result := AdminEmailValue;
end;

function GetAdminPassword(Param: string): string;
begin
  Result := AdminPasswordValue;
end;

function GetUiPort(Param: string): string;
begin
  if UiPortValue = '' then
    Result := '5174'
  else
    Result := UiPortValue;
end;

function GetAiSwitch(Param: string): string;
begin
  if EnableAiValue then
    Result := '-EnableAi'
  else
    Result := '';
end;

function GetUninstallVolumeFlag(Param: string): string;
begin
  if RemoveDataOnUninstall then
    Result := '-RemoveVolumes -RemoveImages'
  else
    Result := '-RemoveImages';
end;

procedure InitializeWizard;
begin
  CompanyNameValue := 'Mon organisation';
  AdminEmailValue := 'admin@example.local';
  AdminPasswordValue := '';
  UiPortValue := '5174';
  EnableAiValue := False;
  RemoveDataOnUninstall := False;

  ConfigPage := CreateInputQueryPage(wpWelcome,
    'Configuration',
    'Paramètres de votre organisation',
    'Ces informations créent le premier compte administrateur. Les secrets techniques (base de données, JWT) sont générés automatiquement et ne sont jamais affichés.');
  ConfigPage.Add('Nom de l''organisation :', False);
  ConfigPage.Add('E-mail administrateur :', False);
  ConfigPage.Add('Mot de passe administrateur :', True);
  ConfigPage.Add('Port d''accès (navigateur, défaut 5174) :', False);
  ConfigPage.Values[0] := CompanyNameValue;
  ConfigPage.Values[1] := AdminEmailValue;
  ConfigPage.Values[2] := '';
  ConfigPage.Values[3] := UiPortValue;

  AiPage := CreateInputOptionPage(ConfigPage.ID,
    'Assistance IA (optionnelle)',
    'Ollama — traitement local',
    'L''IA locale aide à reformuler ou générer du texte dans les formulaires. Elle demande plus de mémoire et n''est pas nécessaire pour produire des documents DOCX/PDF.',
    True, False);
  AiPage.Add('Activer l''assistance IA locale (Ollama)');
  AiPage.Values[0] := False;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  PortNum: Integer;
begin
  Result := True;
  if CurPageID = ConfigPage.ID then
  begin
    CompanyNameValue := Trim(ConfigPage.Values[0]);
    AdminEmailValue := Trim(ConfigPage.Values[1]);
    AdminPasswordValue := ConfigPage.Values[2];
    UiPortValue := Trim(ConfigPage.Values[3]);
    if CompanyNameValue = '' then
    begin
      MsgBox('Veuillez indiquer le nom de l''organisation.', mbError, MB_OK);
      Result := False;
      exit;
    end;
    if (Pos('@', AdminEmailValue) < 2) then
    begin
      MsgBox('Veuillez indiquer un e-mail administrateur valide.', mbError, MB_OK);
      Result := False;
      exit;
    end;
    if Length(AdminPasswordValue) < 8 then
    begin
      MsgBox('Le mot de passe administrateur doit contenir au moins 8 caractères.', mbError, MB_OK);
      Result := False;
      exit;
    end;
    PortNum := StrToIntDef(UiPortValue, -1);
    if (PortNum < 1) or (PortNum > 65535) then
    begin
      MsgBox('Port invalide. Utilisez une valeur entre 1 et 65535 (ex. 5174).', mbError, MB_OK);
      Result := False;
      exit;
    end;
  end;
  if CurPageID = AiPage.ID then
    EnableAiValue := AiPage.Values[0];
end;

function InitializeUninstall: Boolean;
begin
  Result := True;
  if MsgBox('Souhaitez-vous aussi supprimer les données DocuForge (base PostgreSQL et fichiers générés) ?' + #13#10 + #13#10 +
            'Choisissez Non pour conserver vos documents (recommandé).',
            mbConfirmation, MB_YESNO) = IDYES then
  begin
    if MsgBox('Confirmation : supprimer définitivement toutes les données DocuForge sur ce poste ?',
              mbConfirmation, MB_YESNO) = IDYES then
      RemoveDataOnUninstall := True
    else
      RemoveDataOnUninstall := False;
  end
  else
    RemoveDataOnUninstall := False;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
  begin
    Exec('taskkill.exe', '/IM DocuForge.Tray.exe /F', '', SW_HIDE, ewWaitUntilTerminated, KillResultCode);
    Exec('taskkill.exe', '/F /FI "WINDOWTITLE eq DocuForge*"', '', SW_HIDE, ewWaitUntilTerminated, KillResultCode);
  end;
end;
