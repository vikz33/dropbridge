@echo off
setlocal
REM Mengunggah hasil build Windows ke GitHub Releases, supaya DropBridge di laptop lain bisa memperbarui diri.
REM Urutan tiap rilis:  1) git push   2) build_semua.bat   3) file ini (rilis_github.bat)
REM Perlu sekali: winget install GitHub.cli   lalu   gh auth login
REM Catatan rilis (tampil di Pengaturan > Tentang): tulis di Rilis\catatan-<versi>.md, satu butir per baris diawali "- ".
REM Rilis dibuat sebagai draf, diisi berkas, baru diterbitkan, jadi tidak terlihat setengah jadi.
cd /d "%~dp0"
title Rilis DropBridge ke GitHub

set "VER="
for /f "tokens=2 delims==" %%v in ('findstr /b /c:"VERSION" "PC-App\app\version.py"') do set "VER=%%v"
set "VER=%VER: =%"
set "VER=%VER:"=%"
set "ANVER="
for /f "tokens=2 delims==" %%v in ('findstr /c:"versionName" "Android-App\app\build.gradle.kts"') do set "ANVER=%%v"
set "ANVER=%ANVER: =%"
set "ANVER=%ANVER:"=%"
if "%VER%"=="" goto gagal_versi

where gh >nul 2>&1
if errorlevel 1 (
  echo GitHub CLI belum terpasang. Pasang sekali:  winget install GitHub.cli
  echo lalu masuk:  gh auth login
  goto selesai_gagal
)

set "EXE=Rilis\DropBridge-Setup-%VER%.exe"
set "APK=Rilis\DropBridge-%ANVER%.apk"
if not exist "%EXE%" (
  echo Belum ada %EXE%. Jalankan build_semua.bat dulu.
  goto selesai_gagal
)

git fetch -q origin >nul 2>&1
set "AHEAD="
for /f %%n in ('git rev-list --count origin/main..HEAD') do set "AHEAD=%%n"
if not "%AHEAD%"=="0" (
  echo Masih ada %AHEAD% commit yang belum di-push. Jalankan  git push  dulu, supaya tag v%VER% menunjuk kode yang benar.
  goto selesai_gagal
)

set "SHA="
for /f %%h in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 -LiteralPath '%EXE%').Hash.ToLower()"') do set "SHA=%%h"
if "%SHA%"=="" (
  echo Gagal menghitung SHA-256.
  goto selesai_gagal
)
> "%EXE%.sha256" echo %SHA%  DropBridge-Setup-%VER%.exe
echo SHA-256: %SHA%

set "NEWREL=0"
gh release view "v%VER%" >nul 2>&1
if errorlevel 1 (
  set "NEWREL=1"
  if exist "Rilis\catatan-%VER%.md" (
    gh release create "v%VER%" --draft --target main --title "DropBridge %VER%" --notes-file "Rilis\catatan-%VER%.md"
  ) else (
    gh release create "v%VER%" --draft --target main --title "DropBridge %VER%" --notes "DropBridge %VER%"
  )
  if errorlevel 1 goto selesai_gagal
)

gh release upload "v%VER%" "%EXE%" "%EXE%.sha256" --clobber
if errorlevel 1 goto selesai_gagal
if exist "%APK%" gh release upload "v%VER%" "%APK%" --clobber

if "%NEWREL%"=="1" (
  gh release edit "v%VER%" --draft=false --latest
  if errorlevel 1 goto selesai_gagal
)

echo.
echo SELESAI. Rilis v%VER% (Windows) ada di GitHub. Di Linux Mint, jalankan PC-App/rilis_github.sh setelah build_deb.sh
for /f "delims=" %%u in ('gh release view "v%VER%" --json url -q .url') do echo %%u
if /i not "%~1"=="nopause" pause
exit /b 0

:gagal_versi
echo Versi tidak terbaca dari PC-App\app\version.py
:selesai_gagal
echo.
echo GAGAL. Tidak ada yang diterbitkan.
if /i not "%~1"=="nopause" pause
exit /b 1
