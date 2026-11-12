r"""Pengaturan DropBridge, dibaca dari config.json (dibuat otomatis jika belum ada).

Dua cara jalan:
- dari kode (start.bat): semua di folder proyek, seperti biasa;
- terpasang dari DropBridge-Setup.exe: program di Program Files (hanya-baca), pengaturan dan data di
  %LOCALAPPDATA%\DropBridge. Saat pertama jalan, pengaturan lama dari C:\DropBridge diimpor otomatis.
"""
import json
import os
import secrets
import shutil
import sys
from pathlib import Path

FROZEN = bool(getattr(sys, "frozen", False))
BASE_DIR = Path(__file__).resolve().parent.parent
# Berkas bawaan (web, apk): di dalam paket .exe, atau folder proyek
RES_DIR = Path(getattr(sys, "_MEIPASS", BASE_DIR))
# Berkas yang bisa ditulis (config.json, data/)
USER_DIR = Path(os.environ.get("LOCALAPPDATA") or Path.home()) / "DropBridge" if FROZEN else BASE_DIR
CONFIG_PATH = USER_DIR / "config.json"
LEGACY_DIR = Path(r"C:\DropBridge")


def _import_legacy() -> None:
    r"""Versi terpasang, pertama kali jalan: ambil PIN, folder, dan perangkat dari C:\DropBridge."""
    if not FROZEN or CONFIG_PATH.exists() or not (LEGACY_DIR / "config.json").exists():
        return
    try:
        raw = json.loads((LEGACY_DIR / "config.json").read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError):
        return
    shared = Path(str(raw.get("shared_dir", "shared")))
    raw["shared_dir"] = str(shared if shared.is_absolute() else LEGACY_DIR / shared)
    raw["imported_from"] = str(LEGACY_DIR)
    USER_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG_PATH.write_text(json.dumps(raw, indent=2, ensure_ascii=False), encoding="utf-8")
    for name in ("sessions.json", "clips.json"):
        src = LEGACY_DIR / "data" / name
        if src.exists():
            (USER_DIR / "data").mkdir(parents=True, exist_ok=True)
            try:
                shutil.copy2(src, USER_DIR / "data" / name)
            except OSError:
                pass


_import_legacy()

DEFAULTS = {
    "host": "0.0.0.0",
    "port": 8000,
    "pin": "",  # kosong = dibuat acak 6 angka saat pertama jalan, lalu disimpan
    "shared_dir": "shared",  # relatif terhadap folder proyek, atau path absolut (mis. folder di OneDrive kantor)
    "max_upload_mb": 2048,
    "session_hours": 24,
    "photo_max_kb": 512,  # foto lebih besar ini dikecilkan resolusinya di HP/web sebelum dikirim (0 = nonaktif)
    "idle_hours": 8,  # perangkat lain otomatis keluar bila tidak aktif sekian jam (0 = nonaktif)
    "lan_only": True,  # tolak koneksi dari luar jaringan lokal
    "enable_docs": False,  # true = buka dokumentasi API di /docs
    "notify": True,  # notifikasi Windows saat file masuk (versi desktop)
    "welcomed": "",  # versi terakhir yang sudah melihat halaman Selamat datang
}


class Settings:
    def __init__(self) -> None:
        raw: dict = {}
        if CONFIG_PATH.exists():
            try:
                raw = json.loads(CONFIG_PATH.read_text(encoding="utf-8-sig"))
            except json.JSONDecodeError as e:
                raise SystemExit(f"config.json tidak valid: {e}")
        cfg = {**DEFAULTS, **raw}

        if FROZEN and not raw.get("shared_dir"):
            # Versi terpasang tanpa pengaturan lama: simpan di Documents\DropBridge (dicatat di config.json)
            cfg["shared_dir"] = str(Path.home() / "Documents" / "DropBridge")
        if not str(cfg["pin"]).strip() or (FROZEN and not raw.get("shared_dir")):
            if not str(cfg["pin"]).strip():
                cfg["pin"] = f"{secrets.randbelow(10**6):06d}"
            USER_DIR.mkdir(parents=True, exist_ok=True)
            CONFIG_PATH.write_text(json.dumps(cfg, indent=2, ensure_ascii=False), encoding="utf-8")

        self.host: str = str(cfg["host"])
        self.port: int = int(cfg["port"])
        self.pin: str = str(cfg["pin"]).strip()
        shared = Path(str(cfg["shared_dir"]))
        self.shared_dir: Path = shared if shared.is_absolute() else USER_DIR / shared
        self.max_upload_mb: int = int(cfg["max_upload_mb"])
        self.max_upload_bytes: int = self.max_upload_mb * 1024 * 1024
        self.session_hours: float = float(cfg["session_hours"])
        self.photo_max_kb: int = int(cfg["photo_max_kb"])
        self.idle_hours: float = float(cfg["idle_hours"])
        self.lan_only: bool = bool(cfg["lan_only"])
        self.enable_docs: bool = bool(cfg["enable_docs"])
        self.notify: bool = bool(cfg["notify"])
        self.welcomed: str = str(cfg["welcomed"])
        self.imported_from: str = str(raw.get("imported_from", ""))
        self.data_dir: Path = USER_DIR / "data"
        self.thumbs_dir: Path = self.data_dir / "thumbs"
        # File kiriman yang belum lengkap disimpan di sini, bukan di folder shared,
        # supaya OneDrive (atau aplikasi sinkron lain) hanya melihat file yang sudah utuh.
        self.incoming_dir: Path = self.data_dir / "incoming"
        self.thumb_size: int = 480
        self.web_dir: Path = RES_DIR / "web"
        # APK Android untuk diunduh HP: ikut di installer, atau hasil build_apk.bat saat mengembangkan
        self.apk_dirs = [RES_DIR / "apk", BASE_DIR.parent / "dropbridge-android" / "dist"]


def save_setting(key: str, value) -> None:
    """Ubah satu pengaturan di config.json (dipakai menu tray). Berlaku setelah DropBridge dijalankan ulang."""
    raw: dict = {}
    if CONFIG_PATH.exists():
        raw = json.loads(CONFIG_PATH.read_text(encoding="utf-8-sig"))
    raw[key] = value
    USER_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG_PATH.write_text(json.dumps(raw, indent=2, ensure_ascii=False), encoding="utf-8")


settings = Settings()
