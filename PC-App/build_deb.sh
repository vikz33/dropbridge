#!/usr/bin/env bash
# Membangun paket .deb DropBridge (amd64). Sekarang memakai skrip Python yang sama dengan Windows
# (tools/build_deb.py), jadi hasilnya selalu sama di mana pun dibangun.
# Hasil: ../Rilis/dropbridge_<versi>_amd64.deb   Pasang: sudo apt install ./Rilis/dropbridge_<versi>_amd64.deb
set -e
cd "$(dirname "$(readlink -f "$0")")"
python3 -c "import PIL" 2>/dev/null || { echo "Pasang dulu: sudo apt install python3-pil"; exit 1; }
exec python3 tools/build_deb.py
