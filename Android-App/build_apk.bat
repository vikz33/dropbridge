@echo off
setlocal
REM Membangun DropBridge Android menjadi APK.
REM Hasil: ..\Rilis\DropBridge-<versi>.apk  (versi diambil dari versionName di app\build.gradle.kts)
REM PC-App\build_exe.bat mengambil APK terbaru dari folder Rilis untuk dibungkus ke installer.
REM Log build: build_log.txt
if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
set "PROJ=%~dp0"
set "LOG=%PROJ%build_log.txt"
set "APK=%PROJ%app\build\outputs\apk\release\app-release.apk"
set "OUT=%PROJ%..\Rilis"

cd /d "%PROJ%"
set "VER="
for /f "tokens=2 delims==" %%v in ('findstr /c:"versionName" app\build.gradle.kts') do set "VER=%%v"
set "VER=%VER: =%"
set "VER=%VER:"=%"
if "%VER%"=="" set "VER=dev"
set "NAME=DropBridge-%VER%.apk"

if not exist local.properties (
  echo sdk.dir=%LOCALAPPDATA:\=\\%\\Android\\Sdk> local.properties
)
if exist "%APK%" del "%APK%"
echo Membangun DropBridge Android versi %VER%, bisa beberapa menit...
call gradlew.bat assembleRelease > "%LOG%" 2>&1
if exist "%APK%" (
  if not exist "%OUT%" mkdir "%OUT%"
  copy /y "%APK%" "%OUT%\%NAME%" >nul
  echo.
  echo BERHASIL. APK versi %VER% ada di folder Rilis: %NAME%
) else (
  echo.
  echo GAGAL. Kirim isi file ini ke Claude: %LOG%
)
pause
