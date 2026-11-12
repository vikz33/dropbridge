@echo off
setlocal
REM Membangun semuanya sekaligus: APK Android, lalu installer PC (APK terbaru ikut dibungkus).
REM Hasil di folder Rilis. Build masing-masing tetap bisa dari Android-App\build_apk.bat dan PC-App\build_exe.bat.
set "ROOT=%~dp0"
title Build DropBridge
cd /d "%ROOT%"

set "PCVER="
for /f "tokens=2 delims==" %%v in ('findstr /b /c:"VERSION" "PC-App\app\version.py"') do set "PCVER=%%v"
set "PCVER=%PCVER: =%"
set "PCVER=%PCVER:"=%"
set "ANVER="
for /f "tokens=2 delims==" %%v in ('findstr /c:"versionName" "Android-App\app\build.gradle.kts"') do set "ANVER=%%v"
set "ANVER=%ANVER: =%"
set "ANVER=%ANVER:"=%"

echo ==========================================================
echo   Build DropBridge: PC %PCVER%  ^|  Android %ANVER%
echo ==========================================================
echo.
echo ---- 1/2  Aplikasi Android ----
call "%ROOT%Android-App\build_apk.bat" nopause
if errorlevel 1 goto gagal
echo.
echo ---- 2/2  Installer PC ----
call "%ROOT%PC-App\build_exe.bat" nopause
if errorlevel 1 goto gagal

echo.
echo ==========================================================
echo   SELESAI. Di folder Rilis:
echo     DropBridge-Setup-%PCVER%.exe   (berisi APK %ANVER%)
echo     DropBridge-%ANVER%.apk
echo   Bagikan cukup file Setup-nya.
echo ==========================================================
start "" explorer "%ROOT%Rilis"
pause
exit /b 0

:gagal
echo.
echo Build berhenti karena ada yang gagal (lihat pesan di atas).
pause
exit /b 1
