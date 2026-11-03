"""Thumbnail foto (opsional). Butuh paket Pillow; tanpa itu aplikasi tetap jalan, hanya tanpa thumbnail."""
import hashlib
import os
import secrets
import time
from pathlib import Path
from typing import Optional

from ..config import settings

try:
    from PIL import Image, ImageOps

    AVAILABLE = True
except ImportError:  # Pillow belum terpasang
    AVAILABLE = False

THUMBABLE_MIMES = {"image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp"}


def thumbable(mime: str) -> bool:
    return AVAILABLE and mime in THUMBABLE_MIMES


def make_thumb(src: Path, key: str) -> Optional[Path]:
    """Buat (atau ambil dari cache) thumbnail JPEG. None jika gagal."""
    if not AVAILABLE:
        return None
    try:
        st = src.stat()
        digest = hashlib.sha1(f"{key}|{st.st_mtime_ns}|{st.st_size}".encode("utf-8")).hexdigest()
        out = settings.thumbs_dir / f"{digest}.jpg"
        if out.exists():
            return out
        settings.thumbs_dir.mkdir(parents=True, exist_ok=True)
        size = settings.thumb_size
        tmp = out.with_suffix(f".{secrets.token_hex(4)}.tmp")
        with Image.open(src) as im:
            im.draft("RGB", (size * 2, size * 2))  # JPEG: dekode lebih kecil, jauh lebih cepat
            im = ImageOps.exif_transpose(im)  # hormati orientasi foto dari HP
            if im.mode in ("RGBA", "LA", "P"):
                im = im.convert("RGBA")
                bg = Image.new("RGB", im.size, (255, 255, 255))
                bg.paste(im, mask=im.getchannel("A"))
                im = bg
            else:
                im = im.convert("RGB")
            im.thumbnail((size, size), Image.Resampling.LANCZOS)
            im.save(tmp, "JPEG", quality=78, optimize=True)
        os.replace(tmp, out)
        return out
    except Exception:  # file rusak, terlalu besar (decompression bomb), dll
        return None


def cleanup(days: int = 60) -> None:
    """Hapus thumbnail lama agar cache tidak menumpuk."""
    try:
        cutoff = time.time() - days * 86400
        for p in settings.thumbs_dir.glob("*.jpg"):
            if p.stat().st_mtime < cutoff:
                p.unlink(missing_ok=True)
        for p in settings.thumbs_dir.glob("*.tmp"):
            if p.stat().st_mtime < time.time() - 3600:
                p.unlink(missing_ok=True)
    except OSError:
        pass
