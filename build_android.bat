@echo off
setlocal
REM Membangun aplikasi Android DropBridge menjadi APK, lalu menaruhnya di folder shared
REM supaya bisa diunduh dari HP lewat DropBridge itu sendiri (folder "DropBridge-APK").
REM Log build: android\build_log.txt
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
set "PROJ=%~dp0android"
set "LOG=%PROJ%\build_log.txt"
set "APK=%PROJ%\app\build\outputs\apk\release\app-release.apk"
set "OUT=%~dp0shared\DropBridge-APK"

cd /d "%PROJ%"
if not exist local.properties (
  echo sdk.dir=C\:\\Users\\nudis\\AppData\\Local\\Android\\Sdk> local.properties
)
if exist "%APK%" del "%APK%"
echo Membangun aplikasi Android, bisa beberapa menit...
call gradlew.bat assembleRelease > "%LOG%" 2>&1
if exist "%APK%" (
  if not exist "%OUT%" mkdir "%OUT%"
  copy /y "%APK%" "%OUT%\DropBridge.apk" >nul
  echo.
  echo BERHASIL. APK ada di: %OUT%\DropBridge.apk
  echo Di HP: buka DropBridge di browser, masuk folder "DropBridge-APK", unduh DropBridge.apk, lalu pasang.
) else (
  echo.
  echo GAGAL. Kirim isi file ini ke Claude: %LOG%
)
pause
