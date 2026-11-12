r"""Pengaturan DropBridge, dibaca dari config.json (dibuat otomatis jika belum ada).

Pengaturan dan data selalu di %LOCALAPPDATA%\DropBridge, baik dijalankan dari kode (start.bat)
maupun versi terpasang (DropBridge-Setup.exe). File kiriman bawaannya di Documents\DropBridge.
Folder kode (mis. C:\Dev\DropBridge) hanya berisi kode, tidak ada data pribadi.

Struktur kode:  <root>\Web (tampilan), <root>\PC-App (folder ini), <root>\Android-App, <root>\Rilis
"""
import json
import os
import secrets
import shutil
import sys
from pathlib import Path

FROZEN = bool(getattr(sys, "frozen", False))
BASE_DIR = Path(__file__).resolve().parent.parent  # PC-App
ROOT_DIR = BASE_DIR.parent  # folder induk: Web, PC-App, Android-App, Rilis
# Berkas bawaan (web, apk): di dalam paket .exe, atau folder proyek
RES_DIR = Path(getattr(sys, "_MEIPASS", BASE_DIR))
# Berkas yang bisa ditulis (config.json, data/)
USER_DIR = Path(os.environ.get("LOCALAPPDATA") or Path.home() / ".local" / "share") / "DropBridge"
CONFIG_PATH = USER_DIR / "config.json"
DEFAULT_SHARED = Path.home() / "Documents" / "DropBridge"
# Tempat pengaturan versi lama (sebelum 2.4.1 tersimpan di folder kode)
LEGACY_DIRS = [Path(r"C:\DropBridge"), ROOT_DIR, BASE_DIR]


def _import_legacy() -> None:
    """Pertama kali jalan: pindahkan PIN, folder, dan perangkat dari folder kode versi lama."""
    if CONFIG_PATH.exists():
        return
    for old in dict.fromkeys(LEGACY_DIRS):
        cfg = old / "config.json"
        if not cfg.is_file():
            continue
        try:
            raw = json.loads(cfg.read_text(encoding="utf-8-sig"))
        except (OSError, json.JSONDecodeError):
            continue
        val = str(raw.get("shared_dir") or "").strip()
        if not (val and Path(val).is_absolute()):
            # Folder relatif lama (mis. "shared"): pakai bila masih ada isinya, selain itu Documents\DropBridge
            raw["shared_dir"] = str(old / val) if val and (old / val).is_dir() else str(DEFAULT_SHARED)
        raw["imported_from"] = str(old)
        try:
            USER_DIR.mkdir(parents=True, exist_ok=True)
            if (old / "data").is_dir():
                shutil.copytree(old / "data", USER_DIR / "data", dirs_exist_ok=True,
                                ignore=shutil.ignore_patterns("*.part", "incoming", "*.log"))
            CONFIG_PATH.write_text(json.dumps(raw, indent=2, ensure_ascii=False), encoding="utf-8")
        except OSError:
            return
        # Sudah dipindah: bersihkan dari folder kode
        try:
            cfg.unlink()
            shutil.rmtree(old / "data", ignore_errors=True)
        except OSError:
            pass
        return


_import_legacy()

DEFAULTS = {
    "host": "0.0.0.0",
    "port": 8000,
    "pin": "",  # kosong = dibuat acak 6 angka saat pertama jalan, lalu disimpan
    "shared_dir": "",  # kosong = Documents\DropBridge; atau path absolut (mis. folder di OneDrive kantor)
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

        if not raw.get("shared_dir"):
            # Belum ada pengaturan: simpan di Documents\DropBridge (dicatat di config.json)
            cfg["shared_dir"] = str(DEFAULT_SHARED)
        if not str(cfg["pin"]).strip() or not raw.get("shared_dir"):
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
        # Tampilan: ikut di dalam .exe, atau folder Web di sebelah PC-App saat mengembangkan
        self.web_dir: Path = RES_DIR / "web" if FROZEN else ROOT_DIR / "Web"
        # APK Android untuk diunduh HP: ikut di installer, atau hasil build_apk.bat (folder Rilis)
        self.apk_dirs = [RES_DIR / "apk", ROOT_DIR / "Rilis"]


def save_setting(key: str, value) -> None:
    """Ubah satu pengaturan di config.json (dipakai menu tray). Berlaku setelah DropBridge dijalankan ulang."""
    raw: dict = {}
    if CONFIG_PATH.exists():
        raw = json.loads(CONFIG_PATH.read_text(encoding="utf-8-sig"))
    raw[key] = value
    USER_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG_PATH.write_text(json.dumps(raw, indent=2, ensure_ascii=False), encoding="utf-8")


settings = Settings()
