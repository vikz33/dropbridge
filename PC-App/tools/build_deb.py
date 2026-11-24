"""Membangun paket .deb DropBridge (amd64) dengan Python murni: jalan di Windows, Linux, maupun Mac.
Pemakaian:  python tools/build_deb.py        Hasil: Rilis/dropbridge_<versi>_amd64.deb
Python (venv + pustaka) TIDAK dibungkus: dibuat di laptop Linux saat pemasangan (postinst), jadi paket kecil
dan cocok untuk versi Python berapa pun. Butuh internet saat memasang. APK terbaru di Rilis ikut dibungkus."""
import gzip
import hashlib
import io
import re
import sys
import tarfile
import time
from pathlib import Path

PC = Path(__file__).resolve().parent.parent
ROOT = PC.parent
sys.path.insert(0, str(PC))

VER = re.search(r'VERSION\s*=\s*"([^"]+)"', (PC / "app" / "version.py").read_text(encoding="utf-8")).group(1)
NOW = int(time.time())

DESKTOP = """[Desktop Entry]
Type=Application
Name=DropBridge
Comment=Terima file dari HP lewat Wi-Fi
Exec=dropbridge
Icon=dropbridge
Terminal=false
Categories=Network;FileTransfer;Utility;
StartupNotify=true
"""
LAUNCHER = """#!/bin/sh
exec /opt/dropbridge/venv/bin/python /opt/dropbridge/PC-App/desktop.py "$@"
"""
POSTINST = """#!/bin/sh
set -e
APP=/opt/dropbridge
# Python dan pustaka disiapkan di sini (butuh internet); dilewati bila daftar pustaka tidak berubah.
want=$(sha256sum "$APP/PC-App/requirements.txt" | cut -d' ' -f1)
if [ ! -x "$APP/venv/bin/python" ] || [ "$(cat "$APP/venv/.req" 2>/dev/null)" != "$want" ]; then
  echo "DropBridge: menyiapkan Python (butuh internet, bisa beberapa menit)..."
  rm -rf "$APP/venv"
  if ! python3 -m venv --system-site-packages "$APP/venv"; then echo "DropBridge: gagal membuat venv. Pasang: sudo apt install python3-venv" >&2; exit 1; fi
  if ! "$APP/venv/bin/python" -m pip install -q --no-compile -r "$APP/PC-App/requirements.txt"; then
    echo "DropBridge: gagal mengunduh pustaka Python. Cek internet lalu jalankan: sudo dpkg --configure -a" >&2
    rm -rf "$APP/venv"
    exit 1
  fi
  echo "$want" > "$APP/venv/.req"
  rm -rf "$APP/venv/bin/pip"* "$APP/venv/bin/activate"*
fi
"$APP/venv/bin/python" -m compileall -q "$APP/PC-App" >/dev/null 2>&1 || true
# Firewall: hanya bila ufw aktif (di Mint bawaannya mati)
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then
  ufw allow 8000/tcp comment DropBridge >/dev/null 2>&1 || true
  ufw allow 47800/udp comment "DropBridge cari PC" >/dev/null 2>&1 || true
fi
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database -q /usr/share/applications || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache -q /usr/share/icons/hicolor || true
exit 0
"""
PRERM = """#!/bin/sh
pkill -f /opt/dropbridge/PC-App/desktop.py 2>/dev/null || true
exit 0
"""
POSTRM = """#!/bin/sh
case "$1" in
  remove|purge) rm -rf /opt/dropbridge/venv /opt/dropbridge/PC-App/app/__pycache__ ;;
esac
exit 0
"""


def collect() -> list:
    """(jalur di paket, isi bytes, mode)"""
    files = []

    def add_tree(src: Path, dst: str, skip=("__pycache__",)):
        for p in sorted(src.rglob("*")):
            if p.is_dir() or any(part in skip for part in p.parts) or p.suffix == ".pyc":
                continue
            files.append((f"{dst}/{p.relative_to(src).as_posix()}", p.read_bytes(), 0o644))

    add_tree(PC / "app", "opt/dropbridge/PC-App/app")
    for n in ("desktop.py", "run.py", "requirements.txt"):
        files.append((f"opt/dropbridge/PC-App/{n}", (PC / n).read_bytes(), 0o644))
    add_tree(ROOT / "Web", "opt/dropbridge/Web")
    apks = sorted((ROOT / "Rilis").glob("DropBridge-*.apk"), key=lambda p: p.stat().st_mtime)
    if apks:
        files.append((f"opt/dropbridge/Rilis/{apks[-1].name}", apks[-1].read_bytes(), 0o644))
        print(f"APK ikut dibungkus: {apks[-1].name}")
    else:
        print("Tidak ada APK di Rilis; paket dibuat tanpa APK.")
    from app.icon import icon_image  # butuh Pillow (sudah ada di lingkungan PC-App)
    buf = io.BytesIO()
    icon_image(256).save(buf, "PNG")
    files.append(("usr/share/icons/hicolor/256x256/apps/dropbridge.png", buf.getvalue(), 0o644))
    files.append(("usr/bin/dropbridge", LAUNCHER.encode(), 0o755))
    files.append(("usr/share/applications/dropbridge.desktop", DESKTOP.encode(), 0o644))
    return files


