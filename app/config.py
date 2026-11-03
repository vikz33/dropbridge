"""Pengaturan DropBridge, dibaca dari config.json (dibuat otomatis jika belum ada)."""
import json
import secrets
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
CONFIG_PATH = BASE_DIR / "config.json"

DEFAULTS = {
    "host": "0.0.0.0",
    "port": 8000,
    "pin": "",  # kosong = dibuat acak 6 angka saat pertama jalan, lalu disimpan
    "shared_dir": "shared",  # relatif terhadap folder proyek, atau path absolut
    "max_upload_mb": 2048,
    "session_hours": 24,
    "lan_only": True,  # tolak koneksi dari luar jaringan lokal
    "enable_docs": False,  # true = buka dokumentasi API di /docs
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

        if not str(cfg["pin"]).strip():
            cfg["pin"] = f"{secrets.randbelow(10**6):06d}"
            CONFIG_PATH.write_text(json.dumps(cfg, indent=2), encoding="utf-8")

        self.host: str = str(cfg["host"])
        self.port: int = int(cfg["port"])
        self.pin: str = str(cfg["pin"]).strip()
        shared = Path(str(cfg["shared_dir"]))
        self.shared_dir: Path = shared if shared.is_absolute() else BASE_DIR / shared
        self.max_upload_mb: int = int(cfg["max_upload_mb"])
        self.max_upload_bytes: int = self.max_upload_mb * 1024 * 1024
        self.session_hours: float = float(cfg["session_hours"])
        self.lan_only: bool = bool(cfg["lan_only"])
        self.enable_docs: bool = bool(cfg["enable_docs"])
        self.data_dir: Path = BASE_DIR / "data"
        self.thumbs_dir: Path = self.data_dir / "thumbs"
        self.thumb_size: int = 480
        self.web_dir: Path = BASE_DIR / "web"


settings = Settings()
