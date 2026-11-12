@echo off
setlocal
cd /d "%~dp0"
title DropBridge

REM Cari Python yang benar-benar jalan. "python" bawaan Windows (toko Microsoft) sering cuma pintasan kosong.
set "PY="
py -3 -c "import venv, ensurepip" >nul 2>nul
if not errorlevel 1 set "PY=py -3"
if not defined PY (
  python -c "import venv, ensurepip" >nul 2>nul
  if not errorlevel 1 set "PY=python"
)
if not defined PY (
  echo.
  echo Python belum terpasang, atau yang ada tidak bisa dipakai.
  echo.
  echo Caranya:
  echo   1. Unduh Python 3.10 atau lebih baru dari https://www.python.org/downloads/
  echo   2. Saat memasang, CENTANG "Add python.exe to PATH".
  echo   3. Tutup jendela ini, lalu klik dua kali start.bat lagi.
  echo.
  echo Catatan: kalau muncul "Python was not found" atau toko Microsoft terbuka, itu bukan Python asli.
  echo Matikan di: Pengaturan ^> Aplikasi ^> Pengaturan aplikasi lanjutan ^> Alias eksekusi aplikasi ^(python.exe^).
  pause
  exit /b 1
)

REM Lingkungan yang setengah jadi atau dibawa dari PC lain dibuang dulu.
if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" -c "import sys" >nul 2>nul
  if errorlevel 1 rmdir /s /q ".venv"
)

if not exist ".venv\Scripts\python.exe" (
  echo Menyiapkan lingkungan, hanya sekali saja...
  %PY% -m venv .venv
  if errorlevel 1 (
    echo.
    echo Gagal membuat lingkungan Python. Pesan di atas menjelaskan sebabnya.
    echo Yang sering jadi penyebab:
    echo   - Folder DropBridge masih di dalam file ZIP. Ekstrak dulu ke folder biasa, mis. C:\DropBridge
    echo   - Folder ada di tempat yang tidak boleh ditulis ^(Program Files, folder sistem^).
    echo   - Antivirus memblokir. Coba matikan sebentar, lalu ulangi.
    if exist ".venv" rmdir /s /q ".venv"
    pause
    exit /b 1
  )
)

".venv\Scripts\python.exe" -m pip install -q -r requirements.txt
if errorlevel 1 (
  echo.
  echo PERINGATAN: paket gagal dipasang ^(mungkin tidak ada internet^).
  echo Aplikasi tetap dijalankan, tetapi bisa error jika paket belum pernah terpasang,
  echo dan gambar mini ^(thumbnail^) tidak tampil tanpa paket Pillow.
  echo.
)

REM "start.bat konsol" = jalan di jendela hitam seperti dulu (untuk melihat catatan langsung).
if /i "%~1"=="konsol" (
  ".venv\Scripts\python.exe" run.py
  echo.
  echo DropBridge berhenti.
  pause
  exit /b 0
)

REM Biasanya: jendela DropBridge + ikon di dekat jam (tray). Jendela hitam ini menutup sendiri.
start "" ".venv\Scripts\pythonw.exe" desktop.py
echo DropBridge dibuka. Saat jendelanya ditutup, DropBridge tetap jalan di ikon dekat jam.
timeout /t 4 >nul
