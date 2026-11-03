@echo off
setlocal
cd /d "%~dp0"
title DropBridge

set "PY="
where py >nul 2>nul
if not errorlevel 1 set "PY=py -3"
if not defined PY (
  where python >nul 2>nul
  if not errorlevel 1 set "PY=python"
)
if not defined PY (
  echo Python belum terpasang.
  echo Unduh dari https://www.python.org/downloads/ lalu centang "Add python.exe to PATH".
  pause
  exit /b 1
)

if not exist ".venv\Scripts\python.exe" (
  echo Menyiapkan lingkungan, hanya sekali saja...
  %PY% -m venv .venv
  if errorlevel 1 (
    echo Gagal membuat lingkungan Python.
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

".venv\Scripts\python.exe" run.py
echo.
echo DropBridge berhenti.
pause
