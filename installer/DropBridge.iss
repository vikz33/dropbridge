; Installer DropBridge (Inno Setup 6). Dibangun oleh build_exe.bat, versi dari app\version.py.
#define MyVer GetEnv("DB_VERSION")
#if MyVer == ""
  #define MyVer "0.0.0"
#endif

[Setup]
AppId={{6F2B8C1E-4D7A-4B3F-9E21-DB0B1D6E2026}
AppName=DropBridge
AppVersion={#MyVer}
AppVerName=DropBridge {#MyVer}
AppPublisher=DropBridge
VersionInfoVersion={#MyVer}
DefaultDirName={autopf}\DropBridge
DefaultGroupName=DropBridge
DisableProgramGroupPage=yes
OutputDir=..\dist
OutputBaseFilename=DropBridge-Setup-{#MyVer}
SetupIconFile=..\build\dropbridge.ico
UninstallDisplayIcon={app}\DropBridge.exe
UninstallDisplayName=DropBridge {#MyVer}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=yes

[Messages]
FinishedLabel=DropBridge {#MyVer} sudah terpasang.%n%nPengaturan lama dari C:\DropBridge (PIN, perangkat, folder penyimpanan) diimpor otomatis, jadi HP tidak perlu dihubungkan ulang.%n%nKalau DropBridge versi start.bat masih jalan, hentikan dulu lewat ikon tray > Berhenti.

[Tasks]
Name: "autostart"; Description: "Jalankan otomatis saat Windows menyala"; GroupDescription: "Pilihan:"
Name: "firewall"; Description: "Izinkan di Windows Firewall (jaringan Private dan Public, perlu untuk HP dan Tailscale)"; GroupDescription: "Pilihan:"
Name: "desktopicon"; Description: "Buat ikon di Desktop"; GroupDescription: "Pilihan:"; Flags: unchecked

[Files]
Source: "..\dist\DropBridge\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; versi lama di folder yang sama: buang berkas program lama (pengaturan di AppData tidak tersentuh)
Type: filesandordirs; Name: "{app}\_internal"

[Icons]
Name: "{group}\DropBridge"; Filename: "{app}\DropBridge.exe"
Name: "{autodesktop}\DropBridge"; Filename: "{app}\DropBridge.exe"; Tasks: desktopicon

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "DropBridge"; ValueData: """{app}\DropBridge.exe"" --tray"; Tasks: autostart; Flags: uninsdeletevalue

[Run]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""DropBridge"""; Flags: runhidden; Tasks: firewall
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""DropBridge"" dir=in action=allow program=""{app}\DropBridge.exe"" enable=yes profile=private,public"; Flags: runhidden; Tasks: firewall
Filename: "{app}\DropBridge.exe"; Description: "Jalankan DropBridge sekarang"; Flags: nowait postinstall skipifsilent runasoriginaluser

[UninstallRun]
Filename: "{sys}\taskkill.exe"; Parameters: "/f /im DropBridge.exe"; Flags: runhidden; RunOnceId: "StopApp"
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""DropBridge"""; Flags: runhidden; RunOnceId: "DelFirewall"

[Code]
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  R: Integer;
begin
  { DropBridge yang sedang jalan dihentikan supaya berkasnya bisa diganti }
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/f /im DropBridge.exe', '', SW_HIDE, ewWaitUntilTerminated, R);
  Result := '';
end;
