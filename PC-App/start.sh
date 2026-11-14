#!/usr/bin/env bash
# DropBridge untuk Linux (Mint, Ubuntu, Debian), dijalankan dari kode.
#   bash start.sh          jendela DropBridge + ikon di panel (seperti versi terpasang)
#   bash start.sh konsol   server saja di terminal (melihat alamat, PIN, dan catatan langsung)
# Versi terpasang: bash build_deb.sh, lalu pasang Rilis/dropbridge_<versi>_amd64.deb
set -e
cd "$(dirname "$(readlink -f "$0")")"

need=()
command -v python3 >/dev/null || need+=(python3)
python3 -c "import venv, ensurepip" 2>/dev/null || need+=(python3-venv)
python3 -c "import gi; gi.require_version('WebKit2', '4.1')" 2>/dev/null || need+=(python3-gi gir1.2-webkit2-4.1)
python3 -c "import gi; gi.require_version('AyatanaAppIndicator3', '0.1')" 2>/dev/null || need+=(gir1.2-ayatanaappindicator3-0.1)
command -v notify-send >/dev/null || need+=(libnotify-bin)
command -v zenity >/dev/null || need+=(zenity)
if [ ${#need[@]} -gt 0 ]; then
  echo "Paket yang belum terpasang: ${need[*]}"
  echo "Pasang sekali dengan:"
  echo "  sudo apt install ${need[*]}"
  echo
  if [ "$1" != "konsol" ]; then
    read -r -p "Pasang sekarang? (y/n) " yn
    if [ "$yn" = "y" ]; then sudo apt install -y "${need[@]}"; fi
  fi
fi

# .venv dari lokasi lain (folder dipindah) atau rusak: buat ulang
if [ -f .venv/lokasi.txt ] && [ "$(cat .venv/lokasi.txt)" != "$PWD" ]; then rm -rf .venv; fi
if [ -x .venv/bin/python ] && ! .venv/bin/python -c "import sys" 2>/dev/null; then rm -rf .venv; fi
if [ ! -x .venv/bin/python ]; then
  echo "Menyiapkan lingkungan, hanya sekali saja..."
  # --system-site-packages: jendela (WebKitGTK) dan ikon panel memakai paket python3-gi dari sistem
  python3 -m venv --system-site-packages .venv
  echo "$PWD" > .venv/lokasi.txt
fi
.venv/bin/python -m pip install -q -r requirements.txt || echo "PERINGATAN: paket gagal dipasang (tidak ada internet?)."

if [ "$1" = "konsol" ]; then
  exec .venv/bin/python run.py
fi
nohup .venv/bin/python desktop.py >/dev/null 2>&1 &
echo "DropBridge dibuka. Saat jendelanya ditutup, DropBridge tetap jalan di ikon panel bawah."