def make_tar(entries: list, strip_dot=False) -> bytes:
    """Tar .gz: semua milik root, ada entri folder, urutan tetap."""
    raw = io.BytesIO()
    with tarfile.open(fileobj=raw, mode="w", format=tarfile.GNU_FORMAT) as tf:
        def meta(ti, mode):
            ti.uid = ti.gid = 0
            ti.uname = ti.gname = "root"
            ti.mtime = NOW
            ti.mode = mode
            return ti
        dirs = set()
        for path, _, _ in entries:
            parts = path.split("/")[:-1]
            for i in range(1, len(parts) + 1):
                dirs.add("/".join(parts[:i]))
        tf.addfile(meta(_ti("./", tarfile.DIRTYPE), 0o755))
        for d in sorted(dirs):
            tf.addfile(meta(_ti(f"./{d}/", tarfile.DIRTYPE), 0o755))
        for path, data, mode in entries:
            ti = _ti(f"./{path}", tarfile.REGTYPE)
            ti.size = len(data)
            tf.addfile(meta(ti, mode), io.BytesIO(data))
    out = io.BytesIO()
    with gzip.GzipFile(fileobj=out, mode="wb", mtime=NOW, compresslevel=9) as gz:
        gz.write(raw.getvalue())
    return out.getvalue()


def _ti(name: str, typ) -> tarfile.TarInfo:
    ti = tarfile.TarInfo(name)
    ti.type = typ
    return ti


def ar(members: list) -> bytes:
    out = bytearray(b"!<arch>\n")
    for name, data in members:
        out += f"{name:<16}{NOW:<12}{0:<6}{0:<6}{'100644':<8}{len(data):<10}`\n".encode()
        out += data
        if len(data) % 2:
            out += b"\n"
    return bytes(out)


def main() -> None:
    print(f"Membangun DropBridge {VER} untuk Linux (paket .deb)...")
    data = collect()
    size_kb = (sum(len(d) for _, d, _ in data) + 1023) // 1024
    control = f"""Package: dropbridge
Version: {VER}
Section: net
Priority: optional
Architecture: amd64
Installed-Size: {size_kb}
Depends: python3 (>= 3.10), python3-venv, python3-gi, gir1.2-webkit2-4.1, gir1.2-ayatanaappindicator3-0.1, libnotify-bin, zenity
Maintainer: DropBridge <noreply@localhost>
Description: Kirim file antara HP dan laptop lewat Wi-Fi
 Laptop ini langsung jadi server DropBridge: HP mengirim lewat aplikasi Android
 atau browser, file masuk ke Documents/DropBridge. Jendela, ikon di panel,
 notifikasi file masuk, dan izin untuk HP baru.
"""
    md5 = "".join(f"{hashlib.md5(d).hexdigest()}  {p}\n" for p, d, _ in data)
    ctrl_entries = [
        ("control", control.encode(), 0o644),
        ("md5sums", md5.encode(), 0o644),
        ("postinst", POSTINST.encode(), 0o755),
        ("prerm", PRERM.encode(), 0o755),
        ("postrm", POSTRM.encode(), 0o755),
    ]
    deb = ar([
        ("debian-binary", b"2.0\n"),
        ("control.tar.gz", make_tar(ctrl_entries)),
        ("data.tar.gz", make_tar(data)),
    ])
    out_dir = ROOT / "Rilis"
    out_dir.mkdir(exist_ok=True)
    out = out_dir / f"dropbridge_{VER}_amd64.deb"
    out.write_bytes(deb)
    print(f"\nBERHASIL. Paket versi {VER}: {out} ({len(deb) // 1024} KB)")
    print("Pasang: sudo apt install ./" + out.name)


if __name__ == "__main__":
    main()
