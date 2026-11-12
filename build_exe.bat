@echo off
setlocal
REM Membangun DropBridge PC menjadi installer: dist\DropBridge-Setup-<versi>.exe
REM Versi diambil dari app\version.py. APK Android terbaru dari ..\dropbridge-android\dist ikut dibungkus.
REM Perlu: start.bat pernah dijalankan (membuat .venv) dan Inno Setup 6 (winget install JRSoftware.InnoSetup).
REM Log build: build_exe_log.txt
cd /d "%~dp0"
set "LOG=%~dp0build_exe_log.txt"

if not exist ".venv\Scripts\python.exe" (
  echo Jalankan start.bat sekali dulu supaya lingkungan Python siap, lalu ulangi.
  pause
  exit /b 1
)

set "VER="
for /f "tokens=2 delims==" %%v in ('findstr /b /c:"VERSION" app\version.py') do set "VER=%%v"
set "VER=%VER: =%"
set "VER=%VER:"=%"
if "%VER%"=="" set "VER=0.0.0"
echo Membangun DropBridge %VER%, bisa beberapa menit...

echo [1/4] Memasang alat build...
".venv\Scripts\python.exe" -m pip install -q -r requirements.txt pyinstaller > "%LOG%" 2>&1
if errorlevel 1 goto gagal

echo [2/4] Mencari APK Android...
set "DB_APK="
for /f "delims=" %%f in ('dir /b /o:d "..\dropbridge-android\dist\DropBridge-*.apk" 2^>nul') do set "DB_APK=%~dp0..\dropbridge-android\dist\%%f"
if defined DB_APK (
  echo       Ikut dibungkus: %DB_APK%
) else (
  echo       Tidak ada APK di ..\dropbridge-android\dist. Installer dibuat tanpa APK.
)

echo [3/4] Membuat DropBridge.exe...
".venv\Scripts\python.exe" tools\make_icon.py >> "%LOG%" 2>&1
".venv\Scripts\python.exe" -m PyInstaller --noconfirm --clean DropBridge.spec >> "%LOG%" 2>&1
if not exist "dist\DropBridge\DropBridge.exe" goto gagal

echo [4/4] Membuat installer...
set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" (
  echo.
  echo Inno Setup 6 belum terpasang. Pasang sekali dengan perintah:
  echo     winget install JRSoftware.InnoSetup
  echo lalu jalankan build_exe.bat lagi.
  echo.
  echo Programnya sudah jadi dan bisa dicoba tanpa installer: dist\DropBridge\DropBridge.exe
  pause
  exit /b 1
)
set "DB_VERSION=%VER%"
"%ISCC%" /Qp installer\DropBridge.iss >> "%LOG%" 2>&1
if not exist "dist\DropBridge-Setup-%VER%.exe" goto gagal

echo.
echo BERHASIL. Installer versi %VER%: dist\DropBridge-Setup-%VER%.exe
pause
exit /b 0

:gagal
echo.
echo GAGAL. Kirim isi file ini ke Claude: %LOG%
pause
exit /b 1
