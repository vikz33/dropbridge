#!/usr/bin/env bash
# Membangun paket .deb DropBridge untuk Linux Mint / Ubuntu / Debian (amd64).
# Jalankan di laptop Linux itu sendiri:  bash build_deb.sh
# Hasil: ../Rilis/dropbridge_<versi>_amd64.deb  (pasang: klik ganda, atau sudo apt install ./dropbridge_<versi>_amd64.deb)
# APK Android terbaru di ../Rilis ikut dibungkus, jadi HP bisa mengunduhnya dari laptop ini.
set -e
cd "$(dirname "$(readlink -f "$0")")"
ROOT="$(cd .. && pwd)"
VER=$(sed -n 's/^VERSION = "\(.*\)"/\1/p' app/version.py)
[ -n "$VER" ] || VER=0.0.0
STAGE="$(mktemp -d)"
PKG="$STAGE/pkg"
APP="$PKG/opt/dropbridge"
LOG="$PWD/build_deb_log.txt"
echo "Membangun DropBridge $VER untuk Linux..."
: > "$LOG"

for p in python3-venv dpkg-dev; do dpkg -s "$p" >/dev/null 2>&1 || { echo "Pasang dulu: sudo apt install python3-venv dpkg-dev"; exit 1; }; done

echo "[1/4] Menyalin program..."
mkdir -p "$APP/PC-App" "$APP/Web" "$APP/Rilis"
cp -r app desktop.py run.py requirements.txt "$APP/PC-App/"
cp -r "$ROOT/Web/." "$APP/Web/"
APK=$(ls -t "$ROOT"/Rilis/DropBridge-*.apk 2>/dev/null | head -1 || true)
if [ -n "$APK" ]; then cp "$APK" "$APP/Rilis/"; echo "      APK ikut dibungkus: $(basename "$APK")"; else echo "      Tidak ada APK di Rilis; paket dibuat tanpa APK."; fi
find "$APP" -name __pycache__ -prune -exec rm -rf {} +

echo "[2/4] Menyiapkan Python (bisa beberapa menit)..."
python3 -m venv --system-site-packages "$APP/venv" >>"$LOG" 2>&1
"$APP/venv/bin/python" -m pip install -q --no-compile -r requirements.txt >>"$LOG" 2>&1
rm -rf "$APP/venv/bin/pip"* "$APP/venv/bin/activate"*
"$APP/venv/bin/python" -m compileall -q "$APP/PC-App" >>"$LOG" 2>&1 || true

echo "[3/4] Ikon, menu aplikasi, perintah dropbridge..."
mkdir -p "$PKG/usr/bin" "$PKG/usr/share/applications" "$PKG/usr/share/icons/hicolor/256x256/apps" "$PKG/DEBIAN"
"$APP/venv/bin/python" -c "import sys; sys.path.insert(0, 'app/..'); from app.icon import icon_image; icon_image(256).save('$PKG/usr/share/icons/hicolor/256x256/apps/dropbridge.png')" >>"$LOG" 2>&1
cat > "$PKG/usr/bin/dropbridge" <<'SH'
#!/bin/sh
exec /opt/dropbridge/venv/bin/python /opt/dropbridge/PC-App/desktop.py "$@"
SH
chmod 755 "$PKG/usr/bin/dropbridge"
cat > "$PKG/usr/share/applications/dropbridge.desktop" <<DESK
[Desktop Entry]
Type=Application
Name=DropBridge
Comment=Terima file dari HP lewat Wi-Fi
Exec=dropbridge
Icon=dropbridge
Terminal=false
Categories=Network;FileTransfer;Utility;
StartupNotify=true
DESK

SIZE=$(du -sk "$PKG" | cut -f1)
cat > "$PKG/DEBIAN/control" <<CTRL
Package: dropbridge
Version: $VER
Section: net
Priority: optional
Architecture: amd64
Installed-Size: $SIZE
Depends: python3 (>= 3.10), python3-gi, gir1.2-webkit2-4.1, gir1.2-ayatanaappindicator3-0.1, libnotify-bin, zenity
Maintainer: DropBridge <noreply@localhost>
Description: Kirim file antara HP dan laptop lewat Wi-Fi
 Laptop ini langsung jadi server DropBridge: HP mengirim lewat aplikasi Android
 atau browser, file masuk ke Documents/DropBridge. Jendela, ikon di panel,
 notifikasi file masuk, dan izin untuk HP baru.
CTRL
cat > "$PKG/DEBIAN/postinst" <<'POST'
#!/bin/sh
set -e
# Firewall: hanya bila ufw aktif (di Mint bawaannya mati)
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then
  ufw allow 8000/tcp comment DropBridge >/dev/null 2>&1 || true
  ufw allow 47800/udp comment "DropBridge cari PC" >/dev/null 2>&1 || true
fi
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database -q /usr/share/applications || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache -q /usr/share/icons/hicolor || true
exit 0
POST
cat > "$PKG/DEBIAN/prerm" <<'PRE'
#!/bin/sh
pkill -f /opt/dropbridge/PC-App/desktop.py 2>/dev/null || true
exit 0
PRE
chmod 755 "$PKG/DEBIAN/postinst" "$PKG/DEBIAN/prerm"

echo "[4/4] Membuat paket..."
mkdir -p "$ROOT/Rilis"
OUT="$ROOT/Rilis/dropbridge_${VER}_amd64.deb"
dpkg-deb --build --root-owner-group "$PKG" "$OUT" >>"$LOG" 2>&1
rm -rf "$STAGE"
echo
echo "BERHASIL. Paket versi $VER: $OUT"
echo "Pasang: klik ganda file itu, atau  sudo apt install \"$OUT\""
